package io.github.menadion.magus

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

// Whether this phone may send the six facts in Diagnostics (which phone, which Mogar, whether the
// keep-running steps were done, the last crash) along with its location.
//
// Asked once, plainly, with a Yes/No box, and nothing is sent until it is answered (M's call,
// 2026-10-04). Changeable afterwards from Settings. Saying no also deletes what was already sent,
// because the location write merges rather than replaces, so leaving the old field behind would
// make "no" cosmetic.
object Consent {
    private const val KEY = "diagConsent"
    private const val YES = "yes"
    private const val NO = "no"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // No answer stored yet. The box is shown for exactly this case.
    fun dueNow(context: Context): Boolean = prefs(context).getString(KEY, null) == null

    // The gate every send reads. Unanswered counts as no.
    fun allowed(context: Context): Boolean = prefs(context).getString(KEY, null) == YES

    // A no also clears everything this phone already sent, so the answer reaches backwards: the
    // diagnostics riding on its member document, and the settings-page report in probes.
    fun set(context: Context, allow: Boolean) {
        prefs(context).edit().putString(KEY, if (allow) YES else NO).apply()
        if (allow) {
            // Changing the Settings row to yes asks straight away. Without this the probe waits for
            // the next launch, because the effect that runs it is keyed on the first-launch box.
            Probe.run(context)
        } else {
            Family.clearDiagnostics(context)
            Probe.clear(context)
        }
    }
}

// Shown once, over the map, before What's new and the tour. Not dismissible: an unanswered box
// means nothing is sent, so dismissing it would only bring it back on the next launch.
@Composable
fun ConsentDialog(onAnswered: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.consent_title), style = MaterialTheme.typography.titleMedium) },
        text = { Text(stringResource(R.string.consent_body), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = { Consent.set(context, true); onAnswered() }) {
                Text(stringResource(R.string.consent_yes), style = MaterialTheme.typography.bodyLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = { Consent.set(context, false); onAnswered() }) {
                Text(stringResource(R.string.consent_no), style = MaterialTheme.typography.bodyLarge)
            }
        },
    )
}
