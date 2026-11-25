@file:Suppress("DEPRECATION")

package com.glassdialer.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.glassdialer.InCallActivity
import kotlinx.coroutines.flow.MutableStateFlow

object CallManager {
    val call = MutableStateFlow<Call?>(null)
    val state = MutableStateFlow(Call.STATE_NEW)
    val audio = MutableStateFlow<CallAudioState?>(null)
    var service: InCallService? = null
    private val all = mutableListOf<Call>()

    private fun stateOf(c: Call) =
        if (Build.VERSION.SDK_INT >= 31) c.details.state else c.state

    private val callback = object : Call.Callback() {
        override fun onStateChanged(c: Call, newState: Int) {
            if (c == call.value) {
                state.value = newState
                service?.let { CallNotification.show(it, c, newState) }
            }
        }
    }

    private fun focus(c: Call) {
        call.value = c
        state.value = stateOf(c)
    }

    fun add(c: Call) {
        all += c
        c.registerCallback(callback)
        focus(c)
    }

    fun remove(c: Call) {
        c.unregisterCallback(callback)
        all -= c
        val next = all.lastOrNull()
        if (next != null) focus(next) else {
            state.value = Call.STATE_DISCONNECTED
            call.value = null
        }
    }

    fun answer() { call.value?.answer(VideoProfile.STATE_AUDIO_ONLY) }

    fun hangup() {
        val c = call.value ?: return
        if (state.value == Call.STATE_RINGING) c.reject(false, null) else c.disconnect()
    }

    fun rejectWithMessage(text: String) { call.value?.reject(true, text) }

    fun hold(on: Boolean) { call.value?.let { if (on) it.hold() else it.unhold() } }

    fun mute(on: Boolean) { service?.setMuted(on) }

    fun speaker(on: Boolean) {
        service?.setAudioRoute(
            if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
        )
    }

    fun dtmf(ch: Char) { call.value?.apply { playDtmfTone(ch); stopDtmfTone() } }
}

class GlassInCallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onDestroy() {
        if (CallManager.service === this) CallManager.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallManager.add(call)
        CallNotification.show(this, call, CallManager.state.value)
        runCatching {
            startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onCallRemoved(call: Call) {
        CallManager.remove(call)
        if (CallManager.call.value == null) CallNotification.cancel(this)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.audio.value = audioState
    }
}

object CallNotification {
    private const val CHANNEL = "calls"
    private const val ID = 7

    fun show(ctx: Context, call: Call, state: Int) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Calls", NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null) }
        )
        val pi = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val ringing = state == Call.STATE_RINGING
        val builder = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(if (ringing) android.R.drawable.sym_call_incoming else android.R.drawable.sym_action_call)
            .setContentTitle(call.details.handle?.schemeSpecificPart ?: "Unknown")
            .setContentText(if (ringing) "Incoming call" else "Ongoing call")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(pi)
        if (ringing) builder.setFullScreenIntent(pi, true)
        runCatching { nm.notify(ID, builder.build()) }
    }

    fun cancel(ctx: Context) =
        ctx.getSystemService(NotificationManager::class.java).cancel(ID)
}
