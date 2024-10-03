package com.example.iot1

import RegisterUserDetails
import RegistrationResult
import RetrofitClient
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.iot1.databinding.ActivityRegisterBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.security.MessageDigest
import kotlin.random.Random

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private var generatedOtp: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.btnSignUp.setOnClickListener {
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()
            if (password == confirmPassword) {
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        generatedOtp = withContext(Dispatchers.IO) { sendSms() }
                        if (generatedOtp != null) {
                            Toast.makeText(this@RegisterActivity, "OTP sent", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@RegisterActivity, "Failed to send OTP", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@RegisterActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnOtpVerify.setOnClickListener {
            val enteredOtp = binding.etOtp.text.toString().trim()
            if (enteredOtp == generatedOtp) {
                val username = binding.etUsername.text.toString().trim()
                val email = binding.etEmail.text.toString().trim()
                val mobileNum = binding.etMobileNumber.text.toString().trim()
                val thingId = binding.etThingId.text.toString().trim()
                val password = binding.etPassword.text.toString().trim()

                if (isNetworkAvailable()) {
                    registerUserOnline(username, email, mobileNum, thingId)
                    saveUserCredentialsOffline(username, password, thingId)
                } else {
                    Toast.makeText(this, "No internet connection. Registration failed.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Invalid OTP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.activeNetworkInfo?.isConnected == true
    }

    private fun registerUserOnline(username: String, email: String, mobileNum: String, thingId: String) {
        val userDetails = RegisterUserDetails(username, email, mobileNum, thingId)
        RetrofitClient.instance.registerUser(userDetails).enqueue(object : Callback<RegistrationResult> {
            override fun onResponse(call: Call<RegistrationResult>, response: Response<RegistrationResult>) {
                if (response.isSuccessful) {
                    val result = response.body()
                    if (result?.registered == true) {
                        Log.d("Registration", "User registered successfully (online)")
                        Toast.makeText(this@RegisterActivity, "Registration Successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                    } else {
                        Log.d("Registration", "Registration failed (online)")
                        Toast.makeText(this@RegisterActivity, "Registration failed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("Registration", "Response is not successful (online): ${response.errorBody()?.string()}")
                    Toast.makeText(this@RegisterActivity, "Registration failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<RegistrationResult>, t: Throwable) {
                Log.e("Registration", "Error (online): ${t.message}")
                Toast.makeText(this@RegisterActivity, "Registration failed", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // This method saves user credentials offline in encrypted shared preferences
    private fun saveUserCredentialsOffline(username: String, password: String, thingId: String) {
        val hashedPassword = hashWithSHA256(password)
        val hashedUsername = hashWithSHA256(username)
        val hashedThingId = hashWithSHA256(thingId)

        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            "user_credentials",
            masterKeyAlias,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        sharedPreferences.edit().apply {
            putString("username", hashedUsername)
            putString("password", hashedPassword)  // Save hashed password offline
            putString("registration_key", hashedThingId)
            apply()
        }
        Log.d("Registration", "User credentials saved offline securely")
    }

    private fun hashWithSHA256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private suspend fun sendSms(): String? {
        return try {
            val random = Random.nextInt(0, 9999).toString().padStart(4, '0')
            withContext(Dispatchers.Main) {
                Toast.makeText(this@RegisterActivity, "OTP $random", Toast.LENGTH_SHORT).show()
            }
            random
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@RegisterActivity, "SMS failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            null
        }
    }
}
