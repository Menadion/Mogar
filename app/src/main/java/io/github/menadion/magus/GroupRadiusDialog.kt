package io.github.menadion.magus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

// The window Settings > Group radius opens: a real map with a circle of the chosen radius around a centred
// dot, a slider and a number box that move each other, and Cancel / Save. Smaller than the screen.
// Cancel, the Back key and a tap outside all mean Cancel, which asks first if anything changed.
//
// here: where I am, or last was when paused; null when the phone has never given a position, and then the
// map sits where the main map was looking and says so, because a dot labelled as me there would be a lie.
@Composable
fun GroupRadiusDialog(
    current: Int,
    here: LatLng?,
    lookingAt: LatLng,
    sharing: Boolean,
    onSave: (Int) -> Unit,
    onClose: () -> Unit,
) {
    // Saveable: a turn of the phone rebuilds the window but not the half-made choice in it.
    var draft by rememberSaveable { mutableIntStateOf(current) }
    var text by rememberSaveable { mutableStateOf(current.toString()) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val changed = draft != current
    fun cancel() = if (changed) confirmDiscard = true else onClose()
    val colors = MaterialTheme.colorScheme

    Dialog(onDismissRequest = ::cancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surfaceContainerHigh,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp).widthIn(max = 420.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // The body scrolls and the buttons stay put, so a turned phone cannot push Save off the screen.
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.group_radius), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.group_radius_explain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    RadiusMap(radius = draft, centre = here ?: lookingAt, isMe = here != null, sharing = sharing)
                    if (here == null) {
                        Text(
                            stringResource(R.string.group_radius_example),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Text(
                        if (draft == 0) stringResource(R.string.group_radius_overlap) else stringResource(R.string.group_radius_value, draft),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    val sliderLabel = stringResource(R.string.group_radius)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Slider(
                            value = draft.toFloat(),
                            onValueChange = {
                                draft = it.roundToInt()
                                text = draft.toString()
                            },
                            valueRange = 0f..GroupRadius.MAX.toFloat(),
                            modifier = Modifier.weight(1f).semantics { contentDescription = sliderLabel },
                        )
                        OutlinedTextField(
                            value = text,
                            onValueChange = { typed ->
                                val digits = typed.filter { it.isDigit() }.take(3)
                                if (digits.isEmpty()) {
                                    text = ""
                                } else {
                                    val n = digits.toInt().coerceAtMost(GroupRadius.MAX)
                                    draft = n
                                    text = n.toString()
                                }
                            },
                            singleLine = true,
                            suffix = { Text("m") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(104.dp),
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = ::cancel) { Text(stringResource(R.string.cancel)) }
                    // An emptied number box has no radius in it yet.
                    Button(onClick = { onSave(draft) }, enabled = text.isNotEmpty()) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.group_radius_discard_title)) },
            text = { Text(stringResource(R.string.group_radius_discard_text)) },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.group_radius_discard)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.group_radius_keep)) } },
        )
    }
}

private val MAP_HEIGHT = 220.dp
private const val WORLD_DP = 512.0 // MapLibre draws the world this many dp across at zoom 0
private const val EQUATOR_M = 40_075_016.686

// The map has no gestures and a zoom the radius picks, in half steps so dragging the slider does not
// reload tiles on every notch: the circle ends up about a third of the map's height across, between
// 0.7 and 1.4 times that. Radius 0 draws no circle and sits at a street-level zoom.
private fun zoomFor(radius: Int, lat: Double): Double {
    if (radius <= 0) return 16.5
    val metresPerDp = radius / (MAP_HEIGHT.value * 0.32)
    val z = ln(EQUATOR_M * cos(lat * PI / 180) / WORLD_DP / metresPerDp) / ln(2.0)
    return ((z * 2).roundToInt() / 2.0).coerceIn(13.0, 18.0)
}

@Composable
private fun RadiusMap(radius: Int, centre: LatLng, isMe: Boolean, sharing: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current.density
    val colors = MaterialTheme.colorScheme
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val zoom = zoomFor(radius, centre.latitude)
    val mapView = remember {
        // Texture mode: the default surface is a separate layer that ignores the scrolling body's edge.
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
            onCreate(null)
            getMapAsync { m ->
                m.uiSettings.setAllGesturesEnabled(false)
                m.uiSettings.isCompassEnabled = false
                m.uiSettings.isLogoEnabled = false
                m.uiSettings.isAttributionEnabled = false // credit lives under ⋮ > About the map
                m.cameraPosition = CameraPosition.Builder().target(centre).zoom(zoomFor(radius, centre.latitude)).build()
                m.setStyle(Style.Builder().fromUri(MAP_STYLE)) { map = m }
            }
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // The activity lives on, so the map must be shut by hand when its window goes.
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(map, zoom, centre) {
        map?.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(centre).zoom(zoom).build()))
    }

    // Pixels across the circle's radius at this zoom: a dp is metresPerDp metres.
    val metresPerDp = EQUATOR_M * cos(centre.latitude * PI / 180) / WORLD_DP / 2.0.pow(zoom)
    val circlePx = (radius / metresPerDp * density).toFloat()
    val description = if (radius == 0) stringResource(R.string.group_radius_overlap) else stringResource(R.string.group_radius_map, radius)
    val ring = colors.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MAP_HEIGHT)
            .clip(MaterialTheme.shapes.large)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (circlePx > 0f) {
                drawCircle(ring.copy(alpha = 0.18f), circlePx, center)
                drawCircle(ring, circlePx, center, style = Stroke(width = 2.dp.toPx()))
            }
        }
        if (isMe) {
            Avatar(if (sharing) Markers.State.YOU else Markers.State.PAUSED, "", 30.dp, photo = Family.savedPhoto(context), you = true)
        } else {
            // No "You" here: this is only where the main map was looking.
            Box(modifier = Modifier.size(18.dp).background(Color.White, CircleShape)) {
                Box(modifier = Modifier.align(Alignment.Center).size(10.dp).background(ring, CircleShape))
            }
        }
    }
}
