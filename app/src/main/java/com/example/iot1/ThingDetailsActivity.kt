package com.example.iot1

import DBHelper
import RegistrationResult
import RetrofitClient
import ThingDetails
import UserIpResponse
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
import com.example.iot1.databinding.ActivityThingDetailsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.net.Socket
import kotlin.random.Random

class ThingDetailsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityThingDetailsBinding
    private lateinit var dbHelper: DBHelper
    private var generatedOtp: String? = null

    private val raspberryPiIp = "10.203.1.88"
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
                        receiveFileFromServer()

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
        fetchUserIp(thingId, object : IpCallback {
            override fun onIpFetched(ipAddress: String) {
                // Use the fetched IP address in your save function
                val insertResult = dbHelper.insertThing(thingName, thingId, thingKey, ipAddress)
                if (insertResult != -1L) {
                    Log.d("ThingDetails", "Thing details saved locally with IP: $ipAddress")
                    refreshUI()
                } else {
                    Log.d("ThingDetails", "Failed to save thing details locally")
                }
            }
        })
    }

    private fun refreshUI() {
        // Navigate back to AvailableThingsActivity
        val intent = Intent(this@ThingDetailsActivity, AvailableThingsActivity::class.java)
        startActivity(intent)
        finish() // Close the current activity
    }
    // Receive the file from the Raspberry Pi server
    private fun receiveFileFromServer() {
        Thread {
            var clientSocket: Socket? = null
            try {
                // Connect to the server
                clientSocket = Socket(raspberryPiIp, port)

                // Send message to the server
                val message = "send_file"
                clientSocket.getOutputStream().write(message.toByteArray(Charsets.UTF_8))
                clientSocket.getOutputStream().flush()
                Log.d("Socket123", "Message sent: $message")

                // Ensure the Documents directory exists
                val documentsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                if (documentsDir != null && !documentsDir.exists()) {
                    documentsDir.mkdirs()
                    Log.d("Socket123", "Documents directory created at ${documentsDir.absolutePath}")
                }

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
    fun fetchUserIp(thingId: String, callback: IpCallback) {
        val call = RetrofitClient.instance.getUserIp(thingId)
        call.enqueue(object : retrofit2.Callback<UserIpResponse> {
            override fun onResponse(call: Call<UserIpResponse>, response: retrofit2.Response<UserIpResponse>) {
                Log.d("IPFetcher", "Fetching IP for thingId: $thingId")
                if (response.isSuccessful) {
                    val userIpResponse = response.body()
                    Log.d("IPFetcher", "Response code: ${response.code()}")
                    Log.d("IPFetcher", "Response body: $userIpResponse")

                    val ipAddress = userIpResponse?.thingIp
                    if (ipAddress != null && ipAddress.isNotEmpty()) {
                        Log.d("IPFetcher", "Fetched IP address: $ipAddress")
                        callback.onIpFetched(ipAddress)
                    } else {
                        Log.d("IPFetcher", "IP address is null or empty, providing default IP")
                        callback.onIpFetched("default_ip")
                    }
                } else {
                    Log.d("IPFetcher", "Failed to fetch IP address: ${response.errorBody()?.string()}")
                    callback.onIpFetched("default_ip")
                }
            }

            override fun onFailure(call: Call<UserIpResponse>, t: Throwable) {
                Log.d("IPFetcher", "Error: ${t.message}")
                callback.onIpFetched("default_ip")
            }
        })
    }




    interface IpCallback {
        fun onIpFetched(ipAddress: String)
    }
}
