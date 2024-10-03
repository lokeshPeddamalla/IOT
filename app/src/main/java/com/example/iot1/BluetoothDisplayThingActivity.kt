package com.example.iot1

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class BluetoothDisplayThingActivity : AppCompatActivity() {

    private lateinit var temperatureSeekBar: SeekBar
    private lateinit var fanSpinner: Spinner
    private lateinit var modeSpinner: Spinner
    private lateinit var swingRadioGroup: RadioGroup
    private lateinit var powerSwitch: Switch
    private var minTemp: Float = 14f
    private var maxTemp: Float = 30f
    private var stepTemp: Float = 0.5f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_display_thing_activity) // Ensure your layout is set here

        initializeUIComponents()

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
    }

    private fun initializeUIComponents() {
        temperatureSeekBar = findViewById(R.id.temperature_seekbar)
        findViewById<Button>(R.id.temperature_decrease).setOnClickListener { decreaseTemperature() }
        findViewById<Button>(R.id.temperature_increase).setOnClickListener { increaseTemperature() }
        fanSpinner = findViewById(R.id.fan_spinner)
        swingRadioGroup = findViewById(R.id.swing_radio_group)
        modeSpinner = findViewById(R.id.mode_spinner)
        powerSwitch = findViewById(R.id.power_switch)

        setupSpinners()
        setupTemperatureSeekBar()
    }

    private fun setupSpinners() {
        // Populate mode spinner with static values
        val modes = arrayOf("COOL", "DRY")
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modeSpinner.adapter = modeAdapter

        // Populate fan spinner with static values
        val fanOptions = arrayOf("Low", "Medium", "High")
        val fanAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fanOptions)
        fanAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fanSpinner.adapter = fanAdapter
    }

    private fun setupTemperatureSeekBar() {
        // Set SeekBar max based on the range
        temperatureSeekBar.max = ((maxTemp - minTemp) / stepTemp).toInt()
        temperatureSeekBar.progress = 0 // Starting at minTemp
    }

    private fun decreaseTemperature() {
        val currentValue = temperatureSeekBar.progress
        if (currentValue > 0) {
            temperatureSeekBar.progress = currentValue - 1
            val newTemp = minTemp + (temperatureSeekBar.progress * stepTemp)
            sendTemperatureToRpi(newTemp)
        }
    }

    private fun increaseTemperature() {
        val currentValue = temperatureSeekBar.progress
        if (currentValue < temperatureSeekBar.max) {
            temperatureSeekBar.progress = currentValue + 1
            val newTemp = minTemp + (temperatureSeekBar.progress * stepTemp)
            sendTemperatureToRpi(newTemp)
        }
    }

    private fun sendTemperatureToRpi(temperature: Float) {
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val tempFile = File(storageDir, "temperature.txt")
        tempFile.writeText("Current temperature: $temperature°C")

        // Create an intent to share the temperature file via Bluetooth
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, dummyFileUri(tempFile))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        intent.setPackage("com.android.bluetooth")

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(Intent.createChooser(intent, "Share temperature"))
        } else {
            Toast.makeText(this, "Bluetooth sharing not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dummyFileUri(file: File): Uri {
        return FileProvider.getUriForFile(this, "com.example.IOT1.provider", file)
    }
}
