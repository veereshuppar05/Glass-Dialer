package com.glassdialer.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.glassdialer.telecom.CallManager
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CallRecorder {
    enum class Mode { CALL_AUDIO, MICROPHONE }

    data class Status(
        val recording: Boolean = false,
        val startedAt: Long = 0L,
        val mode: Mode? = null,
        val file: File? = null,
    )

    val status = MutableStateFlow(Status())
    private var recorder: MediaRecorder? = null
    private var speakerWasAlreadyOn = false

    fun dir(ctx: Context): File =
        File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Recordings").apply { mkdirs() }

    /** True only when the app is installed as a privileged system app. */
    fun canRecordCallAudio(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAPTURE_AUDIO_OUTPUT) ==
            PackageManager.PERMISSION_GRANTED

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun autoRecord(ctx: Context): Boolean =
        prefs(ctx).getBoolean("auto_record", false)

    fun setAutoRecord(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("auto_record", on).apply()
    }

    fun start(ctx: Context, number: String) {
        if (status.value.recording) return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(ctx, "Allow the microphone permission to record calls", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, RecordService::class.java).putExtra(RecordService.EXTRA_NUMBER, number),
            )
        }.onFailure {
            Toast.makeText(ctx, "Unable to start call recording", Toast.LENGTH_SHORT).show()
        }
    }

    fun stop(ctx: Context) {
        ctx.stopService(Intent(ctx, RecordService::class.java))
    }

    fun amplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    @Suppress("DEPRECATION")
    private fun newRecorder(ctx: Context): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()

    internal fun begin(ctx: Context, number: String): Boolean {
        if (recorder != null) return true
        val safeNumber = number.filter { it.isDigit() || it == '+' }.ifEmpty { "unknown" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir(ctx), "${safeNumber}_${stamp}.m4a")

        val sources = buildList {
            if (canRecordCallAudio(ctx)) {
                add(MediaRecorder.AudioSource.VOICE_CALL to Mode.CALL_AUDIO)
            }
            add(MediaRecorder.AudioSource.MIC to Mode.MICROPHONE)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION to Mode.MICROPHONE)
        }

        for ((source, mode) in sources) {
            val mediaRecorder = newRecorder(ctx)
            try {
                mediaRecorder.setAudioSource(source)
                mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mediaRecorder.setAudioSamplingRate(44100)
                mediaRecorder.setAudioEncodingBitRate(96000)
                mediaRecorder.setOutputFile(file.absolutePath)
                mediaRecorder.prepare()
                mediaRecorder.start()
                recorder = mediaRecorder

                // With microphone-only capture, speakerphone may let the mic pick up the remote voice.
                if (mode == Mode.MICROPHONE) {
                    speakerWasAlreadyOn =
                        CallManager.audio.value?.route == android.telecom.CallAudioState.ROUTE_SPEAKER
                    CallManager.speaker(true)
                }
                status.value = Status(true, System.currentTimeMillis(), mode, file)
                return true
            } catch (_: Exception) {
                runCatching { mediaRecorder.reset() }
                mediaRecorder.release()
                file.delete()
            }
        }
        return false
    }

    internal fun end() {
        val activeRecorder = recorder ?: return
        val current = status.value
        runCatching { activeRecorder.stop() }.onFailure { current.file?.delete() }
        runCatching { activeRecorder.release() }
        recorder = null
        if (current.mode == Mode.MICROPHONE && !speakerWasAlreadyOn) {
            runCatching { CallManager.speaker(false) }
        }
        speakerWasAlreadyOn = false
        status.value = Status()
    }
}

class RecordService : Service() {
    companion object {
        const val EXTRA_NUMBER = "number"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 9
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Call recording", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Recording call")
            .setOngoing(true)
            .build()

        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        val number = intent?.getStringExtra(EXTRA_NUMBER).orEmpty()
        if (!CallRecorder.begin(this, number)) {
            Toast.makeText(this, "This phone blocked call recording", Toast.LENGTH_LONG).show()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        CallRecorder.end()
        super.onDestroy()
    }
}
