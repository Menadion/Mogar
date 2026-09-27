package io.github.menadion.magus

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// The short tour: pictures of Mogar with a box pointing at the part that matters (M's brief,
// 2026-09-25: "just slideshow images with some pointy boxes and text inside of it"). Shown once
// after setup, once to phones that already had Mogar when it arrived, and again from
// Settings > How Mogar works. The pictures are emulator screenshots of a pretend family (Lorna
// and Jen); the words are strings, so they follow the language switch while the pictures stay
// English.
object Tutorial {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // Once, and only once the phone is in a family: setup comes first.
    fun dueNow(context: Context): Boolean =
        Family.savedCode(context) != null && !prefs(context).getBoolean("tutorialSeen", false)

    fun markSeen(context: Context) = prefs(context).edit().putBoolean("tutorialSeen", true).apply()
}

// One picture, one sentence or two, and the rectangle they point at, as fractions of the picture.
private class Slide(
    @DrawableRes val picture: Int, @StringRes val text: Int,
    val x: Float, val y: Float, val w: Float, val h: Float,
)

// The pictures are 1080 x 2400 screenshots scaled to 720 x 1600; fractions don't care.
private const val PICTURE_ASPECT = 1080f / 2400f

private val SLIDES = listOf(
    Slide(R.drawable.tut_map, R.string.tut_map, 0.03f, 0.835f, 0.94f, 0.135f),
    Slide(R.drawable.tut_card, R.string.tut_card, 0.03f, 0.695f, 0.94f, 0.275f),
    Slide(R.drawable.tut_notice, R.string.tut_notice, 0.03f, 0.235f, 0.94f, 0.135f),
    Slide(R.drawable.tut_update, R.string.tut_update, 0.03f, 0.53f, 0.94f, 0.175f),
    Slide(R.drawable.tut_protect, R.string.tut_protect, 0.12f, 0.622f, 0.76f, 0.07f),
    Slide(R.drawable.tut_keep, R.string.tut_keep, 0.03f, 0.22f, 0.94f, 0.415f),
)

@Composable
fun TutorialScreen(onDone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val pager = rememberPagerState { SLIDES.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == SLIDES.lastIndex

    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // What this is, over every page (M's ask, 2026-09-27).
            Text(
                stringResource(R.string.tutorial),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
            )
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                SlidePage(SLIDES[page])
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDone, enabled = !last, modifier = Modifier.alpha(if (last) 0f else 1f)) {
                    Text(stringResource(R.string.tut_skip), color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(SLIDES.size) { i ->
                        val here = i == pager.currentPage
                        Box(
                            Modifier.size(if (here) 10.dp else 8.dp)
                                .background(if (here) colors.primary else colors.outlineVariant, CircleShape)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }) {
                    Text(stringResource(if (last) R.string.done else R.string.tut_next))
                }
            }
        }
    }
}

// The picture as large as fits, a ring round the part that matters, and the box pointing at it:
// below the ring when the ring sits in the top half, above it otherwise.
@Composable
private fun SlidePage(slide: Slide) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        val drawnH = if (boxW / boxH > PICTURE_ASPECT) boxH else boxW / PICTURE_ASPECT
        val drawnW = drawnH * PICTURE_ASPECT
        val ringLeft = slide.x * drawnW
        val ringTop = slide.y * drawnH
        val ringW = slide.w * drawnW
        val ringH = slide.h * drawnH
        val gap = with(density) { 10.dp.toPx() }
        val margin = with(density) { 14.dp.toPx() }
        val below = slide.y + slide.h / 2 < 0.5f
        val shape = RoundedCornerShape(24.dp)

        Box(
            modifier = Modifier
                .size(with(density) { drawnW.toDp() }, with(density) { drawnH.toDp() })
                .clip(shape)
                .border(1.dp, colors.outlineVariant, shape)
        ) {
            Image(
                painterResource(slide.picture), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds,
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(ringLeft.roundToInt(), ringTop.roundToInt()) }
                    .size(with(density) { ringW.toDp() }, with(density) { ringH.toDp() })
                    .border(3.dp, colors.primary, RoundedCornerShape(18.dp))
            )
            // The box spans the picture's width minus a margin; only the pointer moves to the ring.
            val pointerX = ringLeft + ringW / 2 - margin
            if (below) {
                val top = ringTop + ringH + gap
                Box(
                    modifier = Modifier
                        .offset { IntOffset(margin.roundToInt(), top.roundToInt()) }
                        .width(with(density) { (drawnW - 2 * margin).toDp() })
                        .height(with(density) { (drawnH - top).toDp() }),
                    contentAlignment = Alignment.TopCenter,
                ) { Callout(stringResource(slide.text), pointerX, pointsUp = true) }
            } else {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(margin.roundToInt(), 0) }
                        .width(with(density) { (drawnW - 2 * margin).toDp() })
                        .height(with(density) { (ringTop - gap).toDp() }),
                    contentAlignment = Alignment.BottomCenter,
                ) { Callout(stringResource(slide.text), pointerX, pointsUp = false) }
            }
        }
    }
}

@Composable
private fun Callout(text: String, pointerX: Float, pointsUp: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        if (pointsUp) Pointer(pointerX, up = true, color = colors.primary)
        Surface(color = colors.primary, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text, color = colors.onPrimary, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            )
        }
        if (!pointsUp) Pointer(pointerX, up = false, color = colors.primary)
    }
}

// The pointy bit: a triangle on the box's top or bottom edge, tip at x.
@Composable
private fun Pointer(x: Float, up: Boolean, color: Color) {
    Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
        val half = 11.dp.toPx()
        val tipX = x.coerceIn(half + 12.dp.toPx(), size.width - half - 12.dp.toPx())
        val path = Path().apply {
            if (up) { moveTo(tipX - half, size.height); lineTo(tipX, 0f); lineTo(tipX + half, size.height) }
            else { moveTo(tipX - half, 0f); lineTo(tipX, size.height); lineTo(tipX + half, 0f) }
            close()
        }
        drawPath(path, color)
    }
}
