package com.glassdialer

import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.glassdialer.data.PhoneRepository
import com.glassdialer.ui.GlassTheme
import com.glassdialer.ui.InCallScreen

class InCallActivity : ComponentActivity() {
    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Turns the screen off when the phone is held to your ear
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "glassdialer:proximity")
        }

        val repo = PhoneRepository(applicationContext)
        setContent { GlassTheme { InCallScreen(repo, onClose = { finishAndRemoveTask() }) } }
    }

    override fun onStart() {
        super.onStart()
        proximity?.takeIf { !it.isHeld }?.acquire()
    }

    override fun onStop() {
        proximity?.takeIf { it.isHeld }?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        super.onStop()
    }
}
