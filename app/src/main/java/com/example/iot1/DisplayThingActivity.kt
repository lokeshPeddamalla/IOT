package com.example.iot1

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.net.ConnectivityManager
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
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

class DisplayThingActivity : AppCompatActivity() {

    private lateinit var rpiIp: String
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 1000L
    private var socket: Socket? = null
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothSocket: BluetoothSocket
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private val deviceAddress = "B8:27:EB:2B:90:22"
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34DB")
    private var isConnected = false

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

        if (isNetworkAvailable()){
            connectionToThing(rpiIp)
        }else{
            connectToDevice()
        }
        Log.d("lokesh", rpiIp)
        val json = loadJSONFromFile("/storage/emulated/0/Android/data/com.example.iot1/files/manifest.json")
        if (json.isEmpty()) {
            Log.d("MainActivity2", "JSON is null or empty")
            return
        }
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))  // Initialize Python with the context
        }

        val gson = Gson()
        val jsonObject = gson.fromJson(json, JsonObject::class.java)
        if (jsonObject == null) {
            Log.d("MainActivity2", "Failed to parse JSON into JsonObject")
            return
        }

        processJsonObject(JSONObject(jsonObject.toString()))
        addUIElements(JSONObject(jsonObject.toString()))
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice() {
        val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress)
        Log.d("Lokesh", "Device unable to connect $device")
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            Log.d("Lokesh", "Device unable to connect $bluetoothSocket")
            bluetoothSocket.connect()
            outputStream = bluetoothSocket.outputStream
            inputStream = bluetoothSocket.inputStream
            isConnected = true
        } catch (e: IOException) {
            e.printStackTrace()
            isConnected = false
        }
    }
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.activeNetworkInfo?.isConnected == true
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
            Log.d("Lokesh", "Error reading JSON file: ${e.message}")
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
                                sendToRpi("$selectedMode")
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
                            Log.d("MainActivity2", "Mismatch in OPTION count or unexpected type for: $value")
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
            Log.d("MainActivity2", "UI elements missing in layout")
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
    private fun connectionToThing(ipAddr: String) {
        val rpiPort = 12346
        Thread {
            try {
                if (socket == null || socket!!.isClosed) {
                    socket = Socket()
                    socket!!.connect(InetSocketAddress(ipAddr, rpiPort), 5000)
                    outputStream = socket!!.getOutputStream()
                    inputStream = socket!!.getInputStream()
                    Log.d("Connection", "Connected to Raspberry Pi at $ipAddr:$rpiPort")
                }
            } catch (e: Exception) {
                Log.d("Connection", "Failed to connect: ${e.message}")
            }
        }.start()
    }

    private fun sendToRpi(data: String) {
        Thread {
            var success = false
            val timeoutMillis = 15000
            val startTime = System.currentTimeMillis()
            val androidIp = getLocalIpAddress()
            Log.d("Lokesh", androidIp)
            val DataAndIp = "Data:$data+IP:$androidIp"
            val message = generateChecksum(DataAndIp)
            val DataAndIpAndChecksum = "$DataAndIp,$message"
            val encryptedMessage = encryptMessageWithPython(DataAndIpAndChecksum)

            try {
                if (socket == null || socket!!.isClosed) {
                    Log.d("Connection", "No active connection. Call connectionToThing() first.")
                    //connectToBluetooth()
                    sendViaBluetooth(data)
                } else {
                    outputStream?.write(encryptedMessage?.toByteArray(Charsets.UTF_8))
                    Log.d("Lokesh","sent message: $encryptedMessage")
                    outputStream?.flush()

                    val responseBytes = ByteArray(1024)
                    socket?.soTimeout = 5000  // Set timeout for acknowledgment
                    val bytesRead = try {
                        inputStream?.read(responseBytes) ?: -1
                    } catch (e: SocketTimeoutException) {
                        -1 // Timeout occurred
                    }

                    val receivedMessage = if (bytesRead > 0)
                        String(responseBytes, 0, bytesRead, Charsets.UTF_8)
                    else
                        ""
                    if (receivedMessage.isNotEmpty()) {
                        Log.d("ACKDecryption", "Received acknowledgment via Wi-Fi: $receivedMessage")
                        val decryptedMessage =  decryptMessageWithPython(receivedMessage)
                        Log.d("ACKDecryption","$decryptedMessage")
                        val parts = decryptedMessage!!.split("+")
                        if (parts.size ==2){
                            val message = parts[0]
                            Log.d("ACKDecryption","message is: $message")
                            val ipAndChecksum = parts[1]
                            val ipAndChecksumSplit = ipAndChecksum.split(",")
                            if (ipAndChecksumSplit.size == 2){
                                val ip = ipAndChecksumSplit[0]
                                val checksum = ipAndChecksumSplit[1]
                                Log.d("ACKDecryption","ip is: $ip")
                                Log.d("ACKDecryption","checksum is: $checksum")

                                val generateChecksum = generateChecksum("$message+$ip")
                                if (checksum == generateChecksum){
                                    Log.d("ACKDecryption","Checksum matched")
                                }
                            }
                        }
                        success = true
                    }
                }
                // Check if 15 seconds passed without success
                if (!success && (System.currentTimeMillis() - startTime) >= timeoutMillis) {
                    Log.d("Connection", "No acknowledgment received for 15 seconds. Switching to Bluetooth...")
                }
            } catch (e: Exception) {
                Log.d("Lokesh", "Error sending data: ${e.message}")
            }
        }.start()
    }
    private fun sendViaBluetooth(temperature: String) {
        Thread {
            var attempt = 0
            var success = false

            // Call Python to generate checksum
            val checksum = generateChecksum(temperature)

            val androidIp = getLocalIpAddress() // Function to retrieve device IP
            val messageWithChecksum = "\"$temperature,$androidIp\":$checksum"

            // Encrypt the message before sending
            val encryptedMessage = encryptMessageWithPython(messageWithChecksum)

            if (encryptedMessage == null) {
                Log.d("Lokesh", "Encryption failed. Not sending data via Bluetooth.")
                return@Thread
            }

            while (attempt < MAX_RETRIES && !success) {
                try {
                    outputStream?.write("$encryptedMessage".toByteArray(Charsets.UTF_8))
                    outputStream?.flush()

                    // Assuming we receive the acknowledgment message from Bluetooth
                    val responseBytes = ByteArray(1024)
                    val bytesRead = inputStream?.read(responseBytes) ?: -1 // Safely read from inputStream

                    if (bytesRead > 0) {
                        val receivedMessage = String(responseBytes, 0, bytesRead, Charsets.UTF_8)

                        // Decrypt the acknowledgment before verification
                        val decryptedAck = decryptMessageWithPython(receivedMessage)

                        if (decryptedAck != null) {
                            verifyChecksum(decryptedAck)
                        } else {
                            Log.d("Lokesh", "Decryption of acknowledgment failed via Bluetooth.")
                        }

                        success = true
                        Log.i("Lokesh", "Data sent successfully via Bluetooth (encrypted): $encryptedMessage")
                        Log.i("Lokesh", "Received acknowledgment via Bluetooth: $receivedMessage")
                    } else {
                        Log.d("Lokesh", "No response received from Bluetooth.")
                    }
                } catch (e: Exception) {
                    Log.d("Lokesh", "Error sending data via Bluetooth: ${e.message}")
                }

                attempt++
                if (!success) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS)
                    } catch (e: InterruptedException) {
                        Log.d("Lokesh", "Retry delay interrupted: ${e.message}")
                    }
                }
            }

            if (!success) {
                Log.d("Lokesh", "Failed to send data after $MAX_RETRIES attempts via Bluetooth")
            }
        }.start()
    }
    private fun generateChecksum(data: String): String {
        try {
            val python = Python.getInstance()
            val pyResult = python.getModule("hash").callAttr("generate_md5", data)
            return pyResult.toString()  // This is the checksum returned by the Python function
        } catch (e: Exception) {
            Log.d("Lokesh", "Error calling Python to generate checksum: ${e.message}")
            return ""
        }
    }
    private fun verifyReceivedChecksum(message: String, providedChecksum: String): Boolean {
        try {
            val python = Python.getInstance()
            val pyResult = python.getModule("hashverify").callAttr("verify_checksum", "$message:$providedChecksum")
            return pyResult.toString().toBoolean()  // Return true/false based on the Python verification result
        } catch (e: Exception) {
            Log.d("Lokesh", "Error calling Python to verify checksum: ${e.message}")
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
            Log.d("Lokesh", "Error retrieving local IP: ${e.message}")
        }
        return "0.0.0.0"
    }
    private fun verifyChecksum(receivedMessage: String) {
        val messageWithChecksum = receivedMessage.split(":")
        if (messageWithChecksum.size != 2) {
            Log.d("Lokesh", "Invalid message format: Missing checksum delimiter ':'")
            return
        }

        val messagePart = messageWithChecksum[0]
        val providedChecksum = messageWithChecksum[1]

        // Ensure the message is enclosed in quotes
        if (!messagePart.startsWith("\"") || !messagePart.endsWith("\"")) {
            Log.d("Lokesh", "Invalid message format: Expected quotes around message")
            return
        }

        // Extract the actual message by removing surrounding quotes
        val cleanedMessage = messagePart.substring(1, messagePart.length - 1)
        val messageParts = cleanedMessage.split(",")

        if (messageParts.size != 2) {
            Log.d("Lokesh", "Invalid message format: Expected format 'message,sender_ip'")
            return
        }

        val message = messageParts[0]
        val senderIp = messageParts[1]

        // Verify the checksum
        val isValid = verifyReceivedChecksum("$message,$senderIp", providedChecksum)
        if (isValid) {
           Log.d("Lokesh", "Checksum verification successful. Message integrity confirmed.")
        } else {
           Log.d("Lokesh", "Checksum verification failed! Possible data corruption or tampering.")
        }
    }
    private fun encryptMessageWithPython(plainText: String): String? {
        try {
            // Define path to the public key (same as where private key is)
            val publicKeyPath = "/storage/emulated/0/Android/data/com.example.iot1/files/thingPublicKey.txt"
            Log.d("Lokesh", "Public key found")

            // Read the public key from the file
            val publicKey = File(publicKeyPath).readText()
            Log.d("Lokesh", "public key read: $publicKey")

            // Use Chaquopy to call Python for encryption
            val python = Python.getInstance()
            val pyResult = python.getModule("encrypt").callAttr("encrypt_message", publicKey, plainText)
            Log.d("Lokesh", "imported module")
            return pyResult.toString()  // Return the encrypted message
        } catch (e: Exception) {
            Log.d("Lokesh", "Encryption failed: ${e.message}")
            return null
        }
    }

    private fun decryptMessageWithPython(encryptedMessage: String): String? {
        try {
            // Define path to the private key (same as where the public key is)
            val privateKeyPath = "/storage/emulated/0/Android/data/com.example.iot1/files/privateKey.txt"

            // Read the private key from the file
            val privateKey = File(privateKeyPath).readText()

            // Use Chaquopy to call Python for decryption
            val python = Python.getInstance()
            val pyResult = python.getModule("decrypt").callAttr("decrypt_message", privateKey, encryptedMessage)

            return pyResult.toString()  // Return the decrypted message
        } catch (e: Exception) {
            Log.d("Lokesh", "Decryption failed: ${e.message}")
            return null
        }
    }
}
