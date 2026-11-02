package io.github.menadion.magus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

// A family member whose dot is off the visible map gets a small circle on the edge of the gap
// between the top card and the bottom box, its tip pointing towards them (M's call, 2026-09-26).
// A tap does what tapping their dot does. Not for you: the locate button brings you back.
private val MARKER = 88.dp  // the touch area: the 48 dp white circle and the arrowhead outside it
private val AVATAR = 38.dp
// The largest a dot's circle gets on the map (the selected one: 46 dp across plus a 3 dp ring). The name
// tag above it does not count: a marker means the circle itself is gone (M's call, 2026-11-02).
private val DOT_RADIUS = 26.dp

// One dot on the map and where it sits on screen, in pixels. A person alone is uids = [uid]. A group is one dot:
// uid is its face's, uids is everyone in it, and its marker carries the count (M's call, 2026-11-02).
data class MapSpot(val uid: String, val uids: List<String>, val point: Offset)

// spots: where each dot sits on screen. area: the visible gap, in pixels, where the markers sit. screen: the
// whole map, in pixels, which decides which dots get a marker at all. A tap on a person's marker does what
// tapping their dot does (onPick); on a group's it flies to the group and opens its bubble (onGroup).
@Composable
fun EdgeMarkers(
    people: List<Person>, now: Long, spots: List<MapSpot>, area: Rect, screen: Rect,
    onPick: (String) -> Unit, onGroup: (List<String>) -> Unit,
) {
    if (area.width <= 0f || area.height <= 0f) return
    val density = LocalDensity.current
    val half = with(density) { MARKER.toPx() } / 2
    val gap = with(density) { 52.dp.toPx() } // closest two markers may sit, centre to centre
    // A dot whose circle is still on the screen, even half of it, needs no marker. One under the top card
    // or the bottom box is on the screen, so it gets none either.
    val onScreen = screen.inflate(with(density) { DOT_RADIUS.toPx() })
    // Markers sit on this smaller box, so the whole circle and its tip stay in the gap.
    val track = area.deflate(half)
    val centre = area.center

    val placed = mutableListOf<Offset>()
    val byUid = people.filter { !it.isYou }.associateBy { it.uid }
    spots.forEach { spot ->
        val person = byUid[spot.uid] ?: return@forEach
        val point = spot.point
        if (onScreen.contains(point)) return@forEach
        val dx = point.x - centre.x
        val dy = point.y - centre.y
        if (dx == 0f && dy == 0f) return@forEach
        // Along the line from the gap's centre towards the dot, stop at the track's edge.
        val reach = min(
            if (dx != 0f) track.width / 2 / abs(dx) else Float.MAX_VALUE,
            if (dy != 0f) track.height / 2 / abs(dy) else Float.MAX_VALUE,
        )
        var at = Offset(centre.x + dx * reach, centre.y + dy * reach)
        // Two people in the same direction: slide the later one along the edge until it's clear.
        val onSide = abs(at.x - track.left) < 1f || abs(at.x - track.right) < 1f
        repeat(placed.size + 1) {
            val clash = placed.firstOrNull { hypot(it.x - at.x, it.y - at.y) < gap } ?: return@repeat
            at = if (onSide) {
                Offset(at.x, (if (at.y >= clash.y) clash.y + gap else clash.y - gap).coerceIn(track.top, track.bottom))
            } else {
                Offset((if (at.x >= clash.x) clash.x + gap else clash.x - gap).coerceIn(track.left, track.right), at.y)
            }
        }
        placed += at
        val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        val count = spot.uids.size
        EdgeMarker(
            person, now, angle, count,
            onClick = { if (count > 1) onGroup(spot.uids) else onPick(person.uid) },
            modifier = Modifier.offset { IntOffset((at.x - half).toInt(), (at.y - half).toInt()) },
        )
    }
}

// The white circle, their avatar upright in the middle, and outside it an arrowhead in their own
// colour turned towards them, set far enough out to clear the pause or clock badge.
@Composable
private fun EdgeMarker(person: Person, now: Long, angle: Float, count: Int, onClick: () -> Unit, modifier: Modifier) {
    val label = if (count > 1) stringResource(R.string.go_to_group, count) else stringResource(R.string.go_to_person, person.name)
    Box(
        modifier = modifier
            .size(MARKER)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(MARKER).rotate(angle)) {
            val r = 24.dp.toPx()
            val c = center
            val arrow = Path().apply {
                moveTo(c.x + r + 18.dp.toPx(), c.y)
                lineTo(c.x + r + 7.dp.toPx(), c.y - 8.dp.toPx())
                lineTo(c.x + r + 7.dp.toPx(), c.y + 8.dp.toPx())
                close()
            }
            // A soft shadow under the circle, then the circle; the arrowhead gets a white edge so it
            // reads on any map colour.
            drawCircle(Color(0x33000000), r + 1.5.dp.toPx(), c)
            drawCircle(Color.White, r, c)
            drawPath(arrow, Color.White, style = Stroke(width = 4.dp.toPx(), join = StrokeJoin.Round))
            drawPath(arrow, Color(person.color))
        }
        Box(contentAlignment = Alignment.Center) {
            Avatar(
                person.state(now),
                person.letter,
                AVATAR,
                photo = person.member.photo,
                you = person.isYou,
                color = Color(person.color),
                badge = count <= 1, // a group's corner holds its count instead
            )
            if (count > 1) CountBadge(count, Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 6.dp))
        }
    }
}

// The head count at a group's bottom right, where a single person's pause or clock badge goes.
@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .background(Color.White, CircleShape)
            .padding(2.dp)
            .background(Color(0xFF1B1B1F), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = if (count > 99) 8.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
