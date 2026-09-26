package io.github.menadion.magus

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// What changed in this version: a popup the first time an updated Mogar opens, and again from
// Settings > What's new (M's calls, 2026-09-27). The notes live in strings.xml, whats_new_version
// and whats_new_items, and cover the current version only; each release rewrites them. Notes still
// marked for an older version never show, so a release that forgets them simply has no popup.
object WhatsNew {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // True when the notes in this build are for the version installed.
    fun hasNotes(context: Context) = context.getString(R.string.whats_new_version) == Diagnostics.appVersion(context)

    // Once per version, and only after an update: a fresh install starts at setup, not here.
    fun dueNow(context: Context): Boolean {
        if (prefs(context).getString("whatsNewSeen", null) == Diagnostics.appVersion(context)) return false
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (info.firstInstallTime == info.lastUpdateTime) {
            markSeen(context)
            return false
        }
        return hasNotes(context)
    }

    fun markSeen(context: Context) =
        prefs(context).edit().putString("whatsNewSeen", Diagnostics.appVersion(context)).apply()
}

@Composable
fun WhatsNewDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        icon = { Icon(Icons.Default.Star, contentDescription = null, tint = colors.primary) },
        title = {
            Text(
                stringResource(R.string.whats_new_title, Diagnostics.appVersion(context)),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                stringArrayResource(R.array.whats_new_items).forEach {
                    Text("•  $it", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.ok)) } },
    )
}
