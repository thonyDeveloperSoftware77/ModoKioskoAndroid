package com.example.modokiosko

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.modokiosko.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyPairGenerator
import java.util.Base64


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    companion object {
        private val REQUEST_READ_PHONE_STATE = 1
        private val REQUEST_READ_SMS = 2
        private val REQUEST_READ_PHONE_NUMBERS = 3
        private var isKioskModeActive = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "notification_channel",
                "notification_channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        FirebaseMessaging.getInstance().subscribeToTopic("general")
            .addOnCompleteListener { task ->
                var msg = "Subscribed Successfully"
                if (!task.isSuccessful) {
                    msg = "Subscription failed"
                }
                Log.d("mensaje", msg)
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
            }

        binding.fab.setOnClickListener { view ->
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), REQUEST_READ_PHONE_STATE)
            } else if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_SMS), REQUEST_READ_SMS)
            } else if (checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_PHONE_NUMBERS), REQUEST_READ_PHONE_NUMBERS)
            } else {
                checkIfInKioskMode()
                getSimInformation()
                getPhoneNumber()
                getInfoPhone()
                if (isKioskModeActive) {
                    Snackbar.make(view, "Modo kiosko activado", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show()
                } else {
                    Snackbar.make(view, "Modo kiosko desactivado", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show()
                }
                // Abre el panel de configuración de WiFi sin importar si isWifiConnected es true o false
                //val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                //startActivity(panelIntent)

                // Aquí es donde agregamos el código para enviar el mensaje SCEP
                GlobalScope.launch(Dispatchers.IO) {
                    var intentos = 0
                    while (true) {
                        try {
                            val scepMessage = generarMensajeSCEP() // Aquí generas tu mensaje SCEP
                            val encodedMessage = URLEncoder.encode(scepMessage, "UTF-8")
                            val url = URL("https://b9f5-102-177-166-45.ngrok-free.app/enrollment?message=$encodedMessage")
                            val conn = url.openConnection() as HttpURLConnection
                            conn.requestMethod = "GET"

                            val `in` = BufferedInputStream(conn.inputStream)
                            val response = `in`.bufferedReader().use { it.readText() }

                            Log.i("Response", response)
                            allowLockTaskModeToThisApp(true)

                            `in`.close()
                            conn.disconnect()

                            intentos = 0 // Resetea el contador de intentos
                            delay(5000) // Espera 5 segundos antes de la próxima solicitud
                        } catch (e: Exception) {
                            intentos++
                            if (intentos >= 2) {
                                Log.e("Error", "Sin conexión después de 2 intentos. Procediendo a bloquear...")
                                allowLockTaskModeToThisApp(false)
                                delay(5000) // Espera 5 segundos antes de intentar de nuevo
                                // Activa el WiFi
                                // Activa el WiFi
                                val connectivityManager = this@MainActivity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                                val activeNetwork = connectivityManager.activeNetwork
                                if (activeNetwork != null) {
                                    val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                                    val isWifiConnected = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false

                                    // Abre el panel de configuración de WiFi sin importar si isWifiConnected es true o false
                                    val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                                    startActivity(panelIntent)
                                } else {
                                    // Si activeNetwork es null, intenta abrir el panel de configuración de WiFi
                                    val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                                    startActivity(panelIntent)
                                }
                                delay(5000)
                                allowLockTaskModeToThisApp(true)
                                break
                            } else {
                                Log.e("Error", "Sin conexión. Intentando de nuevo en 5 segundos...")
                                delay(5000) // Espera 5 segundos antes de intentar de nuevo
                            }
                            e.printStackTrace()
                        }
                    }
                }

            }
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_READ_PHONE_STATE || requestCode == REQUEST_READ_SMS || requestCode == REQUEST_READ_PHONE_NUMBERS) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                getPhoneNumber()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getPhoneNumber() {
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val phoneNumber = telephonyManager.line1Number
        Log.d("PHONE_INFO", "Phone Number: $phoneNumber")
    }
    private fun getSimInformation() {
        val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        val simState = telephonyManager.simState

        Log.d("SIM_INFO", "SIM State: $simState")
    }

    private fun allowLockTaskModeToThisApp(valor : Boolean) {
        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val deviceAdmin = ComponentName(this, DeviceAdminBroadcastReceiver::class.java)
        if (dpm.isDeviceOwnerApp(packageName)) {
            dpm.setLockTaskPackages(deviceAdmin, arrayOf(packageName))
            Log.d("DEVICE_OWNERS", "App is device owners")
            // Cambia el estado del modo kiosko cada vez que haces clic en el botón
            if(valor){
                stopLockTask()
            }else{
                startLockTask()
            }

        } else {
            Log.d("DEVICE_OWNERS", "App is not device owners")
        }
    }



    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
    private fun checkIfInKioskMode() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val isInKioskMode = am.isInLockTaskMode
        Log.d("KIOSK_MODE", "Is app in kiosk mode: $isInKioskMode")
    }
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    fun getInfoPhone(){
        val deviceName = Build.MODEL
        val deviceManufacturer = Build.MANUFACTURER
        val osVersion = Build.VERSION.RELEASE


        Log.d("DeviceName", deviceName)
        Log.d("DeviceManufacturer", deviceManufacturer)
        Log.d("OSversion", osVersion)

    }
    fun generarMensajeSCEP(): String? {
        try {
            // Genera un par de claves RSA
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048)
            val keyPair = keyPairGenerator.generateKeyPair()
            val publicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)

            // Crea una CSR con la clave pública
            val csrBuilder = PKCS10CertificationRequestBuilder(
                X500Name("CN=Requested Test Certificate"),
                publicKeyInfo
            )
            val csBuilder = JcaContentSignerBuilder("SHA256withRSA")
            val signer: ContentSigner = csBuilder.build(keyPair.private)
            val csr: PKCS10CertificationRequest = csrBuilder.build(signer)

            // Codifica la CSR en un mensaje SCEP
            // Este es un paso muy simplificado, en la realidad necesitarías agregar más información al mensaje SCEP
            val scepMessage: ByteArray = csr.getEncoded()

            // Codifica el mensaje SCEP en Base64 para enviarlo como texto
            return Base64.getEncoder().encodeToString(scepMessage)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

}