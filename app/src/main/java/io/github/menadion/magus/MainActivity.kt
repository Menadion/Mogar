package io.github.menadion.magus

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import android.Manifest
import android.app.Activity
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.log2
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnLocationCameraTransitionListener
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineResult
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.Property.ICON_ANCHOR_CENTER
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.rasterOpacity
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

// OpenFreeMap: free map tiles, no key. Swapping to an offline file later changes only this line.
internal const val MAP_STYLE ="https://tiles.openfreemap.org/styles/liberty"

// How long the map may stay blank before the screen offers a reason. See the LaunchedEffect in
// FamilyMap: this never ends the wait, it only explains one that is already happening.
private const val MAP_SLOW_AFTER_MS = 10_000L

// Roughly the middle of the Philippines, zoomed out to show the whole country.
private val PHILIPPINES = LatLng(12.3, 122.5)

// While the app is open, my location goes up at most this often.
private const val SEND_EVERY_MS = 60_000L

private const val FAMILY_SOURCE = "family"
private const val ME_SOURCE = "me"
private const val RAIN_SOURCE = "rain"
private const val RAIN_LAYER = "rain-tiles"
private const val SATELLITE_SOURCE = "rain-satellite"
private const val SATELLITE_LAYER = "rain-satellite-tiles"
private const val COAST_LAYER = "rain-coastline"

// Where the satellite is slid into the style. Liberty's first SymbolLayer is road_one_way_arrow,
// not a place name, so anchoring on "the first label" left one-way arrows floating on the cloud and
// buried the borders. boundary_3 is the lowest of the three boundary layers and sits directly under
// the real labels, so one anchor puts borders and every label above the weather and leaves roads,
// bridges and buildings under it, which is the right side of the line for each.
private const val BOUNDARY_ANCHOR = "boundary_3"

// The source layer the coastline is traced from. Liberty has no coastline layer at all -- the coast
// is only where the water fill stops -- and the boundary layers all filter out maritime edges, so an
// archipelago gets no outline from them. Drawing the water polygons as a line is what puts the
// country's shape back on top of the cloud.
private const val WATER_SOURCE_LAYER = "water"

// Above this zoom the satellite is a stretched smear over somebody's street, and the radar is the
// only one of the two still saying anything useful, so it comes off and the plain map returns.
private const val SATELLITE_HIDE_ABOVE = 10.0f

// Where the camera goes when weather is switched on from closer in (M, 2026-11-23): one level under
// the point where the satellite hides, so the picture is on screen and not at its most stretched.
private const val WEATHER_ZOOM = 9.0

// Two dots touch when their centres are this close, in dp: one dot's width.
private const val DOT_TOUCH_DP = 38f

// Flying to a person zooms in one level from wherever the map is, but never less than this.
private const val STREET_ZOOM = 15.0

// How far from a dot a tap still counts, so small dots are easy to hit.
private const val TAP_REACH_DP = 24f

// First open asks for a name and a family, then the map shows everyone in it.
class MainActivity : ComponentActivity() {
    // The notification tapped while Mogar was already open. Without setIntent the old intent
    // stays, and without the flag the screen has no way to hear about it.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(AutoUpdate.INSTALL_UPDATE, false)) {
            AutoUpdate.installRequested = true
        }
    }

    // Back from Android's settings: the Keep running steps may have changed, and with them the gear's dot.
    override fun onResume() {
        super.onResume()
        // Debug builds only, for testing another brand's card on whatever phone is to hand:
        //   am start -n io.github.menadion.magus/.MainActivity --es brand vivo
        KeepRunning.overrideBrand(this, intent?.getStringExtra("brand"))
        KeepRunning.refresh(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super: the window's background is drawn from the manifest theme before Compose
        // has a frame, and that theme follows the phone, not the in-app setting. A forced Light or
        // Dark swaps it here, so a Dark phone no longer flashes white on a cold start.
        ThemeSetting.load(this)
        when (ThemeSetting.mode) {
            ThemeSetting.DARK -> setTheme(R.style.Theme_Mogar_Dark)
            ThemeSetting.LIGHT -> setTheme(R.style.Theme_Mogar_Light)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapLibre.getInstance(this)
        Diagnostics.noteAppOpened(this)
        GroupRadius.load(this)
        Rain.load(this)
        Updates.load(this)
        // The check that runs whether or not anyone opens Mogar again. KEEP inside, so this does
        // not restart the day's clock every time the app is opened.
        AutoUpdate.schedule(this)
        if (intent?.getBooleanExtra(AutoUpdate.INSTALL_UPDATE, false) == true) {
            AutoUpdate.installRequested = true
        }
        setContent {
            MogarTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var code by remember { mutableStateOf(Family.savedCode(this)) }
                    val familyCode = code
                    if (familyCode == null) {
                        SetupScreen(onDone = { code = Family.savedCode(this) })
                    } else {
                        FamilyScreen(familyCode, onLeft = { code = null })
                    }
                }
            }
        }
    }
}

// The map, the top strip with the sharing switch, and the steps that let sharing run in the background.
@Composable
fun FamilyScreen(code: String, onLeft: () -> Unit) {
    val context = LocalContext.current
    // Read straight from Family, not remembered here, so the pill follows a change made anywhere:
    // a declined location box, or the phone's own location switch flipping while Mogar is open.
    val sharing = Family.isSharing(context)
    // True between tapping the pill on and the phone answering: it decides whether that answer
    // commits sharing on, or only records that they wanted it (see onLocationSettled).
    var pillWantsOn by remember { mutableStateOf(false) }
    var explainBackground by remember { mutableStateOf(false) }
    // Bumped to ask the map's permission-then-location-switch flow to run again (see FamilyMap).
    var locationAsk by remember { mutableIntStateOf(0) }

    var members by remember { mutableStateOf(emptyList<Member>()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedUid by remember { mutableStateOf<String?>(null) }
    // Show everyone stays up from the first pick until it's tapped; closing a card leaves the map
    // where it is (M's call, 2026-09-26). Each tap bumps fitRequest, which the map answers.
    var zoomedIn by remember { mutableStateOf(false) }
    var fitRequest by remember { mutableIntStateOf(0) }
    // Bumped each time the weather switch goes ON, so the map can pull in from a zoom where the
    // satellite is hidden (see WEATHER_ZOOM). Only a tap bumps it: opening the app with weather
    // already on leaves the camera alone.
    var weatherZoomAsk by remember { mutableIntStateOf(0) }
    // Saveable so a rotation, or the system reclaiming Mogar while they are in the phone's own
    // settings pages, brings them back to the same screen rather than the map.
    var showKeepRunning by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showGroupRadius by rememberSaveable { mutableStateOf(false) }
    // Where the main map is looking, for the radius window when this phone has no position to show.
    var lookingAt by remember { mutableStateOf(PHILIPPINES) }
    var showFamilyPage by rememberSaveable { mutableStateOf(false) }
    var showWhatsNew by remember { mutableStateOf(WhatsNew.dueNow(context)) }
    var showTutorial by rememberSaveable { mutableStateOf(Tutorial.dueNow(context)) }
    // Asked before anything else is shown, and before any diagnostics are sent (see Consent).
    var showConsent by remember { mutableStateOf(Consent.dueNow(context)) }
    var familyName by remember { mutableStateOf(Family.savedFamilyName(context)) }
    var familyCreator by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var me by remember { mutableStateOf<Member?>(null) }
    var showList by remember { mutableStateOf(false) }
    // Heights of the floating cards, so the camera can aim at the gap between them.
    var topHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }
    val myUid = FirebaseAuth.getInstance().currentUser?.uid

    // Starts background sharing. The first time, also shows how to keep the phone from closing Mogar.
    fun startSharing() {
        ShareService.start(context)
        if (!KeepRunning.introShown(context)) {
            KeepRunning.markIntroShown(context)
            showKeepRunning = true
        }
    }

    // Asks this phone what its brand's settings pages are really called, once per Mogar version,
    // and only with consent. Keyed on showConsent so it runs the moment the box is answered yes,
    // rather than waiting for the next launch (see Probe).
    LaunchedEffect(showConsent) {
        if (!showConsent) Probe.run(context)
    }

    // Everyone else in the family, live. The map and the card both read this one list.
    DisposableEffect(code) {
        val registration = Family.listen(context) { list ->
            members = list.filter { it.uid != myUid }
            me = list.find { it.uid == myUid }
            me?.let { Family.cachePhoto(context, it.photo) }
        }
        val nameWatch = Family.listenFamily(context) { name, createdBy ->
            if (name != null) familyName = name
            familyCreator = createdBy
        }
        onDispose {
            registration?.remove()
            nameWatch?.remove()
        }
    }

    // The toggle notice fades after a moment. 3 seconds, not the 1.5 it was: the notice now also
    // carries why sharing switched itself off, which is a sentence rather than a confirmation, and
    // it is the only place that says so.
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(3000)
            notice = null
        }
    }

    // Once a day, asks GitHub whether a newer Mogar exists (the red dot on the gear).
    LaunchedEffect(Unit) { Updates.checkDaily(context) }

    // Arriving from the "ready to install" notification: hand the file straight to Android's
    // installer, which is the one tap nobody can take for them. Anything not in place yet (the
    // download gone, or "Install unknown apps" never granted) falls back to Settings, where the
    // update row already walks the whole thing through rather than failing silently.
    LaunchedEffect(AutoUpdate.installRequested) {
        if (!AutoUpdate.installRequested) return@LaunchedEffect
        AutoUpdate.installRequested = false
        val release = Updates.newer
        if (release != null && Updates.isReady(context, release) && Updates.canInstall(context)) {
            Updates.install(context, release)
        } else {
            showSettings = true
        }
    }

    // Recheck every 30 seconds, so a dot goes hollow even when no new update arrives.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }

    val askBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startSharing() }

    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) startSharing()
        else explainBackground = true
    }

    // Once location is allowed and switched on: notifications, then "Allow all the time", then start sharing.
    fun startSharingSteps() {
        if (!Family.isSharing(context)) return
        when {
            Build.VERSION.SDK_INT >= 33 && !isGranted(context, Manifest.permission.POST_NOTIFICATIONS) ->
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            !isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> explainBackground = true
            else -> startSharing()
        }
    }

    // Everyone on the screens: you first, then the family in a fixed order.
    // The fallback is what my own screens show before any location of mine has been sent: the
    // family record of mine may not exist yet, so me is null then. It reads the picture
    // from this phone's own copy, which is what my dot on the map already did — without it, the
    // profile page lost my picture whenever my location was off (M's question, 2026-10-11).
    val meNow = (me ?: Member(
        uid = myUid ?: "me",
        name = Family.savedName(context) ?: context.getString(R.string.you),
        lat = null,
        lng = null,
        battery = null,
        updatedAtMillis = null,
        sharing = sharing,
        photo = Family.savedPhoto(context),
        phone = Family.savedPhone(context),
    )).copy(sharing = sharing)
    val personColors = PersonColors.assign(members.map { it.uid } + listOfNotNull(myUid))
    val people = listOf(Person(meNow, isYou = true, youLabel = context.getString(R.string.you))) +
        members.sortedBy { it.name.lowercase() }.map { Person(it, isYou = false, color = personColors.getValue(it.uid)) }

    // The map and the edge markers draw one dot per profile, not one per phone (see profileMembers).
    val shown = profileMembers(members, me?.personId)
    val shownPeople = shown.map { Person(it, isYou = false, color = personColors.getValue(it.uid)) }

    // Where each member's dot sits on screen, and the screen's size, for the edge markers.
    // Held as State, not read here, so a camera frame redraws the edge markers and not this whole screen.
    val dotPoints = remember { mutableStateOf(emptyList<MapSpot>()) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { screenSize = it }) {
        FamilyMap(
            members = shown,
            colors = personColors,
            onProjected = { dotPoints.value = it },
            me = me,
            now = now,
            sharing = sharing,
            selectedUid = selectedUid,
            paddingTop = topHeight,
            paddingBottom = { bottomHeight },
            onNotice = { notice = it },
            fitRequest = fitRequest,
            weatherZoomAsk = weatherZoomAsk,
            locationAsk = locationAsk,
            // What the phone answered about reporting a position, either when Mogar opened or
            // because the pill was tapped on. This is the one place the two switches are tied
            // together: the pill is never on while the phone would refuse.
            onLocationSettled = { allowed ->
                val asked = pillWantsOn
                pillWantsOn = false
                when {
                    // They asked for it. The phone agreed, so honour it.
                    allowed && asked -> {
                        Family.setSharing(context, true)
                        startSharingSteps()
                        notice = context.getString(R.string.sharing_notice_on)
                    }
                    // Nobody asked; the phone can report again and Mogar was what switched
                    // sharing off, so put back what they last chose and say it is back.
                    allowed -> {
                        if (Family.resumeAfterPhone(context)) {
                            notice = context.getString(R.string.sharing_notice_resumed)
                        }
                        startSharingSteps()
                    }
                    // Refused. Sharing goes off, or stays off with their wish recorded, and the
                    // notice is the only thing that says the phone is the reason.
                    else -> {
                        Family.pauseForPhone(context, wantedOn = asked)
                        notice = context.getString(R.string.sharing_notice_phone_off)
                    }
                }
            },
            // A tap on empty map (uid null) closes whatever the panel shows.
            onDotTapped = { uid ->
                showList = false
                selectedUid = uid
            },
            people = people,
            onGroupPick = {
                showList = false
                selectedUid = it
            },
            onZoomedIn = { zoomedIn = true },
            onRested = { lookingAt = it },
            // Family whose dots are off the visible map, pinned to its edge and pointing at them.
            // Drawn inside the map's layer so a group's bubble can sit above them.
            edgeMarkers = { openGroup ->
                EdgeLayer(
                    people = shownPeople,
                    now = now,
                    spots = dotPoints,
                    area = Rect(0f, topHeight.toFloat(), screenSize.width.toFloat(), (screenSize.height - bottomHeight).toFloat()),
                    screen = Rect(0f, 0f, screenSize.width.toFloat(), screenSize.height.toFloat()),
                    onPick = {
                        showList = false
                        selectedUid = it
                    },
                    onGroup = openGroup,
                )
            },
        )

        // Bottom: the family box on the screen's bottom edge, or in its place the panel: the
        // person's card while someone is picked, the list after See all. Opening one slides the
        // family box down and the panel up into the same spot, together; closing slides them back
        // (M's call, 2026-09-26, replacing the panel that rose out of the box). The panel keeps
        // showing the last picked person while it slides away.
        val selected = shownPeople.find { it.uid == selectedUid } ?: people.find { it.uid == selectedUid }
        var shown by remember { mutableStateOf<Person?>(null) }
        if (selected != null) {
            shown = selected
            zoomedIn = true
        }
        val panelOpen = showList || selected != null
        fun closePanel() {
            showList = false
            selectedUid = null
        }
        BackHandler(enabled = panelOpen) { closePanel() }
        val familyRowScroll = rememberLazyListState() // kept while the box is away, for big families
        Box(modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { bottomHeight = it.height }) {
            AnimatedVisibility(
                visible = !panelOpen,
                enter = slideInVertically(tween(300)) { it },
                exit = slideOutVertically(tween(300)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                FamilyRow(
                    people = people,
                    now = now,
                    scroll = familyRowScroll,
                    onPick = {
                        showList = false
                        selectedUid = it
                    },
                    onSeeAll = {
                        selectedUid = null
                        showList = true
                    },
                )
            }
            AnimatedVisibility(
                visible = panelOpen,
                enter = slideInVertically(tween(300)) { it },
                exit = slideOutVertically(tween(300)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomPanel(onClose = { closePanel() }) {
                    Crossfade(targetState = showList, label = "panel", modifier = Modifier.fillMaxWidth()) { list ->
                        if (list) Box(modifier = Modifier.height(listPanelHeight())) {
                            FamilyList(
                                people = people,
                                now = now,
                                onPick = {
                                    showList = false
                                    selectedUid = it
                                },
                                onClose = { showList = false },
                            )
                        } else {
                            shown?.let { MemberCard(person = it, now = now, onClose = { selectedUid = null }) }
                        }
                    }
                }
            }
        }

        // Top: the family card, and Show everyone under it while someone is picked.
        Column(modifier = Modifier.align(Alignment.TopCenter)) {
            Box(modifier = Modifier.onSizeChanged { topHeight = it.height }) {
                FamilyStrip(
                    code = code,
                    familyName = familyName,
                    memberCount = members.size + 1,
                    sharing = sharing,
                    // Off is theirs to give and takes effect at once. On is a request: the pill
                    // cannot be on unless the phone will report a position, so it asks first and
                    // the answer decides (see onLocationSettled). Nothing is committed here.
                    onToggle = {
                        if (sharing) {
                            Family.setSharing(context, false)
                            ShareService.stop(context)
                            notice = context.getString(R.string.sharing_notice_off)
                        } else {
                            pillWantsOn = true
                            locationAsk++
                        }
                    },
                    needsAttention = Updates.newer != null || !KeepRunning.allDone,
                    weatherOn = Rain.on,
                    onWeather = {
                        if (!Rain.on) weatherZoomAsk++
                        Rain.set(context, !Rain.on)
                    },
                    onSettings = { showSettings = true },
                    onFamily = { showFamilyPage = true },
                )
            }
            AnimatedVisibility(visible = zoomedIn) {
                ShowEveryoneButton(
                    onClick = {
                        selectedUid = null
                        zoomedIn = false
                        fitRequest++
                    },
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                )
            }
        }

        SharingNotice(text = notice, modifier = Modifier.align(Alignment.Center))

        if (showFamilyPage) {
            BackHandler { showFamilyPage = false }
            FamilyPage(
                code = code,
                canRename = familyCreator != null && familyCreator == myUid,
                people = people,
                now = now,
                onBack = { showFamilyPage = false },
                onPick = {
                    showFamilyPage = false
                    selectedUid = it
                },
                onLeft = {
                    showFamilyPage = false
                    onLeft()
                },
            )
        }

        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(
                onBack = { showSettings = false },
                onKeepRunning = { showKeepRunning = true },
                onTutorial = { showTutorial = true },
                onGroupRadius = { showGroupRadius = true },
            )
        }

        if (showGroupRadius) {
            val lat = me?.lat
            val lng = me?.lng
            GroupRadiusDialog(
                current = GroupRadius.meters,
                here = if (lat != null && lng != null) LatLng(lat, lng) else null,
                lookingAt = lookingAt,
                sharing = sharing,
                onSave = {
                    GroupRadius.set(context, it)
                    showGroupRadius = false
                },
                onClose = { showGroupRadius = false },
            )
        }

        if (showKeepRunning) {
            BackHandler { showKeepRunning = false }
            KeepRunningScreen(onDone = { showKeepRunning = false })
        }

        // The short tour: once after setup, after the Keep running steps; again from Settings.
        if (showTutorial && !showKeepRunning && !explainBackground && !showConsent) {
            val close = { showTutorial = false; Tutorial.markSeen(context) }
            BackHandler(onBack = close)
            TutorialScreen(onDone = close)
        }
    }

    // Asked once, ahead of What's new and the tour, so it isn't buried under them. Nothing is sent
    // until it's answered, so there is no back key and no tapping outside.
    if (showConsent) {
        ConsentDialog(onAnswered = { showConsent = false })
    }

    // After an update, once the map itself is showing: never over another screen or box.
    if (showWhatsNew && !showSettings && !showKeepRunning && !showFamilyPage && !explainBackground && !showTutorial && !showConsent) {
        WhatsNewDialog(onClose = {
            showWhatsNew = false
            WhatsNew.markSeen(context)
        })
    }

    if (explainBackground) {
        // "Not now" still shares, but only after Mogar has been opened since the phone last restarted.
        AlertDialog(
            onDismissRequest = {
                explainBackground = false
                startSharing()
            },
            title = { Text(stringResource(R.string.background_title)) },
            text = {
                Text(stringResource(R.string.background_text))
            },
            confirmButton = {
                TextButton(onClick = {
                    explainBackground = false
                    askBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text(stringResource(R.string.continue_label)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    explainBackground = false
                    startSharing()
                }) { Text(stringResource(R.string.not_now)) }
            },
        )
    }
}

private fun isGranted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
fun FamilyMap(
    members: List<Member>,
    colors: Map<String, Int>, // each member's own colour, from PersonColors
    onProjected: (List<MapSpot>) -> Unit, // where each dot sits on screen, after every camera move
    me: Member?,
    now: Long,
    sharing: Boolean,
    selectedUid: String?,
    paddingTop: Int,
    paddingBottom: () -> Int, // read when the camera moves, so a panel that just opened counts
    fitRequest: Int, // goes up by one each time Show everyone is tapped
    weatherZoomAsk: Int, // goes up by one each time weather is switched on
    locationAsk: Int, // goes up by one each time the sharing pill asks the phone again
    onLocationSettled: (allowed: Boolean) -> Unit, // true when this phone will report a position
    onDotTapped: (String?) -> Unit,
    people: List<Person>, // everyone including me, for the avatars in a group's bubble
    onGroupPick: (String) -> Unit, // an avatar in the bubble was tapped
    onZoomedIn: () -> Unit, // the camera was sent in on a group, so Show everyone belongs on screen
    onRested: (LatLng) -> Unit, // where the camera stopped, for the group radius window's fallback
    onNotice: (String) -> Unit, // a line for the band across the map, same one the sharing switch uses
    edgeMarkers: @Composable (openGroup: (List<String>) -> Unit) -> Unit, // drawn over the map and under a group's bubble
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnDotTapped by rememberUpdatedState(onDotTapped)
    val currentOnRested by rememberUpdatedState(onRested)

    var hasLocation by remember {
        mutableStateOf(
            isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var myLocation by remember { mutableStateOf<Location?>(null) }

    // Google's "Turn on device location?" box. Its answer used to be thrown away, which is how the
    // pill could stay on over a phone that had just refused: RESULT_OK means they turned location
    // on, anything else means they said no and nothing will be sent.
    val askTurnOn = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> onLocationSettled(result.resultCode == Activity.RESULT_OK) }

    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        hasLocation = granted.values.any { it }
        // Refusing the permission is the same answer as refusing the box: this phone will not
        // report a position, so sharing cannot claim to be on.
        if (hasLocation) checkLocationIsOn(context, { askTurnOn.launch(it) }, onLocationSettled)
        else onLocationSettled(false)
    }

    // The phone's location switch, flipped while they are looking at the map — from the quick
    // settings shade, or by another app. Same answer, same decision code as the other two ways in.
    val settled by rememberUpdatedState(onLocationSettled)
    DisposableEffect(Unit) {
        val receiver = PhoneLocation.watchSwitch(context) { canReport -> settled(canReport) }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    // Permission first, then the phone's location switch, then sharing. Runs when the app opens,
    // and again each time the sharing pill is tapped on, which is what keeps the pill and the
    // phone's own switch from disagreeing.
    LaunchedEffect(locationAsk) {
        if (!hasLocation) {
            askLocation.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        } else {
            checkLocationIsOn(context, { askTurnOn.launch(it) }, onLocationSettled)
        }
    }

    // Who is standing together. Worked out when the family changes or the map comes to rest, never
    // on every frame of a drag: the A13 would pay for it, and a group splitting mid-pinch would flicker.
    var restedAt by remember { mutableIntStateOf(0) }
    var groups by remember { mutableStateOf(emptyList<List<String>>()) }
    var joined by remember { mutableStateOf(emptySet<String>()) }

    // The group whose bubble is open: who was in it when it was tapped. It stays open only while a
    // group holding all of them still exists (see the grouping effect), so a zoom that pulls an
    // overlap-only group apart closes it. bubbleTap goes up on each tap that should fly the camera in.
    var bubbleUids by remember { mutableStateOf<List<String>?>(null) }
    var bubbleTap by remember { mutableIntStateOf(0) }
    var camTick by remember { mutableIntStateOf(0) } // goes up as the camera moves, so the bubble follows its spot
    val currentOnGroupTapped by rememberUpdatedState { uids: List<String> -> bubbleUids = uids; bubbleTap++ }

    // Reports where each dot sits on screen, in pixels, for the edge markers: one per person, and one
    // per group, which is drawn as a single dot.
    val latestMembers by rememberUpdatedState(members)
    val latestGroups by rememberUpdatedState(groups)
    val latestNow by rememberUpdatedState(now)
    val latestMe by rememberUpdatedState(me)
    val latestLocation by rememberUpdatedState(myLocation)
    val latestOnProjected by rememberUpdatedState(onProjected)
    fun project(m: MapLibreMap) {
        fun at(p: LatLng) = m.projection.toScreenLocation(p).let { Offset(it.x, it.y) }
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        val grouped = latestGroups.flatten().toSet()
        val singles = latestMembers.filter { it.uid !in grouped }.mapNotNull { member ->
            val lat = member.lat ?: return@mapNotNull null
            val lng = member.lng ?: return@mapNotNull null
            MapSpot(member.uid, listOf(member.uid), at(LatLng(lat, lng)))
        }
        val byUid = latestMembers.associateBy { it.uid }
        val crowds = latestGroups.mapNotNull { uids ->
            val others = uids.mapNotNull { byUid[it] }
            if (others.isEmpty()) return@mapNotNull null
            val centre = groupCentre(uids, latestMembers, myUid, whereAmI(latestLocation, latestMe)) ?: return@mapNotNull null
            MapSpot(groupFace(others, latestNow).uid, uids, at(centre))
        }
        latestOnProjected(singles + crowds)
    }
    LaunchedEffect(map, members, groups) { map?.let { project(it) } }

    // Rain on the map, refreshed for as long as it is switched on. The layer goes under the
    // family dots so nobody is painted over, and is taken out again when switched off so its
    // tiles stop being held. A failed fetch leaves whatever frame is already up and tries again.
    //
    // Three things belong to the FIRST attempt after switching on, and to no later one (M, 2026-11-23):
    // the dim over the map while it loads, the message when it could not, and the message when the
    // map is looking somewhere no weather picture reaches. A retry or an 8-minute refresh is silent.
    // The dim ends when the map reports a frame with the weather's tiles on it, or after ten seconds,
    // whichever comes first; a slow link past that just gets the picture later, unannounced.
    //
    // The whole loop sits inside repeatOnLifecycle, so a backgrounded Mogar stops asking RainViewer
    // and takes one fresh frame on coming back. Before this it polled for as long as the process lived.
    var weatherLoading by remember { mutableStateOf(false) }
    var renderedFully by remember { mutableIntStateOf(0) } // goes up on every fully drawn frame
    val currentOnNotice by rememberUpdatedState(onNotice)
    LaunchedEffect(style, Rain.on) {
        val s = style ?: return@LaunchedEffect
        if (!Rain.on) {
            removeRain(s)
            weatherLoading = false
            return@LaunchedEffect
        }
        var firstAttempt = true
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                while (true) {
                    val loud = firstAttempt
                    firstAttempt = false
                    val cap = if (loud) {
                        weatherLoading = true
                        launch { delay(Rain.LOAD_CAP_MS); weatherLoading = false }
                    } else null

                    val tiles = withContext(Dispatchers.IO) { runCatching { Rain.latest(context) }.getOrNull() }
                    when {
                        tiles == null -> if (loud) currentOnNotice(context.getString(R.string.weather_failed))
                        s.getLayer(RAIN_LAYER) == null -> {
                            val seen = renderedFully
                            showRain(s, tiles)
                            if (loud) withTimeoutOrNull(Rain.LOAD_CAP_MS) { snapshotFlow { renderedFully }.first { it != seen } }
                        }
                        else -> swapRadar(s, tiles)
                    }
                    cap?.cancel()
                    weatherLoading = false
                    if (loud && tiles != null) {
                        val lng = map?.cameraPosition?.target?.longitude
                        if (lng != null && !Rain.satelliteCovers(lng)) currentOnNotice(context.getString(R.string.weather_no_coverage))
                    }
                    delay(Rain.REFRESH_MS)
                }
            } finally {
                weatherLoading = false
            }
        }
    }

    // What the map area shows until the map is really there. MapLibre answers both questions
    // itself: did a frame finish with its tiles on it, and did loading actually fail. Nothing here
    // is a fixed delay, so the indicator lasts as long as this phone needs and no longer.
    val loadState = remember { mutableStateOf(MapLoadState.LOADING) }

    val meGrouped = groups.any { g -> FirebaseAuth.getInstance().currentUser?.uid in g }

    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            // fully is the point: this fires on partial renders too, and hiding on the first of
            // them would put the indicator away while the map was still half grey.
            addOnDidFinishRenderingMapListener { fully ->
                if (fully) {
                    loadState.value = MapLoadState.DONE
                    renderedFully++
                }
            }
            addOnDidFailLoadingMapListener { loadState.value = MapLoadState.FAILED }
            getMapAsync { m ->
                m.cameraPosition = CameraPosition.Builder().target(PHILIPPINES).zoom(4.8).build()
                // No further out than a world as tall as the screen; past that, blank shows below
                // Antarctica. MapLibre draws the world 512 dp across at zoom 0.
                val screenDp = context.resources.displayMetrics.run { heightPixels / density }
                m.setMinZoomPreference(log2(screenDp / 512.0))
                // The screen's centre can't cross the date line, so panning sideways ends instead of
                // looping round the world forever (M's option A, 2026-09-26, until a globe exists).
                m.setLatLngBoundsForCameraTarget(LatLngBounds.world())
                m.addOnCameraMoveListener { project(m); camTick++ }
                m.addOnCameraIdleListener { project(m); restedAt++; currentOnRested(m.cameraPosition.target ?: PHILIPPINES) }
                // Tilting pulls the blank beyond the poles into view, and nothing here needs it (M's call, 2026-11-02).
                m.uiSettings.isTiltGesturesEnabled = false
                m.setMaxPitchPreference(0.0)
                m.uiSettings.isCompassEnabled = false
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.isAttributionEnabled = false // credit lives under ⋮ > About the map
                m.setStyle(Style.Builder().fromUri(MAP_STYLE)) { s ->
                    addFamilyLayers(s)
                    map = m
                    style = s
                }
                // A tap near a dot opens its card; a tap anywhere else closes it.
                val reach = TAP_REACH_DP * context.resources.displayMetrics.density
                m.addOnMapClickListener { point ->
                    val at = m.projection.toScreenLocation(point)
                    val hit = m.queryRenderedFeatures(
                        RectF(at.x - reach, at.y - reach, at.x + reach, at.y + reach),
                        "family-dots",
                    ).firstOrNull()
                    val crowd = hit?.getStringProperty("group")
                    if (crowd != null) {
                        currentOnDotTapped(null) // a card that was open makes way
                        currentOnGroupTapped(crowd.split(","))
                    } else {
                        bubbleUids = null
                        currentOnDotTapped(hit?.getStringProperty("uid"))
                    }
                    hit != null
                }
            }
        }
    }

    // The map view needs to hear when the screen starts, pauses and closes.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Once the map is loaded and location is allowed, put the blue dot on, follow it, and share it.
    DisposableEffect(map, style, hasLocation) {
        val m = map
        val s = style
        val stop = if (m != null && s != null && hasLocation) showMyLocation(context, m, s, hidden = { meGrouped }) { myLocation = it } else null
        onDispose { stop?.invoke() }
    }

    // My own dot follows my sharing switch: blue, or grey while paused.
    LaunchedEffect(map, style, hasLocation, sharing, Photos.key(me?.photo), meGrouped) {
        val s = style ?: return@LaunchedEffect
        val component = map?.locationComponent ?: return@LaunchedEffect
        if (!component.isLocationComponentActivated) return@LaunchedEffect
        component.lastKnownLocation?.let { drawMe(context, s, it, sharing, meGrouped) }
    }

    // Redraw the family whenever the list changes, and on each 30-second recheck.
    // Where I am: a live fix first, else what my own record last said.
    fun myPlace() = whereAmI(myLocation, me)
    LaunchedEffect(map, members, selectedUid, restedAt, myLocation, me, GroupRadius.meters) {
        val m = map ?: return@LaunchedEffect
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        val touch = DOT_TOUCH_DP * context.resources.displayMetrics.density
        fun spot(uid: String, at: LatLng) = m.projection.toScreenLocation(at).let { Grouping.Spot(uid, at.latitude, at.longitude, it.x, it.y) }
        // The person picked comes out of their group and is drawn on their own; the rest stay grouped.
        val spots = members.filter { it.uid != selectedUid && it.lat != null && it.lng != null }
            .map { spot(it.uid, LatLng(it.lat!!, it.lng!!)) } +
            listOfNotNull(if (myUid != null && myUid != selectedUid) myPlace()?.let { spot(myUid, it) } else null)
        val result = Grouping.group(spots, GroupRadius.meters.toDouble(), touch, joined)
        groups = result.groups.filter { it.size > 1 }
        joined = result.joined
        // The bubble goes with its group: pulled apart by a zoom, or emptied by someone leaving.
        bubbleUids?.let { open -> if (groups.none { it.containsAll(open) }) bubbleUids = null }
    }

    // Choosing a person (from the map, the family box, or the bubble itself), or Show everyone,
    // puts the bubble away: the card or the wide view takes over.
    LaunchedEffect(selectedUid) { if (selectedUid != null) bubbleUids = null }
    LaunchedEffect(fitRequest) { bubbleUids = null }
    BackHandler(enabled = bubbleUids != null) { bubbleUids = null }

    // Redraw the family whenever the list or the groups change, and on each 30-second recheck.
    LaunchedEffect(style, members, now, selectedUid, groups) {
        style?.let { showFamily(context, it, members, colors, now, selectedUid, groups, myPlace()) }
    }

    // Picking a person flies the camera to them, aimed at the gap between the top card and the
    // member card. Closing the card leaves the camera there; only Show everyone zooms back out to
    // fit everyone (M's change, 2026-09-26, to HANDOFF.md section 4).
    val density = context.resources.displayMetrics.density
    val side = (12 * density).toInt()
    val tagRoom = (60 * density).toInt() // the name tag sits above the dot
    fun mine() = myPlace()
    LaunchedEffect(map, selectedUid) {
        val m = map ?: return@LaunchedEffect
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        val mine = mine()
        if (selectedUid != null) {
            val target = if (selectedUid == myUid) mine else members.find { it.uid == selectedUid }?.let { mm -> mm.lat?.let { LatLng(it, mm.lng ?: return@let null) } }
            if (target == null) return@LaunchedEffect
            // The map stops following my phone once a person is picked.
            if (m.locationComponent.isLocationComponentActivated) m.locationComponent.cameraMode = CameraMode.NONE
            m.cancelTransitions()
            // Wait one frame, so the card that opens with this pick has been measured into paddingBottom.
            withFrameNanos { }
            val zoom = maxOf(m.cameraPosition.zoom + 1, STREET_ZOOM)
            m.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(target)
                        .zoom(zoom)
                        .padding(0.0, (paddingTop + tagRoom).toDouble(), 0.0, paddingBottom().toDouble())
                        .build()
                ),
                700,
            )
        }
    }

    // Tapping a group flies in on it, as close as street level and no closer, and its bubble opens
    // over it. Already closer than that, the camera stays at its zoom. An overlap-only group that
    // the zoom pulls apart loses its bubble in the grouping effect, which is the zoom doing the job.
    LaunchedEffect(map, bubbleTap) {
        val m = map ?: return@LaunchedEffect
        val open = bubbleUids ?: return@LaunchedEffect
        val target = groupCentre(open, members, FirebaseAuth.getInstance().currentUser?.uid, myPlace()) ?: return@LaunchedEffect
        if (m.locationComponent.isLocationComponentActivated) m.locationComponent.cameraMode = CameraMode.NONE
        m.cancelTransitions()
        onZoomedIn()
        withFrameNanos { }
        m.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(target)
                    .zoom(maxOf(m.cameraPosition.zoom, STREET_ZOOM))
                    .padding(0.0, (paddingTop + tagRoom).toDouble(), 0.0, paddingBottom().toDouble())
                    .build()
            ),
            700,
        )
    }

    // Switching weather on from closer than WEATHER_ZOOM would show nothing (the satellite is hidden
    // above SATELLITE_HIDE_ABOVE), so the camera pulls out to WEATHER_ZOOM, keeping where it is
    // looking. Already at or past that, it stays put.
    LaunchedEffect(map, weatherZoomAsk) {
        val m = map ?: return@LaunchedEffect
        if (weatherZoomAsk == 0) return@LaunchedEffect
        if (m.cameraPosition.zoom > WEATHER_ZOOM) {
            m.animateCamera(CameraUpdateFactory.zoomTo(WEATHER_ZOOM), 700)
        }
    }

    LaunchedEffect(map, fitRequest) {
        val m = map ?: return@LaunchedEffect
        if (fitRequest == 0) return@LaunchedEffect
        val points = members.mapNotNull { mm -> mm.lat?.let { LatLng(it, mm.lng ?: return@let null) } } + listOfNotNull(mine())
        when {
            points.size >= 2 -> m.animateCamera(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.Builder().includes(points).build(),
                    side + tagRoom, paddingTop + tagRoom, side + tagRoom, paddingBottom() + side,
                ),
                700,
            )
            points.size == 1 -> m.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(points[0])
                        .zoom(STREET_ZOOM)
                        .padding(0.0, paddingTop.toDouble(), 0.0, paddingBottom().toDouble())
                        .build()
                ),
                700,
            )
        }
    }

    // The third outcome, and the only place a clock belongs: the network is alive enough not to
    // fail and too slow to draw, so neither listener above ever fires. Without this the ripple
    // would spread forever with nothing said. Ten seconds, because mobile data on a budget phone
    // can honestly take that long before anything is wrong.
    LaunchedEffect(loadState.value) {
        if (loadState.value == MapLoadState.LOADING) {
            delay(MAP_SLOW_AFTER_MS)
            if (loadState.value == MapLoadState.LOADING) loadState.value = MapLoadState.SLOW
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        MapLoadingOverlay(loadState.value)
        WeatherScrim(weatherLoading)
        edgeMarkers { uids ->
            currentOnDotTapped(null) // a card that was open makes way
            currentOnGroupTapped(uids)
        }
        val open = bubbleUids?.let { u -> groups.firstOrNull { g -> g.containsAll(u) } }
        val m = map
        val centre = if (open != null) groupCentre(open, members, FirebaseAuth.getInstance().currentUser?.uid, myPlace()) else null
        if (open != null && m != null && centre != null) {
            val inBubble = people.filter { it.uid in open }.sortedWith(compareBy({ !it.isYou }, { it.name.lowercase() }))
            BubbleLayer(
                spot = { camTick.let { m.projection.toScreenLocation(centre) }.let { Offset(it.x, it.y) } },
                people = inBubble,
                now = now,
                onPick = onGroupPick,
            )
        }
    }
}

// Reads the dot positions here, in its own scope, so a camera frame redraws only the edge markers.
@Composable
private fun EdgeLayer(
    people: List<Person>, now: Long, spots: State<List<MapSpot>>, area: Rect, screen: Rect,
    onPick: (String) -> Unit, onGroup: (List<String>) -> Unit,
) = EdgeMarkers(people, now, spots.value, area, screen, onPick, onGroup)

// Its own function so that the bubble following the camera redraws only itself, not the whole map screen.
@Composable
private fun BubbleLayer(spot: () -> Offset, people: List<Person>, now: Long, onPick: (String) -> Unit) {
    GroupBubble(people = people, now = now, anchor = spot(), onPick = onPick)
}

// Where I am: a live fix first, else what my own record last said.
private fun whereAmI(location: Location?, me: Member?): LatLng? =
    location?.let { LatLng(it.latitude, it.longitude) } ?: me?.let { mm -> mm.lat?.let { LatLng(it, mm.lng ?: return@let null) } }

// The middle of a group's people, which is where its one dot is drawn. I count, using where I am.
private fun groupCentre(uids: List<String>, members: List<Member>, myUid: String?, myPlace: LatLng?): LatLng? {
    val byUid = members.associateBy { it.uid }
    val spots = uids.mapNotNull { byUid[it] }.mapNotNull { m -> m.lat?.let { LatLng(it, m.lng ?: return@let null) } } +
        listOfNotNull(if (myUid != null && myUid in uids) myPlace else null)
    if (spots.isEmpty()) return null
    return LatLng(spots.map { it.latitude }.average(), spots.map { it.longitude }.average())
}

// Asks Google whether the phone's location is on. If it's off, shows the box that turns it on and
// leaves the answer to that box; otherwise it answers here.
private fun checkLocationIsOn(
    context: Context,
    showTurnOnBox: (IntentSenderRequest) -> Unit,
    whenSettled: (allowed: Boolean) -> Unit,
) {
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build()
    val settings = LocationSettingsRequest.Builder().addLocationRequest(request).setAlwaysShow(true).build()
    LocationServices.getSettingsClient(context)
        .checkLocationSettings(settings)
        .addOnSuccessListener { whenSettled(true) }
        .addOnFailureListener { e ->
            if (e is ResolvableApiException) {
                showTurnOnBox(IntentSenderRequest.Builder(e.resolution).build())
            } else {
                // Not something a box can fix (no Play services, for one). Ask the phone itself
                // rather than assuming either way, so sharing is judged on what it can really do.
                whenSettled(PhoneLocation.canReport(context))
            }
        }
}

// Turns on the blue dot and sends my location at most once a minute while the app is open.
// Returns a function that stops listening for locations.
@SuppressLint("MissingPermission") // only called after the permission check above
private fun showMyLocation(context: Context, map: MapLibreMap, style: Style, hidden: () -> Boolean, onLocation: (Location) -> Unit): () -> Unit {
    val component = map.locationComponent
    component.activateLocationComponent(
        LocationComponentActivationOptions.builder(context, style)
            .locationComponentOptions(hiddenPuck(context))
            .build()
    )
    component.isLocationComponentEnabled = true
    component.renderMode = RenderMode.NORMAL

    var lastSent = 0L
    fun maybeSend(location: Location) {
        onLocation(location)
        drawMe(context, style, location, Family.isSharing(context), hidden())
        val now = SystemClock.elapsedRealtime()
        if (lastSent == 0L || now - lastSent >= SEND_EVERY_MS) {
            lastSent = now
            Family.sendLocation(context, location)
        }
    }

    // Zoom in only once a real location arrives. With location off, the map stays on the whole country.
    // A zoom asked for while the camera is still moving onto me gets ignored, so wait for it to arrive.
    var zoomed = false
    val known = component.lastKnownLocation
    component.setCameraMode(CameraMode.TRACKING, object : OnLocationCameraTransitionListener {
        override fun onLocationCameraTransitionFinished(cameraMode: Int) {
            if (known != null && !zoomed) {
                zoomed = true
                component.zoomWhileTracking(15.0)
            }
        }

        override fun onLocationCameraTransitionCanceled(cameraMode: Int) {}
    })
    known?.let { maybeSend(it) }

    val engine = component.locationEngine ?: return {}
    val onFix = object : LocationEngineCallback<LocationEngineResult> {
        override fun onSuccess(result: LocationEngineResult) {
            val location = result.lastLocation ?: return
            if (!zoomed) {
                zoomed = true
                component.zoomWhileTracking(15.0)
            }
            maybeSend(location)
        }

        override fun onFailure(exception: Exception) {}
    }
    engine.requestLocationUpdates(component.locationEngineRequest, onFix, Looper.getMainLooper())
    return { engine.removeLocationUpdates(onFix) }
}

// The library's own dot stays on, because the map follows it, but it's invisible: it only takes its colour
// once, so it can't turn grey when sharing is paused. My dot is drawn by drawMe instead.
private fun hiddenPuck(context: Context): LocationComponentOptions {
    val clear = android.graphics.Color.TRANSPARENT
    return LocationComponentOptions.builder(context)
        .foregroundTintColor(clear)
        .backgroundTintColor(clear)
        .foregroundStaleTintColor(clear)
        .backgroundStaleTintColor(clear)
        .bearingTintColor(clear)
        .accuracyAlpha(0f)
        .elevation(0f)
        .build()
}

// Adds the picture for one marker to the map's cache if it isn't there yet, and returns its id.
private fun markerImage(
    context: Context,
    style: Style,
    state: Markers.State,
    name: String,
    selected: Boolean,
    you: Boolean = false,
    photo: ByteArray? = null,
    color: Int = MogarColors.FamilyGreen.toArgb(),
    count: Int = 0,
): String {
    val id = Markers.id(state, name, selected, you, photo, color, count)
    if (style.getImage(id) == null) style.addImage(id, Markers.draw(context, state, name, selected, you, photo, color, count))
    return id
}

// My dot: primary while sharing, grey while paused, with a "You" tag above it.
// hidden: I am standing in a group, so the group's dot draws me and this one must not.
private fun drawMe(context: Context, style: Style, location: Location, sharing: Boolean, hidden: Boolean) {
    if (hidden) {
        style.getSourceAs<GeoJsonSource>(ME_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }
    val state = if (sharing) Markers.State.YOU else Markers.State.PAUSED
    val me = Feature.fromGeometry(Point.fromLngLat(location.longitude, location.latitude)).apply {
        addStringProperty(
            "icon",
            markerImage(context, style, state, context.getString(R.string.you), selected = false, you = true, photo = Family.savedPhoto(context)),
        )
    }
    style.getSourceAs<GeoJsonSource>(ME_SOURCE)?.setGeoJson(me)
}

// Puts the whole weather picture on the map: satellite, coastline, radar. Called when it is switched
// on. The 8-minute refresh after that goes through swapRadar and leaves the satellite alone, since a
// satellite URL says "default" and never changes.
private fun showRain(style: Style, tiles: Rain.Tiles) {
    removeRain(style)

    // The satellite tile is opaque, so where it goes in the stack decides what survives it. Slid in
    // under BOUNDARY_ANCHOR it covers the land, water and road work while borders and every label
    // ride on top, which is how a weather picture is meant to read. The fallbacks only matter if
    // Liberty is restyled under us: the layer still lands somewhere sane rather than not at all.
    val anchor = when {
        style.getLayer(BOUNDARY_ANCHOR) != null -> BOUNDARY_ANCHOR
        else -> style.layers.firstOrNull { it is SymbolLayer }?.id ?: "family-dots"
    }
    style.addSource(
        RasterSource(
            SATELLITE_SOURCE,
            TileSet("2.2.0", tiles.satellite).apply { maxZoom = Rain.SATELLITE_MAX_ZOOM },
            256,
        )
    )
    val satellite = RasterLayer(SATELLITE_LAYER, SATELLITE_SOURCE)
    style.addLayerBelow(satellite, anchor)
    // Set after the add, which is the order MapLibre documents for a layer's zoom range.
    satellite.maxZoom = SATELLITE_HIDE_ABOVE

    // The coastline, traced straight off the water polygons already in the vector source the base
    // map is drawn from -- no second download, no second source. Dark rather than white because the
    // backdrop is always satellite imagery here, where the sea reads mid-grey and a cloud top reads
    // white: a dark line is the one colour legible against both. It dies with the satellite, above
    // which it would only be an outline around every pond.
    val vector = style.sources.firstOrNull { it is VectorSource }?.id
    if (vector != null) {
        val coast = LineLayer(COAST_LAYER, vector).withSourceLayer(WATER_SOURCE_LAYER)
        coast.setProperties(lineColor("#1A1A1A"), lineWidth(1.2f), lineOpacity(0.85f))
        style.addLayerAbove(coast, SATELLITE_LAYER)
        coast.maxZoom = SATELLITE_HIDE_ABOVE
    }

    addRadar(style, tiles)
}

// A new radar frame is a new tile URL, not new data at the same one, so the source is rebuilt
// rather than updated. Only the radar: the satellite and the coastline stay where they are.
private fun swapRadar(style: Style, tiles: Rain.Tiles) {
    style.getLayer(RAIN_LAYER)?.let { style.removeLayer(it) }
    style.getSource(RAIN_SOURCE)?.let { style.removeSource(it) }
    addRadar(style, tiles)
}

private fun addRadar(style: Style, tiles: Rain.Tiles) {
    // Capped, or above zoom 7 RainViewer serves its "Zoom Level Not Supported" picture instead of
    // weather -- see Rain.RADAR_MAX_ZOOM. Capped, the zoom-7 tile is stretched instead, so rain
    // still shows on a street-level map, in kilometre-wide blocks.
    style.addSource(
        RasterSource(
            RAIN_SOURCE,
            TileSet("2.2.0", tiles.radar).apply { maxZoom = Rain.RADAR_MAX_ZOOM },
            256,
        )
    )
    style.addLayerAbove(
        RasterLayer(RAIN_LAYER, RAIN_SOURCE).withProperties(rasterOpacity(0.65f)),
        SATELLITE_LAYER,
    )
}

private fun removeRain(style: Style) {
    style.getLayer(RAIN_LAYER)?.let { style.removeLayer(it) }
    style.getSource(RAIN_SOURCE)?.let { style.removeSource(it) }
    // The coastline borrows the base map's own source, so the layer goes and the source stays.
    style.getLayer(COAST_LAYER)?.let { style.removeLayer(it) }
    style.getLayer(SATELLITE_LAYER)?.let { style.removeLayer(it) }
    style.getSource(SATELLITE_SOURCE)?.let { style.removeSource(it) }
}

// Family members are drawn from one list of points, each with its own picture (see Markers).
// Pictures keep their size at every zoom and never hide each other.
private fun addFamilyLayers(style: Style) {
    style.addSource(GeoJsonSource(FAMILY_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        SymbolLayer("family-dots", FAMILY_SOURCE).withProperties(
            iconImage(get("icon")),
            iconAnchor(ICON_ANCHOR_CENTER),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
        )
    )

    // My own dot, drawn last so it sits on top.
    style.addSource(GeoJsonSource(ME_SOURCE, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        SymbolLayer("me-dot", ME_SOURCE).withProperties(
            iconImage(get("icon")),
            iconAnchor(ICON_ANCHOR_CENTER),
            iconAllowOverlap(true),
            iconIgnorePlacement(true),
        )
    )
}

private fun showFamily(
    context: Context, style: Style, members: List<Member>, colors: Map<String, Int>, now: Long, selectedUid: String?,
    groups: List<List<String>>, myPlace: LatLng?,
) {
    val myUid = FirebaseAuth.getInstance().currentUser?.uid
    val grouped = groups.flatten().toSet()
    // Someone with no position yet has a row but no dot.
    val single = members.filter { it.uid !in grouped }.mapNotNull { member ->
        val lat = member.lat ?: return@mapNotNull null
        val lng = member.lng ?: return@mapNotNull null
        Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addStringProperty("uid", member.uid)
            addStringProperty("name", member.name)
            addStringProperty("state", member.dotState(now))
            addStringProperty(
                "icon",
                markerImage(
                    context, style, Markers.state(member, now), member.name, member.uid == selectedUid,
                    photo = member.photo, color = colors.getValue(member.uid),
                ),
            )
        }
    }

    // A group is one dot at the middle of its people: the face's picture with the head count on it.
    // Its uid is the face's, so a tap lands on someone real. I am counted but never the face.
    val byUid = members.associateBy { it.uid }
    val crowds = groups.mapNotNull { uids ->
        val people = uids.mapNotNull { byUid[it] }
        val face = groupFace(people, now)
        val centre = groupCentre(uids, members, myUid, myPlace) ?: return@mapNotNull null
        Feature.fromGeometry(Point.fromLngLat(centre.longitude, centre.latitude)).apply {
            addStringProperty("uid", face.uid)
            addStringProperty("name", face.name)
            addStringProperty("group", uids.joinToString(","))
            addStringProperty(
                "icon",
                markerImage(
                    context, style, Markers.state(face, now), face.name, selected = false,
                    photo = face.photo, color = colors.getValue(face.uid), count = uids.size,
                ),
            )
        }
    }
    style.getSourceAs<GeoJsonSource>(FAMILY_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(single + crowds))
}

// The floating top card, one row: family name and code on the left, then the Sharing pill, the
// weather switch and the gear. M's change from the handoff's two-row card (2026-09-25): it took too
// much map. The weather switch joined the three on the right on 2026-11-16, also his call -- there
// is room in the bar, and a control floating loose on the map "just feels off".
@Composable
fun FamilyStrip(
    code: String,
    familyName: String?,
    memberCount: Int,
    sharing: Boolean,
    weatherOn: Boolean,
    needsAttention: Boolean,
    onToggle: () -> Unit,
    onWeather: () -> Unit,
    onSettings: () -> Unit,
    onFamily: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .statusBarsPadding()
            .padding(start = 12.dp, top = 12.dp, end = 12.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLowest,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The name is the door to the family page, like a chat's header in Messenger.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onFamily)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    Family.familyLabel(LocalContext.current, familyName),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                Text(
                    if (memberCount == 1) stringResource(R.string.member_one) else stringResource(R.string.members_n, memberCount),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = MaterialTheme.typography.bodySmall.fontWeight),
                    color = colors.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .width(1.dp)
                    .height(28.dp)
                    .background(colors.outlineVariant),
            )
            SharingDot(sharing = sharing, onToggle = onToggle)
            // Between the two on purpose: sharing stays first because it is the one that matters,
            // and the gear stays last because that is where a gear goes and it carries the badge.
            WeatherButton(on = weatherOn, onClick = onWeather)
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                Box {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(if (needsAttention) R.string.settings_needs_attention else R.string.settings),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                    // The red dot on the gear's upper-right corner, while a newer Mogar exists or
                    // Keep Mogar running isn't finished. Inside Settings the dot sits on the row to act on.
                    if (needsAttention) {
                        UpdateDot(modifier = Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp))
                    }
                }
            }
        }
    }
}

// The sharing switch beside the gear: a location pin in a blue circle while sharing, grey with a
// slash through it when off. No label; the notice across the map says what just happened.
@Composable
fun SharingDot(sharing: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val sharingDescription = stringResource(if (sharing) R.string.sharing_state_on else R.string.sharing_state_off)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .toggleable(value = sharing, role = Role.Switch, onValueChange = { onToggle() })
            .semantics { contentDescription = sharingDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (sharing) colors.primary else MogarColors.Paused, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // onPrimary is dark navy in the dark scheme, 3.3:1 on the grey; the off state gets its own white.
            val glyph = if (sharing) colors.onPrimary else MogarColors.OnPaused
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = glyph,
                modifier = Modifier.size(22.dp),
            )
            if (!sharing) {
                val slash = glyph
                Canvas(modifier = Modifier.size(36.dp)) {
                    drawLine(
                        color = slash,
                        start = Offset(size.width * 0.22f, size.height * 0.22f),
                        end = Offset(size.width * 0.78f, size.height * 0.78f),
                        strokeWidth = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

// The notice across the map after a toggle: a white band from edge to edge, gone after a moment.
@Composable
fun SharingNotice(text: String?, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(text) }
    if (text != null) shown = text
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                shown ?: "",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = MaterialTheme.typography.bodyMedium.fontWeight),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            )
        }
    }
}

// The weather switch, in the top card beside the sharing pill and the gear (M, 2026-11-16). It was
// briefly a floating circle on the map and he moved it here: there is room in the bar, and loose on
// the map it "just feels off". Drawn like the gear and not like the sharing pill -- a bare glyph,
// no filled disc -- deliberately: a second coloured circle would compete with the one control in
// this card that has to be readable without looking, which is whether the phone is still sharing.
// Blue while the weather is up, grey while it is not, the same on/off language as its neighbour.
@Composable
fun WeatherButton(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    // What a tap will do, not what is on screen: a switch with no label reads as nothing at all to
    // TalkBack, and the Settings row that used to carry the words is gone for good (M, 2026-11-16).
    val label = stringResource(if (on) R.string.weather_hide else R.string.weather_show)
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp).semantics { contentDescription = label },
    ) {
        WeatherGlyph(if (on) colors.primary else colors.onSurfaceVariant)
    }
}

// Drawn rather than imported: only material-icons-core is on the classpath and it has no cloud, and
// the app already draws its own glyphs (ExpandGlyph). Three circles for the top and one bar to flatten
// the base, then three slanted strokes falling off it. 26.dp to match the gear it sits next to, since
// the two are read as a pair.
//
// The drops are centred under the cloud (its body runs 0.155 to 0.845, centre 0.50) at an even 0.175
// apart, and lean `\` (M, 2026-11-23). The first cut sat them 0.02 right of centre and leaned them `/`.
//
// The rain is what makes it a weather icon (M, 2026-11-16): a bare cloud "just feels like some kind
// of saving feature", which is exactly right -- an outline cloud on its own is the upload glyph every
// other app uses, and nothing about it says sky. The drops are also the honest picture of what the
// layer actually draws, which is cloud top plus rain radar. They slant because upright strokes under
// a cloud read as a barcode; a slant reads as falling.
@Composable
fun WeatherGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(26.dp)) {
        val w = size.width
        val h = size.height
        // The cloud, squeezed into the top two thirds so the rain has somewhere to fall.
        drawCircle(color, radius = w * 0.155f, center = Offset(w * 0.31f, h * 0.40f))
        drawCircle(color, radius = w * 0.205f, center = Offset(w * 0.50f, h * 0.31f))
        drawCircle(color, radius = w * 0.145f, center = Offset(w * 0.70f, h * 0.41f))
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.155f, h * 0.39f),
            size = Size(w * 0.69f, h * 0.17f),
            cornerRadius = CornerRadius(w * 0.085f, w * 0.085f),
        )
        // Three drops under it. Round caps, or at this size the ends read as cut wire.
        for (x in listOf(0.325f, 0.50f, 0.675f)) {
            // Upright (M, 2026-11-23). The lean was start x - 0.05 to end x + 0.05. Every drop is two
            // pieces, split at different heights so the three do not read as a barcode (M's own
            // sketch): the outer two are a dot over a long stroke, the middle one a long stroke over
            // a dot. A zero-length line with a round cap is a dot. Each cap runs half a stroke past
            // its end, so a gap of 0.14 between ends shows as about 0.055.
            val pieces = if (x == 0.50f) {
                listOf(0.680f to 0.790f, 0.929f to 0.930f)
            } else {
                listOf(0.680f to 0.682f, 0.820f to 0.930f)
            }
            for ((top, bottom) in pieces) {
                drawLine(
                    color,
                    start = Offset(w * x, h * top),
                    end = Offset(w * x, h * bottom),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

// The white pill under the top card while someone is picked. Spec: HANDOFF.md section 4, step 6.
@Composable
fun ShowEveryoneButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = colors.surfaceContainerLowest,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(start = 14.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExpandGlyph(colors.primary)
            Text(
                stringResource(R.string.show_everyone),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = MaterialTheme.typography.labelLarge.fontWeight),
                color = colors.primary,
            )
        }
    }
}
