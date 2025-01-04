package com.example.iot1

import android.content.ContentValues.TAG
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DisplayThingActivity : AppCompatActivity() {

    private lateinit var rpiIp: String
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 1000L
    private val validEndKeywords = listOf(
        "S", "START", "OPTIONAL", "SENSOR", "ACTUATOR", "DEVICE", "OEM", "MODE", "LOCATION", "D", "NAME", "DOMAIN",
        "WARRANTY", "INSTALLATION", "SW VERSION", "HW VERSION", "O", "SOCKET", "M", "NAME VAL", "Z", "DATA",
        "ACTUATOR INFO", "VALUE", "UNIT VAL", "AREA", "X", "DESC", "SENSOR TYPE", "STRUCT X", "BATTERY", "GRAPH",
        "DATA TYPE", "PIE", "BAR", "GRAPH TYPE", "CORD1", "CORD2", "BOOLEAN", "NUMERIC", "STRING", "IMAGE", "AUDIO",
        "VIDEO", "MAP", "COLOR", "DATE", "TIME", "RANGE", "TUPLE", "UNIT", "R", "RVAL", "MIN", "MAX", "STEP",
        "UNIT VAL", "OPERATION", "EXCEPT", "TVAL", "OPTION", "OVAL", "SIZE", "IMAGE ATTRIB", "LENGTH", "WIDTH", "SRC",
        "IMAGE TYPE", "PLAYER TYPE", "PLAYER ATTRIB", "LOOP", "MUTE", "ACTUATOR", "Y", "ACTUATOR TYPE", "STRUCT Y"
    )
    private val rangeCategories = listOf(
        setOf("MIN", "MAX", "STEP", "OPERATION"),
        setOf("MIN", "MAX", "STEP", "OPERATION", "UNIT VAL"),
        setOf("MIN", "MAX", "STEP", "OPERATION", "UNIT VAL", "EXCEPT")
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_display_thing)
        rpiIp = intent.getStringExtra("ip_address") ?: throw IllegalArgumentException("IP address must be provided")
        Log.d("lokesh", rpiIp)
        val json = loadJSONFromFile("/sdcard/Android/data/com.example.iot1/files/Documents/manifest.json")
        if (json.isEmpty()) {
            Log.e("MainActivity2", "JSON is null or empty")
            return
        }
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))  // Initialize Python with the context
        }

        val gson = Gson()
        val jsonObject = gson.fromJson(json, JsonObject::class.java)
        if (jsonObject == null) {
            Log.e("MainActivity2", "Failed to parse JSON into JsonObject")
            return
        }

        processJsonObject(JSONObject(jsonObject.toString()))
        addUIElements(JSONObject(jsonObject.toString()))
    }

    private fun loadJSONFromFile(filePath: String): String {
        val file = File(filePath)
        val stringBuilder = StringBuilder()
        try {
            BufferedReader(FileReader(file)).use { bufferedReader ->
                var line: String? = bufferedReader.readLine()
                while (line != null) {
                    stringBuilder.append(line)
                    line = bufferedReader.readLine()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading JSON file: ${e.message}")
        }
        return stringBuilder.toString()
    }
    private fun extractAndClassify(jsonObject: JSONObject, parentKey: String = ""): List<Triple<String, Any, String>> {
        val classifications = mutableListOf<Triple<String, Any, String>>()

        for (key in jsonObject.keys()) {
            val value = jsonObject.get(key)
            val fullKey = if (parentKey.isNotEmpty()) "$parentKey - $key" else key
            val category = classifyValue(key, value)

            if (key == "MODE") {
                classifications.add(Triple(fullKey, value, "MODE"))
            } else {
                classifications.add(Triple(fullKey, value, category))
            }

            if (value is JSONObject) {
                classifications.addAll(extractAndClassify(value, fullKey))
            }
        }

        return classifications
    }
    private fun processJsonObject(jsonObject: JSONObject) {
        for (key in jsonObject.keys()) {
            if (validEndKeywords.contains(key)) {
                when (val value = jsonObject.get(key)) {
                    is JSONObject -> processSubJsonObject(key, value)
                    is JSONArray -> Log.d("MainActivity2", "$key: Array detected. Value: $value")
                    else -> Log.d("MainActivity2", "$key: Value - $value, Category: ${classifyValue(key, value)}")
                }
            }
        }
    }
    private fun processSubJsonObject(parentKey: String, jsonObject: JSONObject) {
        for (key in jsonObject.keys()) {
            when (val value = jsonObject.get(key)) {
                is JSONObject -> processSubJsonObject("$parentKey - $key", value)
                is JSONArray -> Log.d("MainActivity2", "$parentKey - $key Value: $value Category: ${classifyValue(key, value)}")
                else -> Log.d(
                    "MainActivity2",
                    "$parentKey - $key: Value - $value, Category: ${classifyValue(key, value)}"
                )
            }
        }
    }
    private fun classifyValue(key: String, value: Any): String {
        return when {
            key in listOf("NAME", "DOMAIN", "INSTALLATION", "WARRANTY", "SW_VERSION", "HW_VERSION", "MODE") -> "String"
            key == "RANGE" && value is JSONArray -> {
                val rangeCategory = rangeCategories.firstOrNull { it.size == value.length() }
                rangeCategory?.let {
                    "RANGE Category: ${it.joinToString()}"
                } ?: "Invalid RANGE Category"
            }
            key == "OPTION" && value is JSONArray -> {
                if (isArrayOfStrings(value)) {
                    "Option valid ${value.length()} strings"
                } else {
                    "Invalid OPTION (Non-string values)"
                }
            }
            key == "BOOLEAN" && value is JSONArray -> {
                if (value.length() == 2 && isArrayOfStrings(value)) {
                    "[String, String] - BOOLEAN"
                } else {
                    "Invalid BOOLEAN (Expected 2 values)"
                }
            }
            else -> "Unknown Category"
        }
    }
    private fun isArrayOfStrings(array: JSONArray): Boolean {
        for (i in 0 until array.length()) {
            if (array.get(i) !is String) {
                return false
            }
        }
        return true
    }
    private fun addUIElements(jsonObject: JSONObject) {
        val inflater = LayoutInflater.from(this)
        val containerLayout = findViewById<LinearLayout>(R.id.main2)

        val classifications = extractAndClassify(jsonObject)
        val layoutsWithWeights = mutableListOf<Pair<View, Int>>()

        // Categories to handle dynamically
        val categoryOrder = mutableListOf<String>()

        // First pass to find categories and add them to the dynamic order list
        for ((key, value, category) in classifications) {
            if (!categoryOrder.contains(category)) {
                categoryOrder.add(category) // Only add unique categories
            }
        }
        // Iterate over categories in dynamic order
        for (category in categoryOrder) {
            // Debugging: Log each category being processed
            Log.d("MainActivity2", "Processing category: $category")

            when {
                // Handle 'MODE' category dynamically
                category == "MODE" -> {
                    val modeObject = jsonObject.optJSONObject("MODE")
                    if (modeObject != null) {
                        val modeKeys = modeObject.keys().asSequence().toList()
                        val inflatedView = inflater.inflate(R.layout.xml6, containerLayout, false)
                        val modeSpinner = inflatedView.findViewById<Spinner>(R.id.mode_spinner)

                        modeSpinner.adapter = ArrayAdapter(
                            this,
                            android.R.layout.simple_spinner_item,
                            modeKeys
                        ).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(
                                parent: AdapterView<*>?,
                                view: View?,
                                position: Int,
                                id: Long
                            ) {
                                val selectedMode = modeKeys[position]
                                sendToRpi("Mode:$selectedMode")
                            }
                            override fun onNothingSelected(parent: AdapterView<*>?) {}
                        }
                        layoutsWithWeights.add(Pair(inflatedView, 15))
                    }
                }
                // Handle RANGE Category dynamically
                category.startsWith("RANGE") -> {
                    for ((key, value, category) in classifications) {
                        if (category.startsWith("RANGE Category: MIN, MAX, STEP, OPERATION, UNIT VAL")) {
                            val inflatedView = inflater.inflate(R.layout.xml1, containerLayout, false)
                            if (value is JSONArray) {
                                configureRangeElements(inflatedView, value)
                            }
                            layoutsWithWeights.add(Pair(inflatedView, 15))
                        }
                    }
                }
                // Handle Option valid category dynamically
                category.startsWith("Option") -> {
                    for ((key, value, category) in classifications) {
                        val match = Regex("Option valid (\\d+) strings").find(category)
                        val optionCount = match?.groupValues?.get(1)?.toIntOrNull()
                        if (optionCount != null && value is JSONArray && value.length() == optionCount) {
                            val inflatedView = inflater.inflate(R.layout.xml2, containerLayout, false)
                            layoutsWithWeights.add(Pair(inflatedView, 15))
                        } else {
                            Log.e("MainActivity2", "Mismatch in OPTION count or unexpected type for: $value")
                        }
                    }
                }
                // Handle [String, String] - BOOLEAN category dynamically
                category == "[String, String] - BOOLEAN" -> {
                    for ((key, value, category) in classifications) {
                        if (category == "[String, String] - BOOLEAN") {
                            val fullKey = key // Use the full concatenated key, or reconstruct parentKey if needed.
                            val layoutRes = if (fullKey.contains("Power", ignoreCase = true)) R.layout.xml3 else R.layout.xml5
                            val inflatedView = inflater.inflate(layoutRes, containerLayout, false)
//                            val text1 = inflatedView.findViewById<TextView>(R.id.txt_xml5)
//                            text1.text = ""
//                            val text2 = inflatedView.findViewById<TextView>(R.id.txt_xml3)
//                            text2.text = ""
                            layoutsWithWeights.add(Pair(inflatedView, 15))
                        }
                    }
                }
            }
        }
        // Adding a blank layout at the end
        val blankLayout = inflater.inflate(R.layout.xml4, containerLayout, false)
        layoutsWithWeights.add(Pair(blankLayout, 40))

        // Now add all layouts with their weights in the correct order
        for ((view, weight) in layoutsWithWeights) {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                weight.toFloat()
            )
            view.layoutParams = params
            containerLayout.addView(view)
        }
        // Call populateSpinnerOptions to update any spinner-related UI elements
        populateSpinnerOptions(containerLayout, classifications)
    }
    private fun populateSpinnerOptions(container: LinearLayout, classifications: List<Triple<String, Any, String>>) {
        for ((key, value, category) in classifications) {
            if (category.startsWith("Option valid") && value is JSONArray) {
                val spinnerOptions = mutableListOf<String>()

                for (i in 0 until value.length()) {
                    spinnerOptions.add(value.getString(i))
                }

                val spinner = container.findViewById<Spinner>(R.id.fan_spinner)
                if (spinner != null) {
                    val adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_spinner_item,
                        spinnerOptions
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinner.adapter = adapter
                }
            }
        }
    }
    private fun configureRangeElements(view: View, rangeArray: JSONArray) {
        val min = rangeArray.getDouble(0)
        val max = rangeArray.getDouble(1)
        val step = rangeArray.getDouble(2)
        val unit = rangeArray.optString(4, "")

        val txtDisplay = view.findViewById<TextView>(R.id.txtDisplay)
        val seekBar = view.findViewById<SeekBar>(R.id.temperature_seekbar)
        val btnDecrease = view.findViewById<Button>(R.id.temperature_decrease)
        val btnIncrease = view.findViewById<Button>(R.id.temperature_increase)
        // Ensure elements are not null
        if (txtDisplay == null || seekBar == null || btnDecrease == null || btnIncrease == null) {
            Log.e("MainActivity2", "UI elements missing in layout")
            return
        }
        seekBar.max = ((max - min) / step).toInt()
        var currentTemperature = min
        txtDisplay.text = "$currentTemperature $unit"
        btnDecrease.setOnClickListener {
            if (currentTemperature - step >= min) {
                currentTemperature -= step
                txtDisplay.text = "$currentTemperature $unit"
                seekBar.progress = ((currentTemperature - min) / step).toInt()
                val string = currentTemperature.toString()
                sendToRpi(string)

            }
        }
        btnIncrease.setOnClickListener {
            if (currentTemperature + step <= max) {
                currentTemperature += step
                txtDisplay.text = "$currentTemperature $unit"
                seekBar.progress = ((currentTemperature - min) / step).toInt()
                val string = currentTemperature.toString()
                sendToRpi(string)
            }
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentTemperature = min + (progress * step)
                txtDisplay.text = "$currentTemperature $unit"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }
    private fun sendToRpi(data: String) {
        val rpiPort = 8004
        Thread {
            var attempt = 0
            var success = false

            // Call Python to generate checksum
            val checksum = generateChecksum(data)

            val androidIp = getLocalIpAddress() // Function to retrieve device IP
            val messageWithChecksum = "$data,$androidIp:$checksum"

            // Encrypt the message before sending
            val encryptedMessage = encryptMessageWithPython(messageWithChecksum)
            Log.d("encryption","$encryptedMessage")

            if (encryptedMessage == null) {
                Log.e(TAG, "Encryption failed. Not sending data.")
                return@Thread
            }

            while (attempt < MAX_RETRIES && !success) {
                try {
                    val socket = Socket().apply {
                        soTimeout = 5000
                    }
                    socket.connect(java.net.InetSocketAddress(rpiIp, rpiPort), 5000)
                    val outputStream: OutputStream = socket.getOutputStream()
                    outputStream.write("Invoke:$encryptedMessage".toByteArray(Charsets.UTF_8))
                    outputStream.flush()

                    val inputStream = socket.getInputStream()
                    val responseBytes = ByteArray(1024)
                    val bytesRead = inputStream.read(responseBytes)
                    val receivedMessage = if (bytesRead > 0) String(responseBytes, 0, bytesRead, Charsets.UTF_8) else ""
                    socket.close()
                    success = true
                    Log.i(TAG, "Data sent successfully (encrypted): $encryptedMessage")
                    Log.i(TAG, "Received acknowledgment: $receivedMessage")

                    // Decrypt the acknowledgment before verification
                    val decryptedAck = decryptMessageWithPython(receivedMessage, )

                    if (decryptedAck != null) {
                        verifyChecksum(decryptedAck)
                    } else {
                        Log.e(TAG, "Decryption of acknowledgment failed")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error sending data to RPi: ${e.message}")
                }
                attempt++
                if (!success) {
                    Thread.sleep(RETRY_DELAY_MS)
                }
            }
            if (!success) {
                Log.e(TAG, "Failed to send data after $MAX_RETRIES attempts")
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
    private fun verifyReceivedChecksum(message: String, providedChecksum: String): Boolean {
        try {
            val python = Python.getInstance()
            val pyResult = python.getModule("hashverify").callAttr("verify_checksum", "$message:$providedChecksum")
            return pyResult.toString().toBoolean()  // Return true/false based on the Python verification result
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Python to verify checksum: ${e.message}")
            return false
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
    private fun verifyChecksum(receivedMessage: String) {
        val messageWithChecksum = receivedMessage.split(":")
        if (messageWithChecksum.size != 2) {
            Log.e(TAG, "Invalid message format: Missing checksum delimiter ':'")
            return
        }

        val messagePart = messageWithChecksum[0]
        val providedChecksum = messageWithChecksum[1]

        // Ensure the message is enclosed in quotes
        if (!messagePart.startsWith("\"") || !messagePart.endsWith("\"")) {
            Log.e(TAG, "Invalid message format: Expected quotes around message")
            return
        }

        // Extract the actual message by removing surrounding quotes
        val cleanedMessage = messagePart.substring(1, messagePart.length - 1)
        val messageParts = cleanedMessage.split(",")

        if (messageParts.size != 2) {
            Log.e(TAG, "Invalid message format: Expected format 'message,sender_ip'")
            return
        }

        val message = messageParts[0]
        val senderIp = messageParts[1]

        // Verify the checksum
        val isValid = verifyReceivedChecksum("$message,$senderIp", providedChecksum)
        if (isValid) {
            Log.d(TAG, "Checksum verification successful. Message integrity confirmed.")
        } else {
            Log.e(TAG, "Checksum verification failed! Possible data corruption or tampering.")
        }
    }
    private fun encryptMessageWithPython(plainText: String): String? {
        try {
            // Define path to the public key (same as where private key is)
            val publicKeyPath = "/storage/sdcard0/Android/data/com.example.iot1/files/Documents/thingPublicKey.txt"

            // Read the public key from the file
            val publicKey = File(publicKeyPath).readText()

            // Use Chaquopy to call Python for encryption
            val python = Python.getInstance()
            val pyResult = python.getModule("encrypt").callAttr("encrypt_message", publicKey, plainText)

            return pyResult.toString()  // Return the encrypted message
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed: ${e.message}")
            return null
        }
    }

    private fun decryptMessageWithPython(encryptedMessage: String): String? {
        try {
            // Define path to the private key (same as where the public key is)
            val privateKeyPath = "/storage/emulated/0/Android/data/com.example.iot1/files/Documents/privateKey.txt"

            // Read the private key from the file
            val privateKey = File(privateKeyPath).readText()

            // Use Chaquopy to call Python for decryption
            val python = Python.getInstance()
            val pyResult = python.getModule("decrypt").callAttr("decrypt_message", privateKey, encryptedMessage)

            return pyResult.toString()  // Return the decrypted message
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed: ${e.message}")
            return null
        }
    }
}
