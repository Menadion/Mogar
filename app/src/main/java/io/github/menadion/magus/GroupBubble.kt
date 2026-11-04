package io.github.menadion.magus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// The speech bubble a group opens: a row of up to three avatars with its tail on the group's spot
// on the map. More than three and the row slides sideways, with < and > at the ends only while
// there is more that way. M's design, 2026-11-02.
private const val SHOWN = 3
private val CELL = 72.dp
private val SIDE = 8.dp
private val BODY_HEIGHT = 92.dp
private val TAIL = 9.dp

// The arrows sit ON the row rather than beside it, over a fade from the bubble's own colour, so an
// avatar at either end slides under them instead of being cut off at a hard edge. Same construction
// as FamilyRow's ScrollArrow, which solved this on the bottom strip first. M's call, 2026-11-04:
// reserved side slots made the arrows look like they were swallowing the people next to them.
private val FADE = 40.dp

// How far down a cell the avatar's centre sits: 8 dp of padding, then half of 48 dp.
private val AVATAR_MIDDLE = 16.dp

// How far above a group's spot the bubble's tail ends: just clear of the group's dot, which has no
// name tag (see Markers).
private val ABOVE_SPOT = 30.dp
private val EDGE = 8.dp

@Composable
fun GroupBubble(
    people: List<Person>,
    now: Long,
    anchor: Offset, // the group's spot, in pixels from the map's top left
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val width: Dp = CELL * minOf(people.size, SHOWN) + SIDE * 2
    val height = BODY_HEIGHT + TAIL
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val spotX = with(density) { anchor.x.toDp() }
        val spotY = with(density) { anchor.y.toDp() }
        val left = (spotX - width / 2).coerceIn(EDGE, maxOf(EDGE, maxWidth - width - EDGE))
        val top = spotY - ABOVE_SPOT - height
        // The tail follows the spot even when the bubble itself is held inside the screen.
        val tailAt = (spotX - left).coerceIn(TAIL * 2, width - TAIL * 2)
        val description = stringResource(R.string.group_of, people.size)
        Surface(
            shape = BubbleShape(with(density) { TAIL.toPx() }, with(density) { tailAt.toPx() }),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 4.dp,
            modifier = Modifier
                .offset(left, top)
                .size(width, height)
                .semantics { contentDescription = description },
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(bottom = TAIL)) {
                val list = rememberLazyListState()
                val scope = rememberCoroutineScope()
                val page = with(density) { (CELL * SHOWN).toPx() }
                LazyRow(
                    state = list,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = SIDE),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(people, key = { it.uid }) { person -> Cell(person, now, onClick = { onPick(person.uid) }) }
                }
                // Drawn after the row, so the row passes underneath them.
                Arrow(
                    visible = list.canScrollBackward,
                    left = true,
                    onClick = { scope.launch { list.animateScrollBy(-page) } },
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Arrow(
                    visible = list.canScrollForward,
                    left = false,
                    onClick = { scope.launch { list.animateScrollBy(page) } },
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
    }
}

@Composable
private fun Cell(person: Person, now: Long, onClick: () -> Unit) {
    val state = person.state(now)
    val goTo = when (state) {
        Markers.State.YOU -> stringResource(R.string.go_to_you)
        Markers.State.PAUSED -> stringResource(R.string.go_to_person_paused, person.name)
        Markers.State.QUIET -> stringResource(R.string.go_to_person_quiet, person.name)
        Markers.State.WAITING -> stringResource(R.string.go_to_person_waiting, person.name)
        else -> stringResource(R.string.go_to_person, person.name)
    }
    Column(
        modifier = Modifier
            .width(CELL)
            .clickable(onClick = onClick)
            .semantics { contentDescription = goTo }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Avatar(state, person.letter, 48.dp, photo = person.member.photo, you = person.isYou, color = Color(person.color))
        Text(
            person.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (state == Markers.State.PAUSED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

// A small round < or > over a fade from the bubble's colour, so people and their names slide under
// it. The fade runs the body's full height; the button sits level with the avatars. Only the button
// itself takes a tap: the fade is paint, so a cell showing through it is still tappable.
@Composable
private fun Arrow(visible: Boolean, left: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            modifier = Modifier
                .width(FADE)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        if (left) listOf(colors.surfaceContainerLowest, colors.surfaceContainerLowest.copy(alpha = 0f))
                        else listOf(colors.surfaceContainerLowest.copy(alpha = 0f), colors.surfaceContainerLowest),
                    ),
                ),
            contentAlignment = if (left) Alignment.TopStart else Alignment.TopEnd,
        ) {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = colors.surfaceContainerHigh,
                shadowElevation = 2.dp,
                modifier = Modifier.padding(top = AVATAR_MIDDLE).size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(if (left) R.string.group_scroll_left else R.string.group_scroll_right),
                        tint = colors.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

// A rounded box with a small triangle hanging from its bottom edge, its point at tailAt.
private class BubbleShape(private val tail: Float, private val tailAt: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val corner = CornerRadius(with(density) { 20.dp.toPx() })
        val body = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, size.width, size.height - tail), corner)) }
        val point = Path().apply {
            moveTo(tailAt - tail, size.height - tail - 1f)
            lineTo(tailAt, size.height)
            lineTo(tailAt + tail, size.height - tail - 1f)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, point))
    }
}
