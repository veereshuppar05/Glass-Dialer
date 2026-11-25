package com.glassdialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.glassdialer.data.PhoneRepository
import com.glassdialer.ui.GlassTheme
import com.glassdialer.ui.MainApp

class MainActivity : ComponentActivity() {
    private var dialNumber by mutableStateOf("")
    private var refresh by mutableIntStateOf(0)
    private var isDefault by mutableStateOf(false)

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checkRole()
            requestPerms()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        dialNumber = numberFrom(intent)
        checkRole()
        if (savedInstanceState == null) requestRole()
        val repo = PhoneRepository(applicationContext)
        setContent {
            GlassTheme {
                MainApp(repo, refresh, dialNumber, isDefault, onMakeDefault = ::requestRole)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        numberFrom(intent).takeIf { it.isNotEmpty() }?.let { dialNumber = it }
    }

    override fun onResume() {
        super.onResume()
        checkRole()
    }

    private fun numberFrom(i: Intent?) =
        i?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart.orEmpty()

    private fun checkRole() {
        isDefault = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)
    }

    private fun requestRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        } else requestPerms()
    }

    private fun requestPerms() {
        val wanted = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
        )
        if (Build.VERSION.SDK_INT >= 33) wanted += Manifest.permission.POST_NOTIFICATIONS
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permLauncher.launch(missing.toTypedArray())
    }
}
