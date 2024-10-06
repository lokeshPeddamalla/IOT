package com.example.iot1

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DisplayThingActivity : AppCompatActivity() {
    private lateinit var temperatureSeekBar: SeekBar
    private lateinit var fanSpinner: Spinner
    private lateinit var swingRadioGroup: RadioGroup
    private lateinit var modeSpinner: Spinner
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 2000L
    private var minTemp: Float = 0f
    private var maxTemp: Float = 0f
    private var stepTemp: Float = 0f
    private lateinit var rpiIp: String // Add this line

    // Added properties for fan speed, mode, swing, and power
    private lateinit var fanSpeed: String
    private lateinit var selectedMode: String
    private lateinit var swingDirection: String
    private var powerState: String = "OFF" // Initial power state

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.ac)
        rpiIp = intent.getStringExtra("ip_address") ?: throw IllegalArgumentException("IP address must be provided")

        // Load and parse JSON
        val json = loadJSONFromFile("/storage/emulated/0/Android/data/com.example.iot1/files/Documents/manifest.json")
        val jsonObject = Gson().fromJson(json, JsonObject::class.java)

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
    }

    private fun loadJSONFromFile(filePath: String): String? {
        return try {
            val file = File(filePath)
            file.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            e.printStackTrace()
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

        // Set up listeners for fan spinner, mode spinner, and swing radio group
        fanSpinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                fanSpeed = parent.getItemAtPosition(position) as String
                sendFanSpeedToRpi(fanSpeed)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        })

        modeSpinner.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                selectedMode = parent.getItemAtPosition(position) as String
                sendModeToRpi(selectedMode)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        })

        swingRadioGroup.setOnCheckedChangeListener { group, checkedId ->
            val selectedSwing = if (checkedId == R.id.swing_up) "Up" else "Down"
            sendSwingToRpi(selectedSwing)
        }

        // Set up a toggle button or switch for power state
        val powerSwitch = findViewById<Switch>(R.id.power_switch)
        powerSwitch.setOnCheckedChangeListener { _, isChecked ->
            powerState = if (isChecked) "ON" else "OFF"
            sendPowerStateToRpi(powerState)
        }
    }

    // Populate mode spinner dynamically from the JSON
    private fun populateModeSpinner(modeObject: JsonObject) {
        val modes = modeObject.entrySet().map { it.key }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modeSpinner.adapter = adapter
    }

    // Populate fan spinner dynamically from the JSON
    private fun populateFanSpinner(fanObject: JsonObject) {
        val fanOptions = fanObject.getAsJsonArray("OPTION").map { it.asString }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, fanOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        fanSpinner.adapter = adapter
    }

    private fun setupTemperatureRange(temperatureControl: JsonObject) {
        val rangeArray = temperatureControl.getAsJsonArray("RANGE")
        minTemp = rangeArray[0].asFloat  // Min temperature (e.g., 14)
        maxTemp = rangeArray[1].asFloat  // Max temperature (e.g., 30)
        stepTemp = rangeArray[2].asFloat // Step value (e.g., 0.5)

        // Set SeekBar max based on the range and step size
        temperatureSeekBar.max = ((maxTemp - minTemp) / stepTemp).toInt()

        // Initialize SeekBar position (could be set based on current temperature or minTemp)
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

    // Increase temperature based on step value from JSON
    private fun increaseTemperature() {
        val currentValue = temperatureSeekBar.progress
        if (currentValue < temperatureSeekBar.max) {
            temperatureSeekBar.progress = currentValue + 1
            val newTemp = minTemp + (temperatureSeekBar.progress * stepTemp)
            sendTemperatureToRpi(newTemp)
        }
    }

    private fun sendTemperatureToRpi(temperature: Float) {
        val rpiPort = 8004
        Thread {
            var attempt = 0
            var success = false

            while (attempt < MAX_RETRIES && !success) {
                try {
                    val socket = Socket().apply {
                        soTimeout = 5000 // Set socket timeout
                    }
                    socket.connect(java.net.InetSocketAddress(rpiIp, rpiPort), 5000) // Use the dynamic IP
                    val outputStream: OutputStream = socket.getOutputStream()
                    outputStream.write("Invoke:$temperature".toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                    socket.close()
                    success = true
                    Log.i(TAG, "Temperature sent successfully: $temperature")
                } catch (e: SocketTimeoutException) {
                    Log.e(TAG, "Connection timed out. Retrying...")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending temperature to RPi: ${e.message}")
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
                Log.e(TAG, "Failed to send temperature after $MAX_RETRIES attempts")
            }
        }.start()
    }

    // Send fan speed to Raspberry Pi
    private fun sendFanSpeedToRpi(fanSpeed: String) {
        val rpiPort = 8004
        Thread {
            var attempt = 0
            var success = false

            while (attempt < MAX_RETRIES && !success) {
                try {
                    val socket = Socket().apply {
                        soTimeout = 5000 // Set socket timeout
                    }
                    socket.connect(java.net.InetSocketAddress(rpiIp, rpiPort), 5000) // Use the dynamic IP
                    val outputStream: OutputStream = socket.getOutputStream()
                    outputStream.write("FanSpeed:$fanSpeed".toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                    socket.close()
                    success = true
                    Log.i(TAG, "Fan speed sent successfully: $fanSpeed")
                } catch (e: SocketTimeoutException) {
                    Log.e(TAG, "Connection timed out. Retrying...")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending fan speed to RPi: ${e.message}")
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
                Log.e(TAG, "Failed to send fan speed after $MAX_RETRIES attempts")
            }
        }.start()
    }

    // Send selected mode to Raspberry Pi
    private fun sendModeToRpi(mode: String) {
        val rpiPort = 8004
        Thread {
            var attempt = 0
            var success = false

            while (attempt < MAX_RETRIES && !success) {
                try {
                    val socket = Socket().apply {
                        soTimeout = 5000 // Set socket timeout
                    }
                    socket.connect(java.net.InetSocketAddress(rpiIp, rpiPort), 5000) // Use the dynamic IP
                    val outputStream: OutputStream = socket.getOutputStream()
                    outputStream.write("Mode:$mode".toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                    socket.close()
                    success = true
                    Log.i(TAG, "Mode sent successfully: $mode")
                } catch (e: SocketTimeoutException) {
                    Log.e(TAG, "Connection timed out. Retrying...")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending mode to RPi: ${e.message}")
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
                Log.e(TAG, "Failed to send mode after $MAX_RETRIES attempts")
            }
        }.start()
    }

    // Send swing direction to Raspberry Pi
    private fun sendSwingToRpi(swing: String) {
        val rpiPort = 8004
        Thread {
            var attempt = 0
            var success = false

            while (attempt < MAX_RETRIES && !success) {
                try {
                    val socket = Socket().apply {
                        soTimeout = 5000 // Set socket timeout
                    }
                    socket.connect(java.net.InetSocketAddress(rpiIp, rpiPort), 5000) // Use the dynamic IP
                    val outputStream: OutputStream = socket.getOutputStream()
                    outputStream.write("Swing:$swing".toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                    socket.close()
                    success = true
                    Log.i(TAG, "Swing direction sent successfully: $swing")
                } catch (e: SocketTimeoutException) {
                    Log.e(TAG, "Connection timed out. Retrying...")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending swing direction to RPi: ${e.message}")
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
                Log.e(TAG, "Failed to send swing direction after $MAX_RETRIES attempts")
            }
        }.start()
    }

    // Send power state to Raspberry Pi
    private fun sendPowerStateToRpi(state: String) {
        val rpiPort = 8004
        Thread {
            var attempt = 0
            var success = false

            while (attempt < MAX_RETRIES && !success) {
                try {
                    val socket = Socket().apply {
                        soTimeout = 5000 // Set socket timeout
                    }
                    socket.connect(java.net.InetSocketAddress(rpiIp, rpiPort), 5000) // Use the dynamic IP
                    val outputStream: OutputStream = socket.getOutputStream()
                    outputStream.write("Power:$state".toByteArray(Charsets.UTF_8))
                    outputStream.flush()
                    socket.close()
                    success = true
                    Log.i(TAG, "Power state sent successfully: $state")
                } catch (e: SocketTimeoutException) {
                    Log.e(TAG, "Connection timed out. Retrying...")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending power state to RPi: ${e.message}")
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
                Log.e(TAG, "Failed to send power state after $MAX_RETRIES attempts")
            }
        }.start()
    }
}
