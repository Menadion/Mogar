package io.github.menadion.magus

import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

// Sharing reminders, set from Settings (M, 2026-11-27): a list of rules, each one a time, some days
// and "turn on" or "turn off". Every change goes through SharingReminders.saveRules, which also
// re-arms the alarm, so nothing here touches the alarm itself.
@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var rules by remember { mutableStateOf(SharingReminders.rules(context)) }
    // The rule open in the editor: an existing one, or a fresh one with the next free id.
    var editing by remember { mutableStateOf<ReminderRule?>(null) }
    var notificationsOn by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }

    // Back from Android's notification settings: the answer may have changed while this was open.
    DisposableEffect(context) {
        val lifecycle = (context as ComponentActivity).lifecycle
        val watcher = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycle.addObserver(watcher)
        onDispose { lifecycle.removeObserver(watcher) }
    }

    fun save(next: List<ReminderRule>) {
        rules = next
        SharingReminders.saveRules(context, next)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.height(64.dp).padding(start = 4.dp, end = 16.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_to_settings))
                    }
                    Text(stringResource(R.string.sharing_reminders), style = MaterialTheme.typography.headlineMedium)
                }

                Column(
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        stringResource(R.string.reminders_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )

                    if (!notificationsOn) {
                        Surface(shape = MaterialTheme.shapes.medium, color = colors.errorContainer, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.reminders_notifications_off), color = colors.onErrorContainer)
                                TextButton(onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                    )
                                }) { Text(stringResource(R.string.reminders_open_notification_settings)) }
                            }
                        }
                    }

                    if (rules.isNotEmpty()) {
                        Group(stringResource(R.string.reminders_list)) {
                            rules.forEachIndexed { index, rule ->
                                if (index > 0) Divider()
                                Surface(onClick = { editing = rule }, color = colors.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    ) {
                                        Icon(Icons.Default.Notifications, contentDescription = null, tint = colors.primary)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                stringResource(
                                                    if (rule.action == ReminderAction.ON) R.string.reminder_row_on else R.string.reminder_row_off,
                                                    timeText(rule.hour, rule.minute),
                                                ),
                                                style = MaterialTheme.typography.titleSmall,
                                            )
                                            Text(daysText(rule.days), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { editing = ReminderRule(SharingReminders.nextId(rules), 9, 0, DayOfWeek.values().toSet(), ReminderAction.ON) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.reminders_add)) }
                }
            }
        }
    }

    editing?.let { rule ->
        val exists = rules.any { it.id == rule.id }
        ReminderEditor(
            start = rule,
            canDelete = exists,
            onSave = { made ->
                save(if (exists) rules.map { if (it.id == made.id) made else it } else rules + made)
                editing = null
            },
            onDelete = {
                save(rules.filter { it.id != rule.id })
                editing = null
            },
            onClose = { editing = null },
        )
    }
}

// The editor for one rule: the time, the days, and what the reminder asks for.
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReminderEditor(
    start: ReminderRule,
    canDelete: Boolean,
    onSave: (ReminderRule) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val time = rememberTimePickerState(start.hour, start.minute, DateFormat.is24HourFormat(context))
    var days by remember { mutableStateOf(start.days) }
    var action by remember { mutableStateOf(start.action) }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(stringResource(if (canDelete) R.string.reminders_edit else R.string.reminders_add)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(ReminderAction.ON to R.string.reminders_action_on, ReminderAction.OFF to R.string.reminders_action_off)
                        .forEachIndexed { index, (value, label) ->
                            SegmentedButton(
                                selected = action == value,
                                onClick = { action = value },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 2),
                            ) { Text(stringResource(label)) }
                        }
                }
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = time) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayOfWeek.values().forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                            leadingIcon = if (day in days) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                        )
                    }
                }
                if (days.isEmpty()) {
                    Text(stringResource(R.string.reminders_pick_a_day), style = MaterialTheme.typography.bodyMedium, color = colors.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = days.isNotEmpty(),
                onClick = { onSave(ReminderRule(start.id, time.hour, time.minute, days, action)) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (canDelete) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = colors.error) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private fun timeText(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

// "Every day", or the days in week order: "Mon, Wed, Fri".
@Composable
private fun daysText(days: Set<DayOfWeek>): String = when {
    days.size == 7 -> stringResource(R.string.reminders_every_day)
    days.isEmpty() -> stringResource(R.string.reminders_no_days)
    else -> DayOfWeek.values().filter { it in days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}
