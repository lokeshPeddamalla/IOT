package com.example.iot1

import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.io.OutputStream
import java.net.Socket

class DisplayThingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_display_thing)

        // Load and parse JSON
        val json = loadJSONFromFile("/storage/emulated/0/Android/data/com.example.iot1/files/Documents/manifest.json")
        val jsonObject = Gson().fromJson(json, JsonObject::class.java)

        // Set the thing name from intent
        val thingName = intent.getStringExtra("thing_name")
        findViewById<TextView>(R.id.txtThingName).text = thingName

        // Populate UI with DEVICE info
        jsonObject?.let {
            createDeviceInfoUI(it.getAsJsonObject("DEVICE"))
            createModeUI(it.getAsJsonObject("MODE"))
            createControlUI(it.getAsJsonObject("CONTROL"))
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

    private fun createDeviceInfoUI(deviceObject: JsonObject) {
        val deviceInfoLayout = findViewById<LinearLayout>(R.id.deviceInfoLayout)
        deviceObject.entrySet().forEach { entry ->
            val textView = TextView(this).apply {
                text = "${entry.key}: ${entry.value.asString}"
                textSize = 16f
            }
            deviceInfoLayout.addView(textView)
        }
    }

    private fun createModeUI(modeObject: JsonObject) {
        val modeLayout = findViewById<LinearLayout>(R.id.modeLayout)

        // Only include "COOL" and "DRY" modes
        val modesToDisplay = listOf("COOL", "DRY")
        modesToDisplay.forEach { modeName ->
            modeObject.getAsJsonObject(modeName)?.let { modeDetails ->
                val modeButton = Button(this).apply {
                    text = modeName
                    setOnClickListener {
                        Toast.makeText(this@DisplayThingActivity, "$modeName selected", Toast.LENGTH_SHORT).show()
                    }
                }     
                modeLayout.addView(modeButton)

                modeDetails.entrySet().forEach { detailEntry ->
                    val detailTextView = TextView(this).apply {
                        text = "${detailEntry.key}: ${detailEntry.value}"
                        textSize = 14f
                        setPadding(32, 0, 0, 0)
                    }
                    modeLayout.addView(detailTextView)
                }
            }
        }
    }

    private fun createControlUI(controlObject: JsonObject) {
        val controlLayout = findViewById<LinearLayout>(R.id.controlLayout)
        controlObject.entrySet().forEach { entry ->
            when (entry.key) {
                "Temperature" -> createNumericControlUI(entry.key, entry.value.asJsonObject, controlLayout)
                "Fan" -> createStringControlUI(entry.key, entry.value.asJsonObject, controlLayout)
                "Swing", "Power" -> createBooleanControlUI(entry.key, entry.value.asJsonObject, controlLayout)
            }
        }
    }

    private fun createNumericControlUI(controlName: String, controlDetails: JsonObject, parentLayout: LinearLayout) {
        val range = controlDetails.getAsJsonObject("NUMERIC").getAsJsonArray("RANGE")
        val minValue = range[0].asFloat
        val maxValue = range[1].asFloat
        val step = 0.5f

        parentLayout.addView(TextView(this).apply { text = "$controlName:" })

        val seekBar = SeekBar(this).apply {
            max = ((maxValue - minValue) / step).toInt()
        }
        parentLayout.addView(seekBar)

        // Display the current temperature value
        val valueTextView = TextView(this).apply {
            text = "%.1f".format(minValue + seekBar.progress * step)
        }
        parentLayout.addView(valueTextView)

        // Add increment and decrement buttons
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val decrementButton = Button(this).apply {
            text = "-"
            setOnClickListener {
                val newValue = (seekBar.progress - 1).coerceAtLeast(0)
                seekBar.progress = newValue
                val temperature = minValue + newValue * step
                valueTextView.text = "%.1f".format(temperature)
                Log.d("TemperatureControl", "Temperature decreased to: %.1f".format(temperature))

                sendTemperatureToRpi(temperature)
            }
        }

        val incrementButton = Button(this).apply {
            text = "+"
            setOnClickListener {
                val newValue = (seekBar.progress + 1).coerceAtMost(seekBar.max)
                seekBar.progress = newValue
                val temperature = minValue + newValue * step
                valueTextView.text = "%.1f".format(temperature)
                Log.d("TemperatureControl", "Temperature increased to: %.1f".format(temperature))
                sendTemperatureToRpi(temperature)
            }
        }

        layout.addView(decrementButton)
        layout.addView(incrementButton)

        parentLayout.addView(layout)
    }



    private fun createStringControlUI(controlName: String, controlDetails: JsonObject, parentLayout: LinearLayout) {
        val options = controlDetails.getAsJsonObject("STRING").getAsJsonArray("OPTION").map { it.asString }

        parentLayout.addView(TextView(this).apply { text = "$controlName:" })
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@DisplayThingActivity, android.R.layout.simple_spinner_item, options)
        }
        parentLayout.addView(spinner)
    }

    private fun createBooleanControlUI(controlName: String, controlDetails: JsonObject, parentLayout: LinearLayout) {
        val options = controlDetails.getAsJsonArray("BOOLEAN").map { it.asString }

        parentLayout.addView(TextView(this).apply { text = "$controlName:" })
        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            options.forEach { option ->
                addView(RadioButton(this@DisplayThingActivity).apply { text = option })
            }
        }
        parentLayout.addView(radioGroup)
    }
    private fun sendTemperatureToRpi(temperature: Float) {
        // Replace with your Raspberry Pi's IP address and port
        val rpiIp = "10.203.5.121" // Example IP
        val rpiPort = 8004

        AsyncTask.execute {
            try {
                val socket = Socket(rpiIp, rpiPort)
                val outputStream: OutputStream = socket.getOutputStream()
                outputStream.write("Invoke:$temperature".toByteArray(Charsets.UTF_8))
                outputStream.flush()
                socket.close()
            } catch (e: Exception) {
                Log.e("TemperatureControl", "Error sending temperature to RPi: ${e.message}")
            }
        }
    }
}
