package com.example.iot1

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
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

    private val deviceAddress = "D8:3A:DD:9F:DC:16"
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_main_screen)

        et_thing_name = findViewById(R.id.bt_et_thingName)
        et_thing_key = findViewById(R.id.bt_et_thingKey)
        et_thing_Id = findViewById(R.id.bt_et_thingId)

        buttonSend = findViewById(R.id.bt_submit)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        connectToDevice()

        buttonSend.setOnClickListener {
            sendMessage()
        }
    }


    private fun sendMessage() {
        if (isConnected && outputStream != null) {
            val thingId = et_thing_Id.text.toString()
            val thingKey = et_thing_key.text.toString()

            if (thingId.isNotEmpty() && thingKey.isNotEmpty()) {
                try {
                    val message = "thingId: $thingId thingKey: $thingKey"
                    outputStream!!.write(message.toByteArray())
                    startReceivingFile() // Call to receive the file

                    // Pass the Bluetooth device address and output stream to the next activity
                    val intent = Intent(this@BluetoothMainScreen, BluetoothDisplayThingActivity::class.java).apply {
                        putExtra("bluetooth_device_address", deviceAddress)
                    }
                    startActivity(intent)

                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }


    @SuppressLint("MissingPermission")
    private fun connectToDevice() {
        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket.connect()
            outputStream = bluetoothSocket.outputStream
            inputStream = bluetoothSocket.inputStream
            isConnected = true
        } catch (e: IOException) {
            e.printStackTrace()
            isConnected = false
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun startReceivingFile() {
        GlobalScope.launch(Dispatchers.IO) { // Run in the IO context
            receiveFile()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun receiveFile() {
        if (isConnected && inputStream != null) {
            if (!isExternalStorageWritable()) {
                Log.e("ReceiveFile", "External storage is not writable")
                return
            }

            var fileOutputStream: FileOutputStream? = null
            try {
                // Define the path to save the file in the app's external files directory
                val file = File(getExternalFilesDir(null), "manifest.json") // Safe location

                // Log the file path
                Log.d("ReceiveFile", "File will be saved to: ${file.absolutePath}")

                // Use FileOutputStream to write to the specified file
                fileOutputStream = FileOutputStream(file)
                Log.d("ReceiveFile", "FileOutputStream created successfully")

                val buffer = ByteArray(8192)
                var bytesRead: Int
                Log.d("ReceiveFile", "Starting to read from inputStream")

                // Read data from inputStream and write to file
                while (true) {
                    try {
                        bytesRead = inputStream!!.read(buffer)
                        if (bytesRead == -1) break // End of stream
                        fileOutputStream.write(buffer, 0, bytesRead)
                        Log.d("ReceiveFile", "Written $bytesRead bytes to file")
                    } catch (e: IOException) {
                        Log.e("ReceiveFile", "Error reading from inputStream: ${e.message}")
                        break // Break out of the loop on read error
                    }
                }

                Log.d("ReceiveFile", "File receiving completed successfully")

            } catch (e: IOException) {
                Log.e("ReceiveFile", "Error receiving file: ${e.message}")
            } catch (e: Exception) {
                Log.e("ReceiveFile", "Unexpected error: ${e.message}")
            } finally {
                // Close the FileOutputStream safely in the finally block
                try {
                    fileOutputStream?.close()
                    Log.d("ReceiveFile", "FileOutputStream closed successfully")
                } catch (e: IOException) {
                    Log.e("ReceiveFile", "Error closing FileOutputStream: ${e.message}")
                }
            }
        } else {
            Log.d("ReceiveFile", "Not connected or inputStream is null")
        }
    }

    private fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (isConnected) {
                bluetoothSocket.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
