package com.example.iot1

import DBHelper
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.iot1.databinding.ActivityAvailableThingsBinding

class AvailableThingsActivity : AppCompatActivity() {
    private lateinit var thingAdapter: ThingAdapter
    private lateinit var binding: ActivityAvailableThingsBinding
    private lateinit var dbHelper: DBHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAvailableThingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DBHelper(this)

        // Set up window insets for proper padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Set up RecyclerView with a LinearLayoutManager
        binding.recyclerViewThings.layoutManager = LinearLayoutManager(this)

        // Load data from the database
        loadThings()

        // Set up the Add Device button click listener
        binding.btnAddDevice.setOnClickListener {
            val intent = Intent(this@AvailableThingsActivity, ThingDetailsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload data every time the activity resumes
        loadThings()
    }

    private fun loadThings() {
        val thingList = mutableListOf<Thing>()
        val db = dbHelper.readableDatabase

        val cursor = db.query(
            DBHelper.TABLE_NAME,
            arrayOf(DBHelper.COLUMN_THING_NAME, DBHelper.COLUMN_IP_ADDRESS), // Fetch IP address
            null,
            null,
            null,
            null,
            null
        )

        if (cursor.moveToFirst()) {
            do {
                val thingName = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_THING_NAME))
                val ipAddress = cursor.getString(cursor.getColumnIndexOrThrow(DBHelper.COLUMN_IP_ADDRESS)) // Get IP address
                thingList.add(Thing(thingName, ipAddress)) // Add IP address to Thing
            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()

        if (thingList.isNotEmpty()) {
            binding.txtNoDevice.visibility = View.GONE
            binding.recyclerViewThings.visibility = View.VISIBLE
            thingAdapter = ThingAdapter(thingList) { thing ->
                // Handle item click here
                val intent = Intent(this, DisplayThingActivity::class.java)
                intent.putExtra("thing_name", thing.thingName)
                intent.putExtra("ip_address", thing.ipAddress) // Pass IP address to the next activity
                startActivity(intent)
            }
            binding.recyclerViewThings.adapter = thingAdapter
        } else {
            binding.txtNoDevice.visibility = View.VISIBLE
            binding.recyclerViewThings.visibility = View.GONE
        }
    }

}
