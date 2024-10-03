package com.example.iot1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.FileObserver
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class BluetoothMainScreen : AppCompatActivity() {
    private lateinit var thingNameEditText: EditText
    private lateinit var thingKeyEditText: EditText
    private lateinit var thingIdEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var fileObserver: FileObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_main_screen)
        thingNameEditText = findViewById(R.id.bt_et_thingName)
        thingKeyEditText = findViewById(R.id.bt_et_thingKey)
        thingIdEditText = findViewById(R.id.bt_et_thingId)
        sendButton = findViewById(R.id.bt_submit)

        // Start monitoring the Downloads directory for the ac.json file
        monitorBluetoothDirectory()

        sendButton.setOnClickListener {
            sendButton.isEnabled = false // Disable the button while sending

            val dummyFile = createDummyFile()

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, dummyFileUri(dummyFile))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            intent.setPackage("com.android.bluetooth")

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(Intent.createChooser(intent, "Share file"))
                sendButton.postDelayed({
                    sendButton.isEnabled = true
                }, 3000) // Adjust delay as needed
            } else {
                sendButton.isEnabled = true
                Toast.makeText(this, "Bluetooth sharing not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createDummyFile(): File {
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val dummyFile = File(storageDir, "data.txt")

        val thingKey = thingKeyEditText.text.toString()
        val thingId = thingIdEditText.text.toString()

        val fileContent = """
            Thing Key: $thingKey
            Thing Id: $thingId
        """.trimIndent()

        dummyFile.writeText(fileContent)
        return dummyFile
    }

    private fun dummyFileUri(file: File): Uri {
        return FileProvider.getUriForFile(
            this,
            "com.example.IOT1.provider",
            file
        )
    }

    private fun monitorBluetoothDirectory() {
        val pathToMonitor = "/storage/sdcard0/Download/Bluetooth/"

        fileObserver = object : FileObserver(pathToMonitor, CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (path != null && path.endsWith("ac.json")) {
                    // ac.json file has been created, stop monitoring
                    fileObserver.stopWatching()

                    // Start the BluetoothDisplayThingActivity
                    val intent = Intent(this@BluetoothMainScreen, BluetoothDisplayThingActivity::class.java)
                    startActivity(intent)
                }
            }
        }

        // Start watching the directory
        fileObserver.startWatching()
    }

    override fun onDestroy() {
        super.onDestroy()
        fileObserver.stopWatching() // Stop watching the directory when the activity is destroyed
    }
}
