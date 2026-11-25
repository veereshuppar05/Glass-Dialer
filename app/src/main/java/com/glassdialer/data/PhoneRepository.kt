package com.glassdialer.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telecom.TelecomManager
import android.text.format.DateUtils
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CallEntry(
    val id: Long, val number: String, val name: String?,
    val type: Int, val date: Long, val location: String?,
)

data class Contact(val id: Long, val name: String, val number: String)

class PhoneRepository(private val ctx: Context) {
    private fun has(p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    suspend fun calls(): List<CallEntry> = withContext(Dispatchers.IO) {
        if (!has(Manifest.permission.READ_CALL_LOG)) return@withContext emptyList()
        val cols = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.GEOCODED_LOCATION,
        )
        val out = ArrayList<CallEntry>()
        ctx.contentResolver.query(
            CallLog.Calls.CONTENT_URI, cols, null, null, "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && out.size < 300) {
                out += CallEntry(
                    id = c.getLong(0),
                    number = c.getString(1).orEmpty(),
                    name = c.getString(2)?.takeIf { it.isNotBlank() },
                    type = c.getInt(3),
                    date = c.getLong(4),
                    location = c.getString(5)?.takeIf { it.isNotBlank() },
                )
            }
        }
        out
    }

    suspend fun contacts(): List<Contact> = withContext(Dispatchers.IO) {
        if (!has(Manifest.permission.READ_CONTACTS)) return@withContext emptyList()
        val out = LinkedHashMap<Long, Contact>()
        ctx.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER),
            null, null, "${Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1)?.trim().orEmpty()
                val num = c.getString(2).orEmpty()
                if (name.isNotEmpty() && num.isNotEmpty() && id !in out) out[id] = Contact(id, name, num)
            }
        }
        out.values.toList()
    }

    fun lookupName(number: String): String? {
        if (number.isBlank() || !has(Manifest.permission.READ_CONTACTS)) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        return ctx.contentResolver.query(
            uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }
}

/** Smart search: matches digits in the number, or names typed with keypad letters. */
object T9 {
    private val map: Map<Char, Char> = buildMap {
        mapOf(
            '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
            '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz",
        ).forEach { (d, letters) -> letters.forEach { put(it, d) } }
    }

    private fun digits(s: String) =
        s.lowercase().mapNotNull { if (it.isDigit()) it else map[it] }.joinToString("")

    fun matches(query: String, name: String?, number: String): Boolean {
        if (query.isEmpty()) return true
        val q = query.filter { it.isDigit() }
        if (q.isEmpty()) return number.contains(query)
        if (number.filter { it.isDigit() }.contains(q)) return true
        if (name == null) return false
        return name.split(" ").any { digits(it).startsWith(q) } ||
            digits(name.replace(" ", "")).startsWith(q)
    }
}

@SuppressLint("MissingPermission")
fun placeCall(ctx: Context, number: String) {
    if (number.isBlank()) return
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE)
        != PackageManager.PERMISSION_GRANTED
    ) {
        Toast.makeText(ctx, "Allow the Phone permission to make calls", Toast.LENGTH_SHORT).show()
        return
    }
    ctx.getSystemService(TelecomManager::class.java)
        .placeCall(Uri.fromParts("tel", number, null), Bundle())
}

fun formatTime(ms: Long): String = when {
    DateUtils.isToday(ms) -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
    DateUtils.isToday(ms + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
    else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(ms))
}

fun formatDuration(sec: Long): String = "%02d:%02d".format(sec / 60, sec % 60)
