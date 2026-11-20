package io.qzz.xandll.morgo.presentation.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.qzz.xandll.morgo.domain.update.UpdateOffer

@Composable
fun UpdateDialog(
    offer: UpdateOffer,
    canInstall: Boolean,
    status: String?,
    onLater: () -> Unit,
    onInstall: () -> Unit,
    onAllowUnknown: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Morgo ${offer.tag.removePrefix("v")}") },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(
                    offer.notes.ifBlank { "Ein neueres Release liegt auf GitHub." },
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (canInstall) {
                        "Die APK wird geladen und nur installiert, wenn Prüfsumme und Signatur zur installierten App passen."
                    } else {
                        "Android braucht einmalig die Freigabe „Unbekannte Apps“ für Morgo. Erlaube sie nur dieser App."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
                if (status != null) {
                    Text(status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = if (canInstall) onInstall else onAllowUnknown) {
                Text(if (canInstall) "Installieren" else "Freigabe öffnen")
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text("Später") }
        }
    )
}
