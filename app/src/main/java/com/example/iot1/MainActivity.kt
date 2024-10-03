package com.example.iot1

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.iot1.databinding.ActivityMainBinding
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val username = binding.etUserId.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isNotEmpty() && password.isNotEmpty()) {

                    validateUserOffline(username, password)

            } else {
                Toast.makeText(this, "Please enter both username and password", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnSignUp.setOnClickListener {
            val intent = Intent(this@MainActivity, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.activeNetworkInfo?.isConnected == true
    }

//    private fun validateUserOnline(username: String, password: String) {
//        try {
//            Log.d("validateUserOnline", "Starting online validation for user: $username")
//            val userDetails = UserDetails(username, password)
//            RetrofitClient.instance.validateUser(userDetails).enqueue(object : Callback<ValidationResult> {
//                override fun onResponse(call: Call<ValidationResult>, response: Response<ValidationResult>) {
//                    if (response.isSuccessful) {
//                        val result = response.body()
//                        if (result?.valid == true) {
//                            Log.d("Validation", "User is valid (online)")
//                            navigateToAvailableThingsActivity()
//                        } else {
//                            Log.d("Validation", "Invalid user (online)")
//                            Toast.makeText(this@MainActivity, "Invalid Credentials", Toast.LENGTH_SHORT).show()
//                        }
//                    } else {
//                        Log.e("Validation", "Response is not successful (online): ${response.errorBody()?.string()}")
//                        validateUserOffline(username, password)
//                    }
//                }
//
//                override fun onFailure(call: Call<ValidationResult>, t: Throwable) {
//                    Log.e("Validation", "Error (online): ${t.message}")
//                    validateUserOffline(username, password)
//                }
//            })
//        } catch (e: Exception) {
//            Log.e("validateUserOnline", "Exception occurred: ${e.message}", e)
//            validateUserOffline(username, password)
//        }
//    }

    private fun validateUserOffline(username: String, password: String) {
        val userDetails = getUserCredentials()
        val hashedPassword = hashWithSHA256(password)
        val hashedUser = hashWithSHA256(username)

        Log.d("Validation", "Retrieved Username: ${userDetails["username"]}")
        Log.d("Validation", "Retrieved Hashed Password: ${userDetails["password"]}")
        Log.d("Validation", "Input Hashed Password: $hashedPassword")

        if (userDetails["username"] == hashedUser && userDetails["password"] == hashedPassword) {
            Log.d("Validation", "User is valid (offline)")
            navigateToAvailableThingsActivity()
        } else {
            Log.d("Validation", "Invalid user (offline) $username, $password")
            Toast.makeText(this, "Invalid Credentials", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToAvailableThingsActivity() {
        val intent = Intent(this, AvailableThingsActivity::class.java)
        startActivity(intent)
    }

    private fun hashWithSHA256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getUserCredentials(): Map<String, String?> {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            "user_credentials",
            masterKeyAlias,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        return mapOf(
            "username" to sharedPreferences.getString("username", null),
            "password" to sharedPreferences.getString("password", null)
        )
    }
}
