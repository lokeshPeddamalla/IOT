import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

data class RegisterUserDetails(val username: String, val email: String, val mobileNum: String, val thingId: String)
data class ThingDetails(val thingName: String, val thingId: String, val thingKey: String)
data class ValidationResult(val valid: Boolean)
data class RegistrationResult(val registered: Boolean)
data class UserIpResponse(val thingIp: String?)


interface ApiService {

    @POST("/register_user")
    fun registerUser(@Body registerUserDetails: RegisterUserDetails): Call<RegistrationResult>

    @POST("/register_thing")
    fun registerThing(@Body thingDetails: ThingDetails): Call<RegistrationResult>

    @GET("/get_user_ip")
    fun getUserIp(@Query("thingId") thingId: String): Call<UserIpResponse>
}

object RetrofitClient {
    private const val BASE_URL = "http://10.203.5.193:5000/"

    val instance: ApiService by lazy {
        // Setup logging interceptor
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // Setup OkHttpClient with logging and custom SSL/TLS configuration
        val client = OkHttpClient.Builder().apply {
            // Load custom certificate if necessary
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            trustManagerFactory.init(keyStore)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustManagerFactory.trustManagers, null)
            sslSocketFactory(sslContext.socketFactory, trustManagerFactory.trustManagers[0] as X509TrustManager)

            // Add logging interceptor to OkHttpClient
            addInterceptor(logging)
        }.build()

        // Build Retrofit instance
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
