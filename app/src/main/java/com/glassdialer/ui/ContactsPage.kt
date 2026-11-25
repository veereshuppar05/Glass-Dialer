package com.glassdialer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glassdialer.data.Contact

@Composable
fun ContactsPage(contacts: List<Contact>, onCall: (String) -> Unit) {
    val g = LocalGlass.current
    var q by rememberSaveable { mutableStateOf("") }
    val shown = remember(contacts, q) {
        val digits = q.filter { it.isDigit() }
        if (q.isBlank()) contacts else contacts.filter {
            it.name.contains(q, ignoreCase = true) ||
                (digits.isNotEmpty() && it.number.filter(Char::isDigit).contains(digits))
        }
    }
    val grouped = remember(shown) {
        shown.groupBy { it.name.first().uppercaseChar().let { c -> if (c.isLetter()) c else '#' } }
    }

    Column {
        Text(
            "Contacts", fontSize = 26.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 12.dp),
        )
        Row(
            Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(46.dp)
                .clip(Pill).background(g.key).border(0.5.dp, g.border, Pill)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, Modifier.size(18.dp), tint = g.text2)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                textStyle = TextStyle(color = g.text, fontSize = 15.sp),
                cursorBrush = SolidColor(CallGreen),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (q.isEmpty()) Text("Search ${contacts.size} contacts", color = g.text2, fontSize = 15.sp)
                    inner()
                },
            )
        }
        LazyColumn(contentPadding = PaddingValues(top = 6.dp, bottom = 130.dp)) {
            grouped.forEach { (letter, list) ->
                item(key = "h$letter") {
                    Text(
                        letter.toString(), fontSize = 13.sp, color = CallGreen,
                        modifier = Modifier.padding(start = 24.dp, top = 10.dp, bottom = 2.dp),
                    )
                }
                items(list, key = { "c${it.id}" }) { c ->
                    ListRow(name = c.name, title = c.name, subtitle = c.number) { onCall(c.number) }
                }
            }
        }
    }
}
