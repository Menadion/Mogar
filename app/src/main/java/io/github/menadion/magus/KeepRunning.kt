package io.github.menadion.magus

import androidx.compose.ui.res.stringResource
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

// Xiaomi, Vivo and Samsung kill background apps when they're swiped away, unless the phone is told not to.
// This screen walks through telling it. Plain Android only needs step 1.
object KeepRunning {
    enum class Brand { XIAOMI, VIVO, SAMSUNG, OTHER }

    private val realBrand: Brand = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> Brand.XIAOMI
        "vivo", "iqoo" -> Brand.VIVO
        "samsung" -> Brand.SAMSUNG
        else -> Brand.OTHER
    }

    // Debug builds only: lets a test force another brand's card onto this phone, which is the only
    // way to see the "nothing resolved" path without owning that brand. Set from MainActivity by
    // `am start ... --es brand vivo`; ignored outright in a release build.
    private var override: Brand? = null

    val brand: Brand get() = override ?: realBrand

    fun overrideBrand(context: Context, name: String?) {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) return
        override = when (name?.lowercase()) {
            "xiaomi" -> Brand.XIAOMI
            "vivo" -> Brand.VIVO
            "samsung" -> Brand.SAMSUNG
            "other" -> Brand.OTHER
            else -> null
        }
    }

    // Each brand's settings page, best guess first. A ROM update can rename or drop any of these,
    // so they are tried in order and checked before launching rather than fired blindly.
    //
    // Xiaomi's first entry was confirmed by hand on a Redmi Note 12 Pro, HyperOS 2 (OS2.0.17.0),
    // Android 14, on 2026-10-04. The Vivo and Samsung entries are the published names and are NOT
    // yet confirmed against a real phone; the probe exists to settle them.
    // Every brand's list, for the probe to report on at once: a phone can only confirm its own
    // brand, but there is no cost to checking the others and it keeps the report uniform.
    fun allCandidates(): List<ComponentName> =
        Brand.entries.flatMap { candidates(it) }

    private fun candidates(brand: Brand): List<ComponentName> = when (brand) {
        Brand.XIAOMI -> listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
        )
        Brand.VIVO -> listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"),
        )
        Brand.SAMSUNG -> listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
            ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )
        Brand.OTHER -> emptyList()
    }

    // Can this phone actually open that page? Answerable only because of the <queries> block.
    private fun exists(context: Context, component: ComponentName): Boolean =
        try {
            context.packageManager.getActivityInfo(component, 0).exported
        } catch (e: Exception) {
            false
        }

    // The first candidate this phone really has, or null when it has none of them.
    fun firstWorking(context: Context): ComponentName? =
        candidates(brand).firstOrNull { exists(context, it) }

    // True when this phone has a brand step but no page to send them to, so the card has to give
    // written directions instead of a button that goes somewhere useless.
    fun needsByHand(context: Context): Boolean =
        brand != Brand.OTHER && firstWorking(context) == null

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // Shown once automatically; after that, only from the ⋮ menu.
    fun introShown(context: Context) = prefs(context).getBoolean("keepRunningShown", false)
    fun markIntroShown(context: Context) = prefs(context).edit().putBoolean("keepRunningShown", true).apply()

    // Brand switches can't be read by the app, so the person ticks them.
    fun brandStepDone(context: Context) = prefs(context).getBoolean("brandStepDone", false)
    fun setBrandStepDone(context: Context, done: Boolean) =
        prefs(context).edit().putBoolean("brandStepDone", done).apply()

    // Their own report that the button went somewhere useless. The app can often work this out by
    // itself now (see firstWorking), but not always: a page can exist, open, and still not be the
    // one with the switch on it. Only the person in front of the screen knows that, and before
    // this there was no way for them to say so except to complain to M in person.
    fun pageFailed(context: Context) = prefs(context).getBoolean("brandPageFailed", false)
    fun setPageFailed(context: Context, failed: Boolean) =
        prefs(context).edit().putBoolean("brandPageFailed", failed).apply()

    // Step 1: Android's own "no battery restrictions" list. This one the app can check.
    fun isUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    // Android's "Let Mogar always run in the background?" box.
    @SuppressLint("BatteryLife") // the whole point of the app is to run in the background
    fun askUnrestricted(context: Context) {
        val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        if (!tryOpen(context, ask)) openAppInfo(context)
    }

    // Step 2: walk this brand's candidates and open the first one the phone really has.
    //
    // The old version fired one hardcoded name and, when it failed, quietly dropped the person on
    // Mogar's App info page. That is what M's father met: a button saying "turn on Autostart" that
    // landed somewhere with no Autostart on it, so he ticked "I did this" and the step was recorded
    // as done. Now a page that isn't there sends nobody anywhere; the card gives written directions
    // instead (see needsByHand).
    fun openBrandPage(context: Context) {
        val target = firstWorking(context)
        if (target == null) {
            // Nothing to deep-link to. Plain phone Settings, with the card's own words to follow.
            if (!tryOpen(context, Intent(Settings.ACTION_SETTINGS))) openAppInfo(context)
            return
        }
        if (!tryOpen(context, Intent().setComponent(target))) openAppInfo(context)
    }

    // Last step: Android removes an app's permissions if it isn't opened for a few months.
    // Parents may never open Mogar once it's set up, so this has to be off. Android 11 and up.
    fun neverPaused(context: Context): Boolean =
        Build.VERSION.SDK_INT < 30 || context.packageManager.isAutoRevokeWhitelisted

    // Every step this phone has is done. Until then the gear and the Settings row carry the red dot
    // (M's call, 2026-09-27; the brand step counts once they tick it, since the app can't see it).
    var allDone by mutableStateOf(true)
        private set

    fun refresh(context: Context) {
        allDone = isUnrestricted(context) && (brand == Brand.OTHER || brandStepDone(context)) && neverPaused(context)
    }

    fun openPauseSetting(context: Context) {
        val page = Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.parse("package:${context.packageName}"))
        if (!tryOpen(context, page)) openAppInfo(context)
    }

    private fun openAppInfo(context: Context) {
        tryOpen(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    private fun tryOpen(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}

@Composable
fun KeepRunningScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Re-read step 1 each time they come back from the settings page.
    var checks by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) checks++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val unrestricted = remember(checks) { KeepRunning.isUnrestricted(context) }
    val neverPaused = remember(checks) { KeepRunning.neverPaused(context) }
    var brandDone by remember { mutableStateOf(KeepRunning.brandStepDone(context)) }
    LaunchedEffect(unrestricted, neverPaused, brandDone) { KeepRunning.refresh(context) }

    // Spec: HANDOFF.md section 9. Done steps go tonal with a green tick; the rest stay white with a blue edge.
    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.keep_running),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                stringResource(R.string.keep_running_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            )

            StepCard(
                number = 1,
                title = stringResource(R.string.step_background_title),
                // Xiaomi skips Android's box and opens its own Battery saver page instead.
                detail = if (KeepRunning.brand == KeepRunning.Brand.XIAOMI) {
                    stringResource(R.string.step_background_xiaomi)
                } else {
                    stringResource(R.string.step_background_other)
                },
                done = unrestricted,
                buttonText = stringResource(R.string.allow),
                onButton = { KeepRunning.askUnrestricted(context) },
            )

            // When this phone has none of the brand's pages, the card stops promising a button that
            // works and spells the route out instead. The old fallback was one trailing sentence
            // inside the normal text, which M's father had in front of him and still missed.
            val byHand = remember(checks) { KeepRunning.needsByHand(context) }
            when (KeepRunning.brand) {
                KeepRunning.Brand.XIAOMI -> BrandStep(
                    title = stringResource(R.string.xiaomi_title),
                    detail = stringResource(if (byHand) R.string.xiaomi_by_hand else R.string.xiaomi_detail),
                    byHand = byHand,
                    done = brandDone,
                    onDoneChange = { brandDone = it; KeepRunning.setBrandStepDone(context, it) },
                )
                KeepRunning.Brand.VIVO -> BrandStep(
                    title = stringResource(R.string.vivo_title),
                    detail = stringResource(if (byHand) R.string.vivo_by_hand else R.string.vivo_detail),
                    byHand = byHand,
                    done = brandDone,
                    onDoneChange = { brandDone = it; KeepRunning.setBrandStepDone(context, it) },
                )
                KeepRunning.Brand.SAMSUNG -> BrandStep(
                    title = stringResource(R.string.samsung_title),
                    detail = stringResource(if (byHand) R.string.samsung_by_hand else R.string.samsung_detail),
                    byHand = byHand,
                    done = brandDone,
                    onDoneChange = { brandDone = it; KeepRunning.setBrandStepDone(context, it) },
                )
                KeepRunning.Brand.OTHER -> {}
            }

            if (Build.VERSION.SDK_INT >= 30) {
                StepCard(
                    number = if (KeepRunning.brand == KeepRunning.Brand.OTHER) 2 else 3,
                    title = stringResource(R.string.pause_title),
                    detail = stringResource(R.string.pause_detail),
                    done = neverPaused,
                    buttonText = stringResource(R.string.open_setting),
                    onButton = { KeepRunning.openPauseSetting(context) },
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDone,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).height(56.dp),
            ) { Text(stringResource(R.string.done), style = MaterialTheme.typography.titleSmall) }
        }
    }
}

@Composable
private fun BrandStep(title: String, detail: String, byHand: Boolean, done: Boolean, onDoneChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    StepCard(
        number = 2,
        title = title,
        detail = detail,
        done = done,
        // No page to jump to, so the button stops claiming one and opens the phone's own Settings.
        buttonText = stringResource(if (byHand) R.string.open_phone_settings else R.string.open_settings),
        onButton = { KeepRunning.openBrandPage(context) },
    ) {
        // The two answers contradict each other, so ticking one clears the other (M's call,
        // 2026-10-05). Two independent boxes allowed "I did it" and "it didn't work" at once,
        // which says nothing and is exactly the sort of thing an unsure person ticks.
        var failed by remember { mutableStateOf(KeepRunning.pageFailed(context)) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
            Checkbox(
                checked = done,
                onCheckedChange = {
                    onDoneChange(it)
                    if (it) { failed = false; KeepRunning.setPageFailed(context, false) }
                },
            )
            Text(stringResource(R.string.i_did_this), style = MaterialTheme.typography.bodyLarge)
        }
        // The other answer. Before this the only two options were to tick a box they hadn't earned
        // or to say nothing, and M's father did the first.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
            Checkbox(
                checked = failed,
                onCheckedChange = {
                    failed = it
                    KeepRunning.setPageFailed(context, it)
                    if (it) onDoneChange(false)
                },
            )
            Text(stringResource(R.string.button_went_nowhere), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    detail: String,
    done: Boolean,
    buttonText: String,
    onButton: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (done) colors.surfaceContainerHigh else colors.surfaceContainerLowest,
        border = if (done) null else BorderStroke(2.dp, colors.primary),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(if (done) MogarColors.FamilyGreen else colors.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.done), tint = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text("$number", style = MaterialTheme.typography.titleSmall, color = colors.onPrimaryContainer)
                    }
                }
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            Text(detail, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            if (done) {
                FilledTonalButton(onClick = onButton, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(buttonText, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = MaterialTheme.typography.labelLarge.fontWeight))
                }
            } else {
                Button(onClick = onButton, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(buttonText, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = MaterialTheme.typography.labelLarge.fontWeight))
                }
            }
            extra()
        }
    }
}
