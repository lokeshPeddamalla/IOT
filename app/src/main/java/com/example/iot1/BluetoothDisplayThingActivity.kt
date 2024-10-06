package com.example.iot1

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothDisplayThingActivity : AppCompatActivity() {
    private lateinit var temperatureSeekBar: SeekBar
    private lateinit var fanSpinner: Spinner
    private lateinit var swingRadioGroup: RadioGroup
    private lateinit var modeSpinner: Spinner
    private lateinit var powerSwitch: Switch
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 2000L
    private var minTemp: Float = 0f
    private var maxTemp: Float = 0f
    private var stepTemp: Float = 0f
    private lateinit var bluetoothDeviceAddress: String // Bluetooth MAC address
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false

    // Bluetooth variables
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothSocket: BluetoothSocket
    private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34DB") // Standard UUID for serial devices

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.ac)

        bluetoothDeviceAddress = intent.getStringExtra("bluetooth_device_address") ?: throw IllegalArgumentException("Bluetooth device address must be provided")
        Log.d("Lokesh", bluetoothDeviceAddress)

        // Load and parse JSON
        val json = loadJSONFromFile()
        val jsonObject = Gson().fromJson(json, JsonObject::class.java)
        Log.d("Lokesh", "JSON file is loaded")

        // Initialize UI components
        initializeUIComponents()

        // Populate UI with DEVICE info
        jsonObject?.let {
            val control = it.getAsJsonObject("CONTROL")
            val temperatureControl = control.getAsJsonObject("Temperature").getAsJsonObject("NUMERIC")
            setupTemperatureRange(temperatureControl)
            populateModeSpinner(it.getAsJsonObject("MODE"))
            populateFanSpinner(it.getAsJsonObject("CONTROL").getAsJsonObject("Fan").getAsJsonObject("STRING"))
        }
        Log.d("Lokesh", "UI components loaded")

        // Initialize Bluetooth
        setupBluetooth()
        Log.d("Lokesh", "Bluetooth setup done")

        // Connect to Bluetooth device
        checkBluetoothPermissions()
    }

    // Load JSON from file
    private fun loadJSONFromFile(): String? {
        return try {
            val file = File(getExternalFilesDir(null), "manifest.json") // Correct path
            file.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading JSON file: ${e.message}")
            null
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

        // Add listeners for spinner changes
        fanSpinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedFan = fanSpinner.selectedItem.toString()
                sendFanSpeedToBluetooth(selectedFan)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        })

        modeSpinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedMode = modeSpinner.selectedItem.toString()
                sendModeToBluetooth(selectedMode)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        })

        // Add listener for swing selection
        swingRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            val selectedSwing = if (checkedId == R.id.swing_up) "Up" else "Down"
            sendSwingToBluetooth(selectedSwing)
        }

        // Add listener for power switch
        powerSwitch.setOnCheckedChangeListener { _, isChecked ->
            val powerState = if (isChecked) "ON" else "OFF"
            sendPowerStateToBluetooth(powerState)
        }
    }

    private fun populateModeSpinner(modeObject: JsonObject) {
        val modes = modeObject.entrySet().map { it.key }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modeSpinner.adapter = adapter
    }

    private fun populateFanSpinner(fanObject: JsonObject) {
        val fanOptions = fanObject.getAsJsonArray("OPTION").map { it.asString }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fanOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fanSpinner.adapter = adapter
    }

    private fun setupTemperatureRange(temperatureControl: JsonObject) {
        val rangeArray = temperatureControl.getAsJsonArray("RANGE")
        minTemp = rangeArray[0].asFloat
        maxTemp = rangeArray[1].asFloat
        stepTemp = rangeArray[2].asFloat

        temperatureSeekBar.max = ((maxTemp - minTemp) / stepTemp).toInt()
        temperatureSeekBar.progress = 0 // Starting at minTemp
    }

    private fun decreaseTemperature() {
        val currentValue = temperatureSeekBar.progress
        if (currentValue > 0) {
            temperatureSeekBar.progress = currentValue - 1
            val newTemp = minTemp + (temperatureSeekBar.progress * stepTemp)
            sendTemperatureToBluetooth(newTemp)
        }
    }

    private fun increaseTemperature() {
        val currentValue = temperatureSeekBar.progress
        if (currentValue < temperatureSeekBar.max) {
            temperatureSeekBar.progress = currentValue + 1
            val newTemp = minTemp + (temperatureSeekBar.progress * stepTemp)
            sendTemperatureToBluetooth(newTemp)
        }
    }

    // Initialize Bluetooth connection
    @SuppressLint("MissingPermission")
    private fun setupBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // Check if Bluetooth is enabled
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // Connect to the Bluetooth device
    @SuppressLint("MissingPermission")
    private fun connectToBluetoothDevice() {
        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(bluetoothDeviceAddress)

        try {
            // Create an RFCOMM socket to connect to the device
            bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)

            // Cancel any ongoing discovery to optimize connection
            bluetoothAdapter.cancelDiscovery()

            // Connect to the remote device
            bluetoothSocket.connect()

            // Get the input and output streams
            outputStream = bluetoothSocket.outputStream
            inputStream = bluetoothSocket.inputStream

            isConnected = true
            Log.d(TAG, "Connected to Bluetooth device")

        } catch (e: IOException) {
            e.printStackTrace()
            isConnected = false
            Log.e(TAG, "Could not connect to Bluetooth device: ${e.message}")
        }
    }

    // Send temperature via Bluetooth
    private fun sendTemperatureToBluetooth(temperature: Float) {
        Thread {
            var attempt = 0
            var success = false

            while (attempt < MAX_RETRIES && !success) {
                try {
                    outputStream?.write("Received Temperature: $temperature".toByteArray(Charsets.UTF_8))
                    outputStream?.flush()
                    success = true
                    Log.i(TAG, "Temperature sent successfully via Bluetooth: $temperature")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending temperature via Bluetooth: ${e.message}")
                }

                attempt++
                if (!success) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (e: InterruptedException) {
                        Log.e(TAG, "Retry delay interrupted: ${e.message}")
                    }
                }
            }

            if (!success) {
                Log.e(TAG, "Failed to send temperature after $MAX_RETRIES attempts via Bluetooth")
            }
        }.start()
    }

    // Send fan speed via Bluetooth
    private fun sendFanSpeedToBluetooth(fanSpeed: String) {
        Thread {
            var attempt = 0
            var success = false

            while (attempt < MAX_RETRIES && !success) {
                try {
                    outputStream?.write("Selected Fan Speed: $fanSpeed".toByteArray(Charsets.UTF_8))
                    outputStream?.flush()
                    success = true
                    Log.i(TAG, "Fan speed sent successfully via Bluetooth: $fanSpeed")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending fan speed via Bluetooth: ${e.message}")
                }

                attempt++
                if (!success) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (e: InterruptedException) {
                        Log.e(TAG, "Retry delay interrupted: ${e.message}")
                    }
                }
            }

            if (!success) {
                Log.e(TAG, "Failed to send fan speed after $MAX_RETRIES attempts via Bluetooth")
            }
        }.start()
    }

    // Send mode via Bluetooth
    private fun sendModeToBluetooth(mode: String) {
        Thread {
            var attempt = 0
            var success = false

            while (attempt < MAX_RETRIES && !success) {
                try {
                    outputStream?.write("Selected Mode: $mode".toByteArray(Charsets.UTF_8))
                    outputStream?.flush()
                    success = true
                    Log.i(TAG, "Mode sent successfully via Bluetooth: $mode")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending mode via Bluetooth: ${e.message}")
                }

                attempt++
                if (!success) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (e: InterruptedException) {
                        Log.e(TAG, "Retry delay interrupted: ${e.message}")
                    }
                }
            }

            if (!success) {
                Log.e(TAG, "Failed to send mode after $MAX_RETRIES attempts via Bluetooth")
            }
        }.start()
    }

    // Send swing direction via Bluetooth
    private fun sendSwingToBluetooth(swingDirection: String) {
        Thread {
            var attempt = 0
            var success = false

            while (attempt < MAX_RETRIES && !success) {
                try {
                    outputStream?.write("Selected Swing: $swingDirection".toByteArray(Charsets.UTF_8))
                    outputStream?.flush()
                    success = true
                    Log.i(TAG, "Swing direction sent successfully via Bluetooth: $swingDirection")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending swing direction via Bluetooth: ${e.message}")
                }

                attempt++
                if (!success) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (e: InterruptedException) {
                        Log.e(TAG, "Retry delay interrupted: ${e.message}")
                    }
                }
            }

            if (!success) {
                Log.e(TAG, "Failed to send swing direction after $MAX_RETRIES attempts via Bluetooth")
            }
        }.start()
    }

    // Send power state via Bluetooth
    private fun sendPowerStateToBluetooth(powerState: String) {
        Thread {
            var attempt = 0
            var success = false

            while (attempt < MAX_RETRIES && !success) {
                try {
                    outputStream?.write("Power State: $powerState".toByteArray(Charsets.UTF_8))
                    outputStream?.flush()
                    success = true
                    Log.i(TAG, "Power state sent successfully via Bluetooth: $powerState")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending power state via Bluetooth: ${e.message}")
                }

                attempt++
                if (!success) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (e: InterruptedException) {
                        Log.e(TAG, "Retry delay interrupted: ${e.message}")
                    }
                }
            }

            if (!success) {
                Log.e(TAG, "Failed to send power state after $MAX_RETRIES attempts via Bluetooth")
            }
        }.start()
    }

    // Check Bluetooth permissions
    private fun checkBluetoothPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
        } else {
            connectToBluetoothDevice()
        }
    }
}
