package com.example.iot1

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class BluetoothMainScreen : AppCompatActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothSocket: BluetoothSocket
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false

    private lateinit var et_thing_name: EditText
    private lateinit var et_thing_key: EditText
    private lateinit var et_thing_Id: EditText
    private lateinit var buttonSend: Button

    private var deviceAddress: String? = null // To store the dynamically retrieved MAC address
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val REQUEST_PERMISSION_CODE = 1001

    private val bluetoothConnectionReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    deviceAddress = device.address
                    Log.d("Lokesh", "Connected Device MAC: $deviceAddress")
                    Toast.makeText(context, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
                    connectToDevice() // Initiate connection once a device is connected
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_main_screen)

        et_thing_name = findViewById(R.id.bt_et_thingName)
        et_thing_key = findViewById(R.id.bt_et_thingKey)
        et_thing_Id = findViewById(R.id.bt_et_thingId)
        buttonSend = findViewById(R.id.bt_submit)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))  // Initialize Python with the context
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Register the receiver to listen for connected devices
        registerBluetoothReceiver()

        buttonSend.setOnClickListener {
            sendMessage()
        }

        // Request permissions if needed
        if (!arePermissionsGranted()) {
            requestBluetoothPermissions()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice() {
        if (deviceAddress.isNullOrEmpty()) {
            Log.e("Lokesh", "No device address available to connect")
            return
        }

        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
        Log.d("Lokesh", "Attempting to connect to $device")
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket.connect()
            outputStream = bluetoothSocket.outputStream
            inputStream = bluetoothSocket.inputStream
            isConnected = true
            Toast.makeText(this, "Connection successful", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            e.printStackTrace()
            isConnected = false
            Toast.makeText(this, "Failed to connect to device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendMessage() {
        if (isConnected && outputStream != null) {
            val thingId = et_thing_Id.text.toString()
            val thingKey = et_thing_key.text.toString()

            if (thingId.isNotEmpty() && thingKey.isNotEmpty()) {
                try {
                    val message = "thingId: $thingId thingKey: $thingKey"
                    val checksum = generateChecksum(message)
                    Log.d("BluetoothCheckSum","check sum is : $checksum")
                    val FinalMessage1 = "$message + $checksum"
                    Log.d("BluetoothCheckSum","final message is : $FinalMessage1")
                    val publicKey = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQD01140hnh0T1Qav0I0d/1JXB2dIeKOittsKcTV8xWiHlCTyV8rufC8kpa4owbrFGTg1ZlPDksioNvzqxH2QjnUhXn5VvThUc1YjfnU8CzUK63ZvRzGRR4nBsPlEbnVKfmiCvJX1iLPXhgtwlQTRLlAQckT09ZKAIXEbbXg03g6fqIuiXmxm1IPev8a1H5undGiUltpkNxpKu5INYlr+Yd1jj0B95oXzJkGrnZtLirJRr2q86As/XiGnkpjAMyx0GXeGH0HVizhZ7jZPqB5168nfDU+OIvOT3Ns1p9dwsCj8TLXghITIGiFg8DGksm4EqLucNb56DeTfSdClpWyFdT5"
                    val FinalMessage = "$FinalMessage1%$publicKey"
                    outputStream!!.write(FinalMessage.toByteArray())
                    startReceivingFile()
                    SplitFiles()
                    Log.d("BluetoothCheckSum","file received moving to next activity")
                    val intent = Intent(this, BluetoothDisplayThingActivity::class.java).apply {
                        putExtra("bluetooth_device_address", deviceAddress)
                    }
                    startActivity(intent)

                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun startReceivingFile() {
        GlobalScope.launch(Dispatchers.IO) {
            receiveFile()
        }
    }
    private fun SplitFiles() {
        Log.d("BluetoothCheckSum", "starting the split function")
        val randomFile = File(getExternalFilesDir(null), "random.json")
        val manifestFile = File(getExternalFilesDir(null), "manifest.json")
        Log.d("BluetoothCheckSum", "check random.json")

        var manifestContent = randomFile.readText(Charsets.UTF_8)
        Log.d("BluetoothCheckSum", "reading random.json")

        // Split the manifest content and extract the public key
        val sshKeyStartIndex = manifestContent.indexOf("ssh-rsa")
        if (sshKeyStartIndex != -1) {
            // Extract the ssh-rsa content (public key)
            val sshKeyContent = manifestContent.substring(sshKeyStartIndex)

            // Save the ssh-rsa content to a new file
            val keyFile = File(getExternalFilesDir(null), "thingPublicKey.txt")
            keyFile.writeText(sshKeyContent)
            Log.d("BluetoothCheckSum", "Public key saved as ${keyFile.absolutePath}")

            // Remove the public key content from the manifest
            manifestContent = manifestContent.substring(0, sshKeyStartIndex)
            Log.d("BluetoothCheckSum", "$manifestContent")

            // Save the updated manifest content (without the public key) to manifest.json
            manifestFile.writeText(manifestContent)
            Log.d("BluetoothCheckSum", "Manifest file updated and saved as ${manifestFile.absolutePath}")
        } else {
            Log.d("BluetoothCheckSum", "No public key found in the manifest content.")
        }
    }
    private fun generateChecksum(data: String): String {
        try {
            val python = Python.getInstance()
            val pyResult = python.getModule("hash").callAttr("generate_md5", data)
            return pyResult.toString()  // This is the checksum returned by the Python function
        } catch (e: Exception) {
            Log.d("BluetoothCheckSum", "Error calling Python to generate checksum: ${e.message}")
            return ""
        }
    }
    private fun receiveFile() {
        if (isConnected && inputStream != null) {
            if (!isExternalStorageWritable()) {
                Log.e("Lokesh", "External storage is not writable")
                return
            }
            var fileOutputStream: FileOutputStream? = null
            try {
                // Save the manifest.json file
                val file = File(getExternalFilesDir(null), "random.json")
                fileOutputStream = FileOutputStream(file)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (true) {
                    try {
                        bytesRead = inputStream!!.read(buffer)
                        if (bytesRead == -1) break
                        fileOutputStream.write(buffer, 0, bytesRead)
                    } catch (e: IOException) {
                        break
                    }
                }

                // Read the manifest file content

            } catch (e: IOException) {
                Log.e("Lokesh", "Error: ${e.message}")
            } finally {
                try {
                    fileOutputStream?.close()
                } catch (e: IOException) {
                    Log.e("Lokesh", "Error closing FileOutputStream: ${e.message}")
                }
            }
        }
    }


    private fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    private fun arePermissionsGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_PERMISSION_CODE
            )
        }
    }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        registerReceiver(bluetoothConnectionReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothConnectionReceiver)
        try {
            if (isConnected) {
                bluetoothSocket.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
