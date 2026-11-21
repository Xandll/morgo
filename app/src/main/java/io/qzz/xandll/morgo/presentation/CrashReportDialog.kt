package io.qzz.xandll.morgo.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.qzz.xandll.morgo.data.CrashLog

@Composable
fun CrashReportDialog(context: Context) {
    var report by remember { mutableStateOf(CrashLog.peek(context)) }
    val text = report ?: return
    val dismiss = {
        CrashLog.clear(context)
        report = null
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Letzter Absturz") },
        text = {
            Text(
                text,
                modifier = Modifier
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace
            )
        },
        confirmButton = {
            TextButton(onClick = { share(context, text) }) { Text("Teilen") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { copy(context, text) }) { Text("Kopieren") }
                TextButton(onClick = dismiss) { Text("Schließen") }
            }
        }
    )
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Morgo Absturz", text))
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    val chooser = Intent.createChooser(send, "Absturzbericht")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}
