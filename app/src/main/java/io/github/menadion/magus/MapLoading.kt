package io.github.menadion.magus

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// What the map area shows before the map itself is there.
//
// Why it exists: on a budget phone (the family's Galaxy A13) the map stayed a blank grey rectangle
// for seconds with nothing to say why, and a phone with no load on its SIM stayed blank forever.
// The wait is not shortened by any of this; it is only made legible.
//
// Nothing here is on a fixed timer. MapLibre says when the map is really drawn and when it has
// really failed (see FamilyMap), so the indicator lasts exactly as long as the phone in front of
// you needs. The one timer, SLOW, covers the third case: a network too slow to render and too
// alive to fail, where neither callback ever fires.
enum class MapLoadState { LOADING, SLOW, FAILED, DONE }

private const val PEBBLE_DP = 26f
private const val RING_DP = 5f
private const val WAVES = 3
private const val MAX_SCALE = 3.6f
private const val CYCLE_MS = 2400

// The still mark has no spreading waves to give it presence, so it is drawn larger than the
// pebble the ripple starts from; at the same size it read as a dot lost in the empty area.
private const val STILL_SCALE = 1.9f

@Composable
fun MapLoadingOverlay(state: MapLoadState, modifier: Modifier = Modifier) {
    if (state == MapLoadState.DONE) return
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxSize().background(colors.surfaceContainerHigh),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state == MapLoadState.LOADING) Ripple(colors.primary) else NoSignal(colors.onSurfaceVariant)
        Text(
            text = stringResource(
                when (state) {
                    MapLoadState.FAILED -> R.string.map_failed
                    MapLoadState.SLOW -> R.string.map_slow
                    else -> R.string.map_loading
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
    }
}

// The logo in motion: the pebble sits still and its ripple spreads, which is the mark's own
// picture and also what the app is doing, looking for where everyone is. Three strokes scaled on
// the GPU, which is as cheap as this can be drawn; the phone showing it is the one already
// struggling, so a shimmer sweep across the whole area would be the wrong way round.
@Composable
private fun Ripple(color: Color) {
    val transition = rememberInfiniteTransition(label = "ripple")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CYCLE_MS, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(modifier = Modifier.size((PEBBLE_DP * MAX_SCALE).dp)) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val pebble = PEBBLE_DP.dp.toPx() / 2f
        val ring = RING_DP.dp.toPx()
        repeat(WAVES) { i ->
            val p = (phase + i.toFloat() / WAVES) % 1f
            drawCircle(
                color = color.copy(alpha = 0.85f * (1f - p)),
                radius = pebble * (1f + (MAX_SCALE - 1f) * p),
                center = centre,
                style = Stroke(width = ring * 0.6f),
            )
        }
        drawCircle(color = color, radius = pebble - ring / 2f, center = centre, style = Stroke(width = ring))
    }
}

// The same pebble with its ripples gone and a line through it. Deliberately not a wifi mark: the
// family is on mobile data, and a crossed-out wifi symbol would send them looking for a router
// when what has usually run out is the load on the SIM.
@Composable
private fun NoSignal(color: Color) {
    Canvas(modifier = Modifier.size((PEBBLE_DP * MAX_SCALE).dp)) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val pebble = PEBBLE_DP.dp.toPx() / 2f * STILL_SCALE
        val ring = RING_DP.dp.toPx() * STILL_SCALE
        drawCircle(color = color, radius = pebble - ring / 2f, center = centre, style = Stroke(width = ring))
        val reach = pebble * 1.35f
        drawLine(
            color = color,
            start = Offset(centre.x - reach, centre.y + reach),
            end = Offset(centre.x + reach, centre.y - reach),
            strokeWidth = ring,
            cap = StrokeCap.Round,
        )
    }
}
