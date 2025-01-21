package com.example.iot1

import DBHelper
import RegistrationResult
import RetrofitClient
import ThingDetails
import UserIpResponse
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.iot1.databinding.ActivityThingDetailsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

class ThingDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityThingDetailsBinding
    private lateinit var dbHelper: DBHelper
    private var generatedOtp: String? = null
    private val port = 12345

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThingDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DBHelper(this) // Initialize the DBHelper
        // Set up window insets for proper layout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // Set up the spinner with vendor options
        val vendors = arrayOf("Select Your Vendor", "Vendor 'xyz'", "Vendor 'abc'")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, vendors)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinner.adapter = adapter
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))  // Initialize Python with the context
        }
        // Set up the OTP generation button
        binding.btnThingSubmit.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    generatedOtp = withContext(Dispatchers.IO) { sendSms() }
                    if (generatedOtp != null) {
                        Toast.makeText(this@ThingDetailsActivity, "OTP sent", Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        Toast.makeText(
                            this@ThingDetailsActivity,
                            "Failed to send OTP",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this@ThingDetailsActivity,
                        "Error: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Set up the OTP verification button
        binding.btnThingOtpVerify.setOnClickListener {
            val enteredOtp = binding.etThingOtp.text.toString().trim()
            if (enteredOtp == generatedOtp) {
                val thingName = binding.etThingname.text.toString()
                val UID = binding.etThingId.text.toString()
                val Tkey = binding.etThingKey.text.toString()
                if (isNetworkAvailable()) {
                    registerThing(thingName, UID, Tkey)
                } else {
                    Toast.makeText(
                        this,
                        "No internet connection. Registration failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Check for network connectivity
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.activeNetworkInfo?.isConnected == true
    }

    // Register the thing with the backend server
    private fun registerThing(thingName: String, thingId: String, thingKey: String) {
        val thingDetails = ThingDetails(thingName, thingId, thingKey)

        // Step 1: Directly register the thing (no separate key verification needed)
        RetrofitClient.instance.registerThing(thingDetails).enqueue(object : Callback<RegistrationResult> {
            override fun onResponse(call: Call<RegistrationResult>, response: Response<RegistrationResult>) {
                if (response.isSuccessful) {
                    val registrationResult = response.body()
                    if (registrationResult?.registered == true) {
                        Log.d("ThingDetails", "Thing registered successfully (online)")

                        // Save to local database after successful registration
                        saveThingDetailsLocally(thingName, thingId, thingKey)
                        // Optionally, retrieve and store any files from the server
                        receiveFileFromServer(thingId)
                        // Notify user and navigate back to AvailableThingsActivity
                        Toast.makeText(this@ThingDetailsActivity, "Thing registered successfully", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@ThingDetailsActivity, AvailableThingsActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        Log.d("ThingDetails", "Registration failed (online)")
                        Toast.makeText(this@ThingDetailsActivity, "Registration failed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("ThingDetails", "Response is not successful (online): ${response.errorBody()?.string()}")
                    Toast.makeText(this@ThingDetailsActivity, "Registration failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<RegistrationResult>, t: Throwable) {
                Log.e("ThingDetails", "Error (online): ${t.message}")
                Toast.makeText(this@ThingDetailsActivity, "Registration failed", Toast.LENGTH_SHORT).show()
            }
        })
    }


    // Save thing details to the local database
    private fun saveThingDetailsLocally(thingName: String, thingId: String, thingKey: String) {
        Log.d("Lokesh", "entered save things locally")

        // Start a coroutine to handle the IP fetching and saving operation
        CoroutineScope(Dispatchers.Main).launch {
            // Fetch the IP address in the background (on IO thread)
            val IPAddress = withContext(Dispatchers.IO) {
                fetchUserIp(thingId) // Fetch IP asynchronously
            }
            Log.d("Lokesh", "locally saving ip is: $IPAddress")

            // Use the fetched IP address to insert into the database (done on the IO thread)
            val insertResult = withContext(Dispatchers.IO) {
                dbHelper.insertThing(thingName, thingId, thingKey, IPAddress)
            }

            // Check the result and update the UI on the Main thread
            if (insertResult != -1L) {
                Log.d("Lokesh", "Thing details saved locally with IP: $IPAddress")
                refreshUI()
            } else {
                Log.d("Lokesh", "Failed to save thing details locally")
            }
        }
    }


    private fun refreshUI() {
        // Navigate back to AvailableThingsActivity
        val intent = Intent(this@ThingDetailsActivity, AvailableThingsActivity::class.java)
        startActivity(intent)
        finish() // Close the current activity
    }
    // Receive the file from the Raspberry Pi server
    private fun receiveFileFromServer(thingId: String) {
        Thread {
            var clientSocket: Socket? = null
            try {
                // Fetch the IP address asynchronously on the IO thread
                val raspberryPiIp = runBlocking { fetchUserIp(thingId) }

                // Connect to the server using the fetched IP address
                clientSocket = Socket(raspberryPiIp, port)
                val publicKey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQD01140hnh0T1Qav0I0d/1JXB2dIeKOittsKcTV8xWiHlCTyV8rufC8kpa4owbrFGTg1ZlPDksioNvzqxH2QjnUhXn5VvThUc1YjfnU8CzUK63ZvRzGRR4nBsPlEbnVKfmiCvJX1iLPXhgtwlQTRLlAQckT09ZKAIXEbbXg03g6fqIuiXmxm1IPev8a1H5undGiUltpkNxpKu5INYlr+Yd1jj0B95oXzJkGrnZtLirJRr2q86As/XiGnkpjAMyx0GXeGH0HVizhZ7jZPqB5168nfDU+OIvOT3Ns1p9dwsCj8TLXghITIGiFg8DGksm4EqLucNb56DeTfSdClpWyFdT5"
                // Prepare the message to send to the server
                val androidIp = getLocalIpAddress()
                val message1 = "'send_file',$androidIp"
                val checksum = generateChecksum(message1)
                Log.d("checksum", "$checksum")
                val message = "$message1:$checksum@$publicKey"
                clientSocket.getOutputStream().write(message.toByteArray(Charsets.UTF_8))
                clientSocket.getOutputStream().flush()
                Log.d("Socket123", "Message sent: $message")

                // Ensure the Documents directory exists
                val documentsDir = getExternalFilesDir("")

                // Prepare the file for writing
                val file = File(documentsDir, "manifest.json")
                FileOutputStream(file).use { fileOutputStream ->
                    val inputStream = clientSocket.getInputStream()
                    val buffer = ByteArray(1024)
                    var bytesRead: Int

                    // Receive the file from the server
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        fileOutputStream.write(buffer, 0, bytesRead)
                    }
                }
                Log.d("Socket123", "File received and saved as ${file.absolutePath}")

                // Now process the received file and extract the public key
                var manifestContent = file.readText(Charsets.UTF_8)

                // Find the part starting with ssh-rsa
                val sshKeyStartIndex = manifestContent.indexOf("ssh-rsa")
                if (sshKeyStartIndex != -1) {
                    val sshKeyContent = manifestContent.substring(sshKeyStartIndex)

                    // Save the ssh-rsa content to a new file
                    val keyFile = File(documentsDir, "thingPublicKey.txt")
                    keyFile.writeText(sshKeyContent)
                    Log.d("Socket123", "Public key saved as ${keyFile.absolutePath}")

                    // Remove the public key content from manifest.json
                    manifestContent = manifestContent.substring(0, sshKeyStartIndex)
                    file.writeText(manifestContent)
                    Log.d("Socket123", "Manifest file updated and saved as ${file.absolutePath}")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("Socket123", "Error: ${e.message}")
            } finally {
                try {
                    clientSocket?.close()
                    Log.d("Socket123", "Socket closed successfully")
                } catch (e: Exception) {
                    Log.e("Socket123", "Error closing socket: ${e.message}")
                }
            }
        }.start()
    }


    private fun generateChecksum(data: String): String {
        try {
            val python = Python.getInstance()
            val pyResult = python.getModule("hash").callAttr("generate_md5", data)
            return pyResult.toString()  // This is the checksum returned by the Python function
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Python to generate checksum: ${e.message}")
            return ""
        }
    }
    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving local IP: ${e.message}")
        }
        return "0.0.0.0"
    }

    // Generate and send OTP for verification
    private suspend fun sendSms(): String? {
        return try {
            val random = Random.nextInt(0, 9999).toString().padStart(4, '0')
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ThingDetailsActivity, "OTP $random", Toast.LENGTH_SHORT).show()
            }
            random
        } catch (e: Exception) {
            Log.e("ThingDetails", "Error sending OTP: ${e.message}")
            null
        }
    }
//    private suspend fun fetchUserIp(thingId: String): String {
//        return withContext(Dispatchers.IO) {
//            try {
//                val call = RetrofitClient.instance.getUserIp(thingId)
//                val response = call.execute() // Synchronous execution for coroutine compatibility
//
//                if (response.isSuccessful) {
//                    val userIpResponse = response.body()
//                    val ipAddress = userIpResponse?.thingIp
//                    if (!ipAddress.isNullOrEmpty()) {
//                        Log.d("IPFetcher", "Fetched IP address: $ipAddress")
//                        return@withContext ipAddress
//                    } else {
//                        Log.d("IPFetcher", "IP address is null or empty, providing default IP")
//                    }
//                } else {
//                    Log.d("IPFetcher", "Failed to fetch IP address: ${response.errorBody()?.string()}")
//                }
//            } catch (e: Exception) {
//                Log.d("IPFetcher", "Error: ${e.message}")
//            }
//            return@withContext "default_ip" // Default IP in case of failure
//        }
//    }
suspend fun fetchUserIp(thingId: String): String {
    return suspendCoroutine { continuation ->
        val call = RetrofitClient.instance.getUserIp(thingId)
        call.enqueue(object : retrofit2.Callback<UserIpResponse> {
            override fun onResponse(call: Call<UserIpResponse>, response: retrofit2.Response<UserIpResponse>) {
                if (response.isSuccessful) {
                    val ipAddress = response.body()?.thingIp
                    if (ipAddress != null && ipAddress.isNotEmpty()) {
                        continuation.resume(ipAddress)
                    } else {
                        continuation.resume("default_ip")
                    }
                } else {
                    continuation.resume("default_ip")
                }
            }

            override fun onFailure(call: Call<UserIpResponse>, t: Throwable) {
                continuation.resume("default_ip")
            }
        })
    }
}





    interface IpCallback {
        fun onIpFetched(ipAddress: String)
    }
}
