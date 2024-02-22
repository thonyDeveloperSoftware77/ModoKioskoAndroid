package com.example.modokiosko

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.Manifest
import android.annotation.SuppressLint
import android.view.MenuItem
import androidx.core.app.ActivityCompat
import com.example.modokiosko.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    companion object {
        private val REQUEST_READ_PHONE_STATE = 1
        private val REQUEST_READ_SMS = 2
        private val REQUEST_READ_PHONE_NUMBERS = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener { view ->
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), REQUEST_READ_PHONE_STATE)
            } else if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_SMS), REQUEST_READ_SMS)
            } else if (checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.READ_PHONE_NUMBERS), REQUEST_READ_PHONE_NUMBERS)
            } else {
                allowLockTaskModeToThisApp()
                checkIfInKioskMode()
                getSimInformation()
                getPhoneNumber()
                Snackbar.make(view, "Modo kiosko activado", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
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
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val phoneNumber = telephonyManager.line1Number
        Log.d("PHONE_INFO", "Phone Number: $phoneNumber")
    }
    private fun getSimInformation() {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val simState = telephonyManager.simState

        Log.d("SIM_INFO", "SIM State: $simState")
    }

    private fun allowLockTaskModeToThisApp() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val deviceAdmin = ComponentName(this, DeviceAdminBroadcastReceiver::class.java)
        if (dpm.isDeviceOwnerApp(packageName)) {
            dpm.setLockTaskPackages(deviceAdmin, arrayOf(packageName))
            Log.d("DEVICE_OWNERS", "App is device owners")
            // Iniciar el modo kiosko
            //startLockTask();
            stopLockTask()
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
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isInKioskMode = am.isInLockTaskMode
        Log.d("KIOSK_MODE", "Is app in kiosk mode: $isInKioskMode")
    }
    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}