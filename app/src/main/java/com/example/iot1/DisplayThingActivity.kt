package com.example.iot1

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import androidx.databinding.DataBindingUtil.setContentView
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
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34DB")
    private var isConnected = false
    private var deviceAddress: String = "B8:27:EB:D3:65:ED" // To store the dynamically retrieved MAC address


    private val validEndKeywords = listOf(
        "S", "START", "OPTIONAL", "SENSOR", "ACTUATOR", "DEVICE", "OEM", "MODE", "LOCATION", "D", "NAME", "DOMAIN",
        "WARRANTY", "INSTALLATION", "SW_VERSION", "HW_VERSION", "O", "SOCKET", "M", "NAME_VAL", "Z", "DATA",
        "ACTUATOR_INFO", "VALUE", "UNIT_VAL", "AREA", "X", "DESC", "SENSOR_TYPE", "STRUCT_X", "BATTERY", "GRAPH",
        "DATA_TYPE", "PIE", "BAR", "GRAPH_TYPE", "CORD1", "CORD2", "BOOLEAN", "NUMERIC", "STRING", "IMAGE", "AUDIO",
        "VIDEO", "MAP", "COLOR", "DATE", "TIME", "RANGE", "TUPLE", "UNIT", "R", "RVAL", "MIN", "MAX", "STEP",
        "UNIT_VAL", "OPERATION", "EXCEPT", "TVAL", "OPTION", "OVAL", "SIZE", "IMAGE_ATTRIB", "LENGTH", "WIDTH", "SRC",
        "IMAGE_TYPE", "PLAYER_TYPE", "PLAYER_ATTRIB", "LOOP", "MUTE", "ACTUATOR", "Y", "ACTUATOR_TYPE", "STRUCT_Y"
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

        Log.d("BluetoothConnectionIoT", rpiIp)
        val json = loadJSONFromFile("/storage/emulated/0/Android/data/com.example.iot1/files/manifest.json")
        if (json.isEmpty()) {
            Log.d("MainActivity2", "JSON is null or empty")
            return
        }
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))  // Initialize Python with the context
        }
        connectionToThing(rpiIp)

        val gson = Gson()
        val jsonObject = gson.fromJson(json, JsonObject::class.java)
        if (jsonObject == null) {
            Log.d("MainActivity2", "Failed to parse JSON into JsonObject")
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
            Log.d("BluetoothConnectionIoT", "Error reading JSON file: ${e.message}")
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
                        layoutsWithWeights.add(Pair(inflatedView, 18))
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
                            layoutsWithWeights.add(Pair(inflatedView, 18))
                        }
                    }
                }
                // Handle Option valid category dynamically
                // Handle Option valid category dynamically
                category.startsWith("Option") -> {
                    classifications.filter { it.third.startsWith("Option valid") }.forEach { (key, value, cat) ->
                        val match = Regex("Option valid (\\d+) strings").find(cat)
                        val optionCount = match?.groupValues?.get(1)?.toIntOrNull()
                        if (optionCount != null && value is JSONArray && value.length() == optionCount) {
                            val inflatedView = inflater.inflate(R.layout.xml2, containerLayout, false)
                            // Optional: Customize each inflated view using key/value if needed
                            layoutsWithWeights.add(Pair(inflatedView, 18))
                        } else {
                            Log.d("MainActivity2", "Mismatch in OPTION count or unexpected type for key $key: $value")
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
                            layoutsWithWeights.add(Pair(inflatedView, 18))
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
                    Log.d("BluetoothConnectionIoT", "Connected to Raspberry Pi at $ipAddr:$rpiPort")
                }
            } catch (e: Exception) {
                Log.d("BluetoothConnectionIoT", "Failed to connect: ${e.message}")
            }
        }.start()
    }

    //    private fun sendToRpi(data: String) {
    //        Thread {
    //            var success = false
    //            val timeoutMillis = 15000
    //            val startTime = System.currentTimeMillis()
    //            val androidIp = getLocalIpAddress()
    //            Log.d("BluetoothConnectionIoT", androidIp)
    //            val DataAndIp = "Data:$data+IP:$androidIp"
    //            val message = generateChecksum(DataAndIp)
    //            val DataAndIpAndChecksum = "$DataAndIp,$message"
    //            val encryptedMessage = encryptMessageWithPython(DataAndIpAndChecksum)
    //
    //            try {
    //                if (socket == null || socket!!.isClosed) {
    //                    Log.d("Connection", "No active connection. Call connectionToThing() first.")
    //                    setupBluetooth() // Ensure Bluetooth setup is complete
    //                    Log.d("BluetoothConnectionIoT", "setup done")
    //                    checkBluetoothPermissions() // Check for Bluetooth permissions
    //                    Log.d("BluetoothConnectionIoT", "bluetooth permissions checked")
    //                    registerBluetoothReceiver() // Register the Bluetooth receiver
    //                    Log.d("BluetoothConnectionIoT", "registered")
    //                    sendViaBluetooth(data) // Send data via Bluetooth
    //                } else {
    //                    try {
    //                        outputStream?.write(encryptedMessage?.toByteArray(Charsets.UTF_8))
    //                        Log.d("BluetoothConnectionIoT", "sent message: $encryptedMessage")
    //                        outputStream?.flush()
    //
    //                        val responseBytes = ByteArray(1024)
    //                        socket?.soTimeout = 5000 // Set timeout for acknowledgment
    //                        val bytesRead = try {
    //                            inputStream?.read(responseBytes) ?: -1
    //                        } catch (e: SocketTimeoutException) {
    //                            -1 // Timeout occurred
    //                        }
    //
    //                        val receivedMessage = if (bytesRead > 0)
    //                            String(responseBytes, 0, bytesRead, Charsets.UTF_8)
    //                        else
    //                            ""
    //
    //                        if (receivedMessage.isNotEmpty()) {
    //                            Log.d("BluetoothConnectionIoT", "Received acknowledgment via Wi-Fi: $receivedMessage")
    //                            val decryptedMessage = decryptMessageWithPython(receivedMessage)
    //                            Log.d("BluetoothConnectionIoT", "$decryptedMessage")
    //                            val parts = decryptedMessage!!.split("+")
    //                            if (parts.size == 2) {
    //                                val message = parts[0]
    //                                Log.d("BluetoothConnectionIoT", "message is: $message")
    //                                val ipAndChecksum = parts[1]
    //                                val ipAndChecksumSplit = ipAndChecksum.split(",")
    //                                if (ipAndChecksumSplit.size == 2) {
    //                                    val ip = ipAndChecksumSplit[0]
    //                                    val checksum = ipAndChecksumSplit[1]
    //                                    Log.d("BluetoothConnectionIoT", "ip is: $ip")
    //                                    Log.d("BluetoothConnectionIoT", "checksum is: $checksum")
    //
    //                                    val generateChecksum = generateChecksum("$message+$ip")
    //                                    if (checksum == generateChecksum) {
    //                                        Log.d("BluetoothConnectionIoT", "Checksum matched")
    //                                    }
    //                                }
    //                            }
    //                            success = true
    //                        }
    //                    } catch (e: Exception) {
    //                        Log.d("BluetoothConnectionIoT", "Error during Wi-Fi communication: ${e.message}")
    //                    }
    //
    //                    // If Wi-Fi communication fails or no acknowledgment is received
    //                    if (!success && (System.currentTimeMillis() - startTime) >= timeoutMillis) {
    //                        Log.d("Connection", "No acknowledgment received for 15 seconds. Switching to Bluetooth...")
    //                        try {
    //                            socket?.close() // Close the current socket
    //                        } catch (e: Exception) {
    //                            Log.d("BluetoothConnectionIoT", "Error closing Wi-Fi socket: ${e.message}")
    //                        }
    //
    //                        // Switch to Bluetooth communication
    //                        try {
    //                            setupBluetooth()
    //                            Log.d("BluetoothConnectionIoT", "Bluetooth setup complete")
    //                            checkBluetoothPermissions()
    //                            Log.d("BluetoothConnectionIoT", "Bluetooth permissions checked")
    //                            registerBluetoothReceiver()
    //                            Log.d("BluetoothConnectionIoT", "Bluetooth receiver registered")
    //                            sendViaBluetooth(data)
    //                        } catch (e: Exception) {
    //                            Log.d("BluetoothConnectionIoT", "Error during Bluetooth communication setup: ${e.message}")
    //                        }
    //                    }
    //                }
    //            } catch (e: Exception) {
    //                Log.d("BluetoothConnectionIoT", "Error sending data: ${e.message}")
    //            }
    //        }.start()
    //    }
    private fun sendToRpi(data: String) {
        Thread {
            val maxWifiRetries = 3
            val timeoutMillis = 5000
            val androidIp = getLocalIpAddress()
            val dataAndIp = "Data:$data+IP:$androidIp"
            val checksum = generateChecksum(dataAndIp)
            val dataWithChecksum = "$dataAndIp,$checksum"
            val encryptedMessage = encryptMessageWithPython(dataWithChecksum)

            if (encryptedMessage == null) {
                Log.d("BluetoothConnectionIoT", "Encryption failed. Aborting.")
                return@Thread
            }

            var wifiSuccess = false
            for (attempt in 1..maxWifiRetries) {
                try {
                    socket?.soTimeout = timeoutMillis
                    outputStream?.write(encryptedMessage.toByteArray(Charsets.UTF_8))
                    outputStream?.flush()
                    Log.d("BluetoothConnectionIoT", "Wi-Fi attempt $attempt: Sent encrypted message.")

                    val responseBytes = ByteArray(1024)
                    val bytesRead = inputStream?.read(responseBytes) ?: -1

                    if (bytesRead > 0) {
                        val response = String(responseBytes, 0, bytesRead, Charsets.UTF_8)
                        val decrypted = decryptMessageWithPython(response)
                        if (decrypted != null && handleAcknowledgment(decrypted)) {
                            Log.d("BluetoothConnectionIoT", "Wi-Fi acknowledgment validated on attempt $attempt.")
                            wifiSuccess = true
                            break
                        }
                    } else {
                        Log.d("BluetoothConnectionIoT", "Wi-Fi attempt $attempt: No response.")
                    }
                } catch (e: Exception) {
                    Log.d("BluetoothConnectionIoT", "Wi-Fi attempt $attempt failed: ${e.message}")
                }

                // Delay before retry
                Thread.sleep(1000)
            }

            if (!wifiSuccess) {
                Log.d("BluetoothConnectionIoT", "Wi-Fi failed after 3 attempts. Switching to Bluetooth.")

                try {
                    socket?.close()
                } catch (e: IOException) {
                    Log.e("BluetoothConnectionIoT", "Error closing Wi-Fi socket: ${e.message}")
                }


                setupBluetooth()
                checkBluetoothPermissions()

                connectToBluetoothDevice()
                if (isConnected) {
                    sendViaBluetooth(data)
                } else {
                    Log.e("BluetoothConnectionIoT", "Bluetooth connection failed. Aborting.")
                }
            }

        }.start()
    }

    private fun handleAcknowledgment(decryptedMessage: String?): Boolean {
        if (decryptedMessage != null) {
            val parts = decryptedMessage.split("+")
            if (parts.size == 2) {
                val message = parts[0]
                Log.d("BluetoothConnectionIoT", "Message: $message")
                val ipAndChecksum = parts[1]
                val ipAndChecksumSplit = ipAndChecksum.split(",")
                if (ipAndChecksumSplit.size == 2) { setupBluetooth()
//                        Log.d("BluetoothConnectionIoT", "Bluetooth setup done.")
//                        checkBluetoothPermissions()
//                        Log.d("BluetoothConnectionIoT", "Bluetooth permissions checked.")
//                        registerBluetoothReceiver()
//                        Log.d("BluetoothConnectionIoT", "Bluetooth receiver registered.")
//                        sendViaBluetooth(data)
                    val ip = ipAndChecksumSplit[0]
                    val checksum = ipAndChecksumSplit[1]
                    Log.d("BluetoothConnectionIoT", "IP: $ip")
                    Log.d("BluetoothConnectionIoT", "Checksum: $checksum")

                    val generatedChecksum = generateChecksum("$message+$ip")
                    if (checksum == generatedChecksum) {
                        Log.d("BluetoothConnectionIoT", "Checksum matched.")
                        return true
                    } else {
                        Log.d("BluetoothConnectionIoT", "Checksum mismatch.")
                    }
                }
            }
        }
        return false
    }
    private fun sendViaBluetooth(data: String) {
        Thread {
            if (!::bluetoothSocket.isInitialized || !bluetoothSocket.isConnected || outputStream == null || inputStream == null) {
                Log.d("BluetoothConnectionIoT", "Bluetooth socket or streams not ready. Aborting send.")
                return@Thread
            }

            val checksum = generateChecksum(data)
            val androidIp = getLocalIpAddress()
            val messageWithChecksum = "\"$data,$androidIp\":$checksum"
            val encryptedMessage = encryptMessageWithPython(messageWithChecksum)

            if (encryptedMessage == null) {
                Log.d("BluetoothConnectionIoT", "Encryption failed. Not sending.")
                return@Thread
            }

            Thread.sleep(1000) // Allow Pi server to be ready

            try {
                outputStream?.write(encryptedMessage.toByteArray(Charsets.UTF_8))
                outputStream?.flush()
                Log.d("BluetoothConnectionIoT", "Sent encrypted message via Bluetooth.")

                val responseBytes = ByteArray(1024)
                val bytesRead = inputStream?.read(responseBytes) ?: -1

                if (bytesRead > 0) {
                    val receivedMessage = String(responseBytes, 0, bytesRead, Charsets.UTF_8)
                    val decryptedAck = decryptMessageWithPython(receivedMessage)
                    if (decryptedAck != null) {
                        verifyChecksum(decryptedAck)
                        Log.i("BluetoothConnectionIoT", "Acknowledgment received and verified.")
                    } else {
                        Log.e("BluetoothConnectionIoT", "Decryption of acknowledgment failed.")
                    }
                } else {
                    Log.e("BluetoothConnectionIoT", "No response received from Bluetooth device.")
                }

            } catch (e: Exception) {
                Log.e("BluetoothConnectionIoT", "Error sending via Bluetooth: ${e.message}")
            }
        }.start()
    }

    @SuppressLint("MissingPermission")
    private fun setupBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        // Check if Bluetooth is enabled
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_SHORT).show()
            Log.d("BluetoothConnectionIoT","bluetooth is enabled")
            finish()
        }
    }
    @SuppressLint("InlinedApi")
    private fun checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Only needed on Android 12 (API level 31) and above
            // Check if BLUETOOTH_CONNECT and BLUETOOTH_SCAN permissions are granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

                // Request both permissions at once
                ActivityCompat.requestPermissions(this, arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN), 1)
            } else {
                // Permissions are already granted, proceed with Bluetooth connection
                connectToBluetoothDevice()
            }
        } else {
            // For devices running below Android 12, no need to request these permissions
            connectToBluetoothDevice()
        }
    }
    @SuppressLint("MissingPermission")
    private fun connectToBluetoothDevice() {
        try {
            if (::bluetoothSocket.isInitialized && bluetoothSocket.isConnected) {
                bluetoothSocket.close()
            }

            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothAdapter.cancelDiscovery()

            bluetoothSocket.connect()

            // Delay to allow the Pi to prepare recv()
            Thread.sleep(1000)

            outputStream = bluetoothSocket.outputStream
            inputStream = bluetoothSocket.inputStream

            isConnected = bluetoothSocket.isConnected && outputStream != null && inputStream != null
            if (isConnected) {
                Log.d("BluetoothConnectionIoT", "Bluetooth reconnected and ready.")
            } else {
                Log.e("BluetoothConnectionIoT", "Streams not ready after connection.")
            }

        } catch (e: IOException) {
            isConnected = false
            Log.e("BluetoothConnectionIoT", "Bluetooth reconnection failed: ${e.message}")
            try {
                bluetoothSocket.close()
            } catch (closeEx: IOException) {
                Log.e("BluetoothConnectionIoT", "Error closing Bluetooth socket: ${closeEx.message}")
            }
        }
    }

    private val bluetoothConnectionReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    deviceAddress = device.address
                    Log.d("BluetoothConnectionIoT", "Connected Device MAC: $deviceAddress")
                    Toast.makeText(context, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
                    connectToBluetoothDevice() // Initiate connection once a device is connected
                }
            }
        }
    }
    private fun registerBluetoothReceiver() {
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
        registerReceiver(bluetoothConnectionReceiver, filter)
    }

//    override fun onDestroy() {
//        super.onDestroy()
//        unregisterReceiver(bluetoothConnectionReceiver)
//        try {
//            if (isConnected) {
//                bluetoothSocket.close()
//            }
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//    }
    private fun generateChecksum(data: String): String {
        try {
            val python = Python.getInstance()
            val pyResult = python.getModule("hash").callAttr("generate_md5", data)
            return pyResult.toString()  // This is the checksum returned by the Python function
        } catch (e: Exception) {
            Log.d("BluetoothConnectionIoT", "Error calling Python to generate checksum: ${e.message}")
            return ""
        }
    }
    private fun verifyReceivedChecksum(message: String, providedChecksum: String): Boolean {
        try {
            val python = Python.getInstance()
            val pyResult = python.getModule("hashverify").callAttr("verify_checksum", "$message:$providedChecksum")
            return pyResult.toString().toBoolean()  // Return true/false based on the Python verification result
        } catch (e: Exception) {
            Log.d("BluetoothConnectionIoT", "Error calling Python to verify checksum: ${e.message}")
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
            Log.d("BluetoothConnectionIoT", "Error retrieving local IP: ${e.message}")
        }
        return "0.0.0.0"
    }
    private fun verifyChecksum(receivedMessage: String) {
        val messageWithChecksum = receivedMessage.split(":")
        if (messageWithChecksum.size != 2) {
            Log.d("BluetoothConnectionIoT", "Invalid message format: Missing checksum delimiter ':'")
            return
        }

        val messagePart = messageWithChecksum[0]
        val providedChecksum = messageWithChecksum[1]

        // Ensure the message is enclosed in quotes
        if (!messagePart.startsWith("\"") || !messagePart.endsWith("\"")) {
            Log.d("BluetoothConnectionIoT", "Invalid message format: Expected quotes around message")
            return
        }

        // Extract the actual message by removing surrounding quotes
        val cleanedMessage = messagePart.substring(1, messagePart.length - 1)
        val messageParts = cleanedMessage.split(",")

        if (messageParts.size != 2) {
            Log.d("BluetoothConnectionIoT", "Invalid message format: Expected format 'message,sender_ip'")
            return
        }

        val message = messageParts[0]
        val senderIp = messageParts[1]

        // Verify the checksum
        val isValid = verifyReceivedChecksum("$message,$senderIp", providedChecksum)
        if (isValid) {
           Log.d("BluetoothConnectionIoT", "Checksum verification successful. Message integrity confirmed.")
        } else {
           Log.d("BluetoothConnectionIoT", "Checksum verification failed! Possible data corruption or tampering.")
        }
    }
    private fun encryptMessageWithPython(plainText: String): String? {
        try {
            // Define path to the public key (same as where private key is)
            val publicKeyPath = "/storage/emulated/0/Android/data/com.example.iot1/files/thingPublicKey.txt"
            Log.d("BluetoothConnectionIoT", "Public key found")

            // Read the public key from the file
            val publicKey = File(publicKeyPath).readText()
            Log.d("BluetoothConnectionIoT", "public key read: $publicKey")

            // Use Chaquopy to call Python for encryption
            val python = Python.getInstance()
            val pyResult = python.getModule("encrypt").callAttr("encrypt_message", publicKey, plainText)
            Log.d("BluetoothConnectionIoT", "imported module")
            return pyResult.toString()  // Return the encrypted message
        } catch (e: Exception) {
            Log.d("BluetoothConnectionIoT", "Encryption failed: ${e.message}")
            return null
        }
    }

    @SuppressLint("SdCardPath")
    private fun decryptMessageWithPython(encryptedMessage: String): String? {
        try {
            // Define path to the private key (same as where the public key is)
            val privateKeyPath = "/data/data/com.example.iot1/files/privateKey.txt"

            // Read the private key from the file
            val privateKey = File(privateKeyPath).readText()

            // Use Chaquopy to call Python for decryption
            val python = Python.getInstance()
            val pyResult = python.getModule("decrypt").callAttr("decrypt_message", privateKey, encryptedMessage)

            return pyResult.toString()  // Return the decrypted message
        } catch (e: Exception) {
            Log.d("BluetoothConnectionIoT", "Decryption failed: ${e.message}")
            return null
        }
    }
}
