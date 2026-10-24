# Mogar UI/UX pass, October 2026

Scored against `data/app-interface.csv` from
[nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill)
(MIT), 32 rows, read at commit `d14efff`. Mogar read at `3cf4260` (version 0.12).

This is a source-only review. Nothing was built, signed, installed or run. No app code was
changed. The catalogue is recommendations, not instructions: where a row argues against how
Mogar already works, the row is noted and the repo wins.

The catalogue's code examples are all React Native and were ignored. Only the rules transfer.
`data/stacks/jetpack-compose.csv` was out of scope and was not read.

## Verdicts at a glance

| | Count |
|---|---|
| PASS | 14 |
| FAIL | 16 |
| NEEDS-A-DEVICE | 2 |

Rows are ordered by what they cost a non-technical person on a small cheap phone, not by the
catalogue's numbering. The catalogue row number is given for each.

Three of the fourteen passes (rows 10, 19 and 31) pass only because they do not apply to Mogar
at all: no tab bar, no password field, nothing to drag-reorder. They are marked as such rather
than counted as design wins, so the real score is 11 earned passes out of 29 applicable rows.

Several FAILs are one named defect in a rule the app otherwise satisfies (rows 5, 7, 13 and 22
are each a single site). The verdict is binary; the body says how wide the failure is, and the
four items below are the ones worth spending the next build on.

### The short version

Four things are worth the next build's attention, in this order:

1. **The sharing pill's own icon is the worst-contrast element in the app in dark mode**
   (row 29). The grey "off" state borrows `onPrimary` for its glyph, which is a dark navy in
   the dark scheme. 3.3:1 on the single most important state indicator Mogar has.
2. **Settings is operated almost entirely through 40 dp targets** (row 6). Every segmented
   button (Theme, auto-update, What's new popup, Help fix problems) is 40 dp tall, as is the
   pencil that renames the family and the row that changes your number.
3. **Two text fields have no label at all** (row 2), both of them in the family page's
   dialogs, which is the screen the pairing build is about to touch anyway.
4. **Nothing in the app ever announces a change** (row 4). The sharing notice, "Copied", the
   map's loading state and the update subtitle all change silently.

The things Mogar does better than the catalogue asks are worth saying too: the gesture
alternatives (row 30) are unusually complete, inset handling (row 28) is applied on every
screen rather than the usual two, and the map's loading states (row 13) are driven by real
MapLibre callbacks instead of a guessed timer.

---

## Tier 1: the register this project is judged in

### Row 1, Accessibility, Icon Button Labels (Critical)

**Rule.** An icon with no text beside it must still have a name a screen reader can read.

**PASS.**

Every icon-only control in the app has one:

- `MainActivity.kt:1073` the gear, and it changes to "Settings, something needs your
  attention" while the red dot is up, which is better than the rule asks for
- `MainActivity.kt:1093` and `MainActivity.kt:1099` the sharing pill
- `FamilyPage.kt:127` and `SettingsScreen.kt:83` the back arrows
- `FamilyPage.kt:145` the rename pencil
- `FamilyRow.kt:163` the `<` and `>` scroll arrows
- `FamilyRow.kt:222` the close-list chevron
- `MemberCard.kt:117` the card's close button
- `EdgeMarkers.kt:83` and `EdgeMarkers.kt:89` the off-screen member markers

The `.clickable{}.semantics{}` pairs (`FamilyRow.kt:187`, `EdgeMarkers.kt:88`) work because
`clickable` merges descendants, so the explicit description wins over the avatar's letter
rather than being read after it.

### Row 6, Touch, Touch Target Size (Critical)

**Rule.** On Android a tappable thing should be at least 48 dp in both directions. The
catalogue's point is that this is a per-platform number, not one cross-platform number.

**FAIL.**

Mogar clearly knows the rule: `heightIn(min = 48.dp)` appears deliberately at
`MainActivity.kt:1168`, `FamilyRow.kt:79`, `KeepRunning.kt:344`, `KeepRunning.kt:356`,
`KeepRunning.kt:408` and `KeepRunning.kt:412`, and `Modifier.size(48.dp)` on five of the six
`IconButton`s. The failures are the places that were missed, and they cluster in Settings and
the family page.

Under 48 dp:

- `SettingsScreen.kt:306` every `SegmentedButton` in the app. Material 3's
  `SegmentedButtonDefaults.Height` is 40 dp and nothing overrides it. This is Theme (Auto /
  Light / Dark), "Update Mogar by itself", "Show what's new after an update" and "Help fix
  problems": four of Settings' nine controls, and the four that are not `NavRow`s.
- `Feedback.kt:93` the same 40 dp on the feedback Yes/No.
- `FamilyPage.kt:144` the rename pencil, explicitly `Modifier.size(40.dp)` with a 20 dp glyph.
- `FamilyPage.kt:212-219` the row that opens the number dialog. A `bodyMedium` line (15 sp,
  20 sp line height) with 4 dp padding above and below inside the clickable: roughly 28 dp.
  This is also "Add a number", so it is the only route to entering a phone number at all.
- `FamilyPage.kt:150-163` the family code, tapped to copy. `displayMedium` (26 sp, 32 sp line
  height) plus 2 dp vertical padding, roughly 36 dp. The whole invite flow runs through it.
- `MemberCard.kt:183-195` the phone number, tapped to copy: `titleMedium` plus 8 dp each side,
  roughly 40 dp.
- `FamilyRow.kt:158` the `<` and `>` arrows, `Modifier.size(32.dp)`. These only appear once
  the family outgrows five people, so they do not bite today.
- The `AlertDialog` text buttons throughout (Save, Cancel, OK, Leave) sit at Material 3's
  40 dp `TextButton` height. `FamilyPage.kt:239-246`'s "Change name" and "Change picture"
  `FilledTonalButton`s and `MemberCard.kt:196-205`'s Call and Text are the same.

**Direction.** The `heightIn(min = 48.dp)` habit already in the file, applied to the
segmented rows and to the three tap-to-copy and tap-to-edit text targets. The dialog buttons
are a Material default and arguably the platform's call rather than Mogar's; the tap-to-copy
text targets are not, and they are the ones a parent misses and then taps the map by accident.

### Row 2, Accessibility, Form Control Labels (Critical)

**Rule.** Every input needs a visible label of its own, not just a placeholder. (Mogar uses
no `placeholder` anywhere, so the literal anti-pattern is absent. The spirit is not.)

**FAIL.**

Labelled correctly: `SetupScreen.kt:107` (your name), `SetupScreen.kt:119` (phone),
`SetupScreen.kt:141` (family name), `SetupScreen.kt:166` (family code),
`Feedback.kt:105` and `Feedback.kt:111`.

Unlabelled:

- `FamilyPage.kt:363-372` the number dialog's field. It has a `prefix` of "+63" and
  `supportingText` of "Optional", but no `label`. The only thing naming it is the dialog's
  title at `FamilyPage.kt:361`, which is a separate semantics node.
- `FamilyPage.kt:401-409` the name dialog's field. Same shape, no `label`, named only by the
  title at `FamilyPage.kt:399`.

Both fields sit alone in their dialog, so sighted use is fine. A screen reader lands on an
unnamed edit box. It is also an inconsistency inside the repo: the same two values have
proper `label`s on the Setup screen.

**Direction.** Give each dialog field the `label` its Setup twin already has. The dialog
title then repeats rather than carries.

### Row 27, Typography, Dynamic Type Support (High)

**Rule.** Honour the system text-size setting without the layout breaking.

**NEEDS-A-DEVICE.**

The first half is settled and passes: nothing anywhere disables scaling. There is no
`fontScale` override, no `TextUnitType.Dp` font size, no `nonScaledSp` helper. Every size in
`Theme.kt:113-128` is `.sp`, and the map pin text goes through
`TypedValue.applyDimension(COMPLEX_UNIT_SP, ...)` at `Markers.kt:61`, which picks up
`scaledDensity`, so even the bitmap markers scale and `Markers.kt:86-94` re-measures the tag
width to match.

The second half (does it break) cannot be answered from source. **What would settle it:** set
Android's font size to Largest on the vivo 1910 and walk the map, family page, Settings and
Keep-running screens. Specifically worth looking at, because these are fixed-dp boxes holding
scalable text:

- `SettingsScreen.kt:78` and `FamilyPage.kt:122`, the two screen headers, `Modifier.height(64.dp)`
  with `headlineMedium` (24 sp, 30 sp line height) inside and 8 dp of top padding. At the top
  font scale the line box alone exceeds the row.
- `People.kt:126`, `letterSize = (size.value * 0.43f).sp`. The avatar letter is derived from a
  dp and then declared in sp, so the letter scales while the circle at `People.kt:128` does
  not. At large scales the initial should overflow its disc in the family row, the See-all
  list, the member card and the edge markers at once.
- `BottomPanel.kt:48` (`screenHeightDp * 0.5f`) and `FamilyPage.kt:184`
  (`screenHeightDp * 0.38f`), both fixed fractions of the screen holding scalable rows.
- `SetupScreen.kt:153` and `SetupScreen.kt:176`, `Modifier.height(52.dp)` on the two buttons
  that are the only way out of the Setup screen, and `KeepRunning.kt:322`'s `height(56.dp)`.
- `FamilyRow.kt:202-203`, `maxLines = 1` with ellipsis on family-row names inside a 72 dp
  column: names will truncate earlier as the text grows, which is the correct behaviour but
  worth seeing at what point it becomes one letter and a dot.

### Row 26, Typography, Base Font Size (High)

**Rule.** Body text at 14 to 16 pt or larger. Nothing important below 12.

**PASS.**

`Theme.kt:113-128` bottoms out at 14 sp (`bodySmall`, `labelSmall`). Body is 16 sp
(`bodyLarge`), status lines 15 sp, list row names 18 sp. Nothing in the scale is below 14, and
the map pin text is 16 sp (`Markers.kt:85`). This is a noticeably larger floor than Material's
own defaults, which reads as a deliberate choice for this audience.

One thing to watch rather than a defect: the two places still at the 14 sp floor are the ones
carrying diagnostic detail, `MemberCard.kt:147` (the diagnostics summary) and
`MemberCard.kt:154` (the last-crash line), both grey or red on white at the smallest size in
the app. They are the least important text on the card, so the ranking is right.

### Row 13, Feedback, Loading Indicators (High)

**Rule.** Anything over about 300 ms shows something. Do not leave a button or a screen frozen.

**FAIL**, on one path.

Almost everything is covered, and the map is covered unusually well:

- `MapLoading.kt:52-75` with `MainActivity.kt:686-696` and `MainActivity.kt:830-835`. Three
  states (loading, slow, failed) driven by MapLibre's own render and fail callbacks, with the
  only timer covering the case where neither callback ever fires. The comment at
  `MapLoading.kt:29-38` explains why, and it is the right call: the indicator lasts as long as
  the phone needs.
- `SetupScreen.kt:181` "Working…" plus `enabled = !busy` on both buttons.
- Label swaps under `busy`: `FamilyPage.kt:376` ("Saving…"), `FamilyPage.kt:346` ("Leaving…"),
  `Feedback.kt:149` ("Sending…").
- `SettingsScreen.kt:267` the download percentage in the button, with the deliberate note at
  `SettingsScreen.kt:253` about keeping it enabled so the percentage stays full-colour.

The gap is the profile picture. `FamilyPage.kt:105-112` runs `Photos.shrink` on the IO
dispatcher and then `Family.setPhoto`, both inside `attempt`, which sets `busy`. `busy` only
disables the two buttons at `FamilyPage.kt:239` and `FamilyPage.kt:242`. There is no label
swap and no indicator. Decoding a modern camera JPEG and uploading it as a Firestore blob on
a Galaxy A13 over mobile data is the slowest operation in the app, and what the person sees is
two buttons going grey and their old picture still on screen.

**Direction.** The same label-swap the other three dialogs already use, on the "Change
picture" button.

### Row 14, Feedback, Success Feedback (Medium)

**Rule.** Confirm that something worked, briefly.

**PASS.**

- `MainActivity.kt:1132-1154` the notice band, with four distinct messages at
  `MainActivity.kt:376`, `382`, `390` and `493`, and the 3-second window at
  `MainActivity.kt:273-278`. The comment there explains why it is 3 seconds and not the 1.5 it
  was, which is the right reasoning: it now carries a sentence, not a tick.
- `FamilyPage.kt:160-171` the code turns primary-coloured and the hint line becomes "Copied"
  for 1.5 s.
- `MemberCard.kt:183-193` the same for the phone number.
- `Feedback.kt:79-80` a thank-you that replaces the form.

Renames and photo changes confirm by the value visibly changing in place, which is adequate.

### Row 15, Feedback, Error Feedback (High)

**Rule.** Show the error near the problem. Input-level message plus a summary, not a colour
change with no explanation.

**FAIL.**

There is no field-level error anywhere: `isError` appears on none of the eight
`OutlinedTextField`s. Errors are one line of red text at the bottom of a screen:

- `SetupScreen.kt:182`, below both choice cards.
- `FamilyPage.kt:267`, at the bottom of the scrolling column, below the Leave group.

The family page case is the sharper one. A failed rename or a failed photo upload closes its
dialog (`FamilyPage.kt:282-284`, `FamilyPage.kt:297-299` only run on success, so on failure
the dialog stays, but `attempt`'s `problem` is rendered on the page behind it). The red line
lands under "Leave family", which on these screen heights is below the fold.

Separately, the message itself is `e.message` first and the authored string only as a fallback
(`SetupScreen.kt:75`, `FamilyPage.kt:97`). `Family.kt` raises two readable messages
(`Family.kt:165`, `Family.kt:174`), but a Firestore `PERMISSION_DENIED` or a network timeout
arrives as its own English SDK text, shown verbatim to a non-technical reader.

**Direction.** Put the message inside the dialog that caused it, and prefer the authored
string over `e.message` rather than the other way round. The existing
`something_wrong_connection` string already says the useful thing.

### Row 28, Safe Areas, Safe Area Insets (High)

**Rule.** Content, especially tappable content, must not sit under notches or the gesture bar.

**PASS.**

`enableEdgeToEdge()` at `MainActivity.kt:172`, then insets applied on every screen rather than
the usual one or two:

- `MainActivity.kt:1029` `statusBarsPadding()` on the top card
- `FamilyRow.kt:76` and `BottomPanel.kt:95` `navigationBarsPadding()` on both bottom boxes
- `SetupScreen.kt:85-86`, `SettingsScreen.kt:75`, `FamilyPage.kt:117-118`,
  `KeepRunning.kt:246-247`, `Tutorial.kt:95` both insets on each full screen
- `MainActivity.kt:406` the edge markers are confined to a `Rect` built from the measured
  heights of the two inset-padded boxes, so they inherit the safe gap rather than needing
  their own

Two residual questions are device work rather than source work, and neither changes the
verdict:

- Landscape on a hole-punch phone. There is no `displayCutoutPadding()` and no orientation
  lock in `AndroidManifest.xml`, so the top card's 12 dp side padding is all that stands
  between the family name and a side cutout in landscape.
- The map is full-screen under both bars (`MainActivity.kt:838`) and is itself tappable, so a
  member's dot can land in the gesture-bar strip. Whether that tap reaches the map depends on
  the phone's navigation mode.

### Row 7, Touch, Touch Spacing (Medium)

**Rule.** At least 8 dp between adjacent tappable things.

**FAIL**, in one place.

Correct nearly everywhere: `MainActivity.kt:1039` (8 dp, plus the 1 dp divider with 6 dp
padding at `MainActivity.kt:1061-1066` between the pill and the gear),
`FamilyPage.kt:236` (8 dp), `MemberCard.kt:123` and `MemberCard.kt:181` (12 and 8 dp),
`FamilyPage.kt:189` and `FamilyRow.kt:227` (8 dp between list rows).

`FamilyRow.kt:95`, `PERSON_GAP = 4.dp`, is the exception, and it applies to both layout
branches (`FamilyRow.kt:102` and `FamilyRow.kt:111`). The family row's person columns are
64 to 72 dp wide and the whole column is the tap target (`FamilyRow.kt:187`), so adjacent
targets are 4 dp apart across the full 72 dp height. This is the control the family uses most,
on the phones with the smallest screens, and the people either side of your target are your
other relatives.

**Direction.** 8 dp, with `FIT_WIDTH` dropped to compensate so five still fit on a 720 px
screen. Whether five still fit after that is the one thing to check on a device.

### Row 30, Anti-Pattern, No Gesture-Only Actions (Critical)

**Rule.** No core action reachable only by a hidden gesture. Visible buttons as well as
gestures.

**PASS**, and this is the strongest row in the pass.

Every gesture in the app has a visible equivalent:

- The panel's swipe-down (`BottomPanel.kt:81-89`) is backed by a close button
  (`MemberCard.kt:116` for the card, `FamilyRow.kt:221` for the list), the back key
  (`MainActivity.kt:429`) and a tap on empty map (`MainActivity.kt:718-726`).
- The family row's sideways scroll is backed by the `<` and `>` buttons at
  `FamilyRow.kt:118-129`, each shown only while there is more that way.
- The tutorial's pager swipe is backed by the Next button at `Tutorial.kt:124-128`.
- Zooming back out after picking someone is a button, not a pinch
  (`MainActivity.kt:504-513`).

### Row 29, Theming, Light/Dark Contrast (High)

**Rule.** Enough contrast in both themes. Use semantic tokens; do not reuse light-theme greys
in dark mode.

**FAIL.**

The scheme work itself is right. `Theme.kt:38-74` is two full schemes, not one scheme with a
flag, and the dark values are genuinely re-derived rather than lifted.

The defect is the two app-specific colours that deliberately do not flip.
`Theme.kt:33-36` declares `MogarColors.FamilyGreen` and `MogarColors.Paused` as "The same in
both themes", which is fine while they are backgrounds and breaks when they meet a foreground
that does flip.

Two computable failures, both from hex values in the file:

- **`MainActivity.kt:1105-1121`, the sharing pill when sharing is off.** The disc is
  `MogarColors.Paused` (`#6E7482`) and both the pin glyph (`MainActivity.kt:1111`) and the
  slash across it (`MainActivity.kt:1115`) are tinted `colors.onPrimary`. In the light scheme
  `onPrimary` is white (`Theme.kt:40`): 4.7:1, fine. In the dark scheme `onPrimary` is
  `#0A1F5C` (`Theme.kt:59`), a dark navy: **3.3:1**. That is a 22 dp glyph on the one control
  that says whether your family can see you, and it is the "off" state, which is the state
  that matters.
- **`FamilyRow.kt:272`, the battery percentage in the See-all list.** `MogarColors.FamilyGreen`
  (`#26803F`) on the dark `surfaceContainerLowest` (`#1C1D23`, `Theme.kt:67`): **3.4:1** for
  15 sp bold text, against the 4.5:1 that size needs.

**Direction.** Not new colours for the scheme. Give `Paused` its own on-colour instead of
borrowing `onPrimary`, and give the battery readout a dark-scheme green, in the same shape
`Theme.kt` already uses for everything else. The point is the mechanism: a fixed colour used
as a foreground, or under one, cannot be theme-neutral.

**What still needs a device on this row:** 14 sp grey (`onSurfaceVariant`) on white as
actually rendered at `MemberCard.kt:147`; the white map pins and white edge-marker circles
(`Markers.kt:130-132`, `EdgeMarkers.kt:104`) over real map tiles, which stay light in both
themes because the OpenFreeMap style does; and whether the hardcoded `Color.White` avatar
fills (`People.kt:155`, `People.kt:161`) read as intended against the dark surfaces.

---

## Tier 2: the rest of accessibility

### Row 3, Accessibility, Role and Traits (High)

**Rule.** Interactive things should say what kind of thing they are.

**FAIL.**

`Role` appears exactly once, at `MainActivity.kt:1098` (`Role.Switch` on the sharing pill,
which is correct). Material 3's `Surface(onClick = ...)` supplies `Role.Button` on its own, so
every `NavRow` (`SettingsScreen.kt:351`), `PersonRow` (`FamilyRow.kt:249`), `ChoiceCard`
(`SetupScreen.kt:199`) and `ShowEveryoneButton` (`MainActivity.kt:1160`) is covered without
asking.

The six bare `Modifier.clickable` sites are not. They get an `onClick` action, so a screen
reader will offer to activate them, but they announce as text rather than as controls:

- `MainActivity.kt:1046` the family name, which is the only door to the family page
- `FamilyPage.kt:157` the family code (tap to copy)
- `FamilyPage.kt:217` the number row (tap to edit)
- `MemberCard.kt:190` the phone number (tap to copy)
- `FamilyRow.kt:187` a person in the family row
- `EdgeMarkers.kt:88` an off-screen member marker

The two tap-to-copy cases are the worst of the six, because their accessible name is just the
value ("+63 917 123 4567"), with nothing saying that tapping copies it. The visible hint
exists for the code (`strings.xml:150`) but is a separate node.

A smaller note on the one row that does use `Role`: `MainActivity.kt:1093` puts the state in
the `contentDescription` ("Sharing on" / "Sharing off") while `toggleable` also reports the
on/off state, so it is announced twice. `stateDescription` is the idiomatic split. Not a
defect, and the duplication errs in the safe direction.

**Direction.** `role = Role.Button` on the six `clickable` calls, and an `onClickLabel` of
"copy" on the two tap-to-copy ones.

### Row 4, Accessibility, Dynamic Updates (Medium)

**Rule.** Status that changes on its own should be announced, not just redrawn.

**FAIL.**

There is no `liveRegion`, no `announceForAccessibility` and no `stateDescription` anywhere in
the module. Every asynchronous status change in the app is silent:

- `MainActivity.kt:1132-1154` the sharing notice, which is the only place that explains why
  sharing switched itself off (`MainActivity.kt:390`) and the only place that says it came
  back (`MainActivity.kt:382`). It appears and leaves after 3 seconds with nothing said.
- `MapLoading.kt:61-73` the map going from loading to slow to failed.
- `FamilyPage.kt:171` and `MemberCard.kt:184` the "Copied" confirmation.
- `SettingsScreen.kt:229-237` the update row's subtitle, which walks through checking,
  found, downloading and failed.
- `People.kt:100-112` a member's status line changing from "Sharing" to "Phone quiet" on the
  30-second tick at `MainActivity.kt:299-304`.

**Direction.** `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` on the notice band
and the map overlay's text would cover the two that carry information the user cannot get any
other way. The rest are lower value.

### Row 5, Accessibility, Decorative Icons (Medium)

**Rule.** Hide decorative icons from screen readers. Do not have every icon read aloud.

**FAIL**, on one item, and only because that item is not actually decorative.

The decorative work is done properly: 20 `contentDescription = null` sites, which is Compose's
documented way of excluding a node, and they are genuinely decorative in each case. The
chevron on every nav row (`SettingsScreen.kt:362`), the pin on every list row
(`FamilyRow.kt:275`), the Setup hero icon (`SetupScreen.kt:94`), the Call and Text glyphs that
sit beside their own labels (`MemberCard.kt:197`, `MemberCard.kt:202`), the dialog icons
(`WhatsNew.kt:65`, `SettingsScreen.kt:376`, `FamilyPage.kt:325`), and the avatar image itself
(`People.kt:138`) where the parent carries the name.

`UpdateDot` is the exception. `Updates.kt:374-382` is a bare `Box` with a background and no
semantics at all, so it is invisible to a screen reader, which would be correct if it were
decoration. It is state: it means "a newer Mogar exists" or "the Keep-running steps are not
finished".

On the map that is handled, and handled well: `MainActivity.kt:1073` swaps the gear's
description to "Settings, something needs your attention" whenever the dot is up. Inside
Settings there is no equivalent:

- `SettingsScreen.kt:111`, the dot on the Keep-running row, whose subtitle is the static
  "Stop your phone from closing Mogar" (`strings.xml:81`). Nothing says the step is
  outstanding.
- `SettingsScreen.kt:245`, the dot on the update row. This one is saved by its subtitle, which
  already reads "Mogar 0.13 is out".

**Direction.** The pattern from `MainActivity.kt:1073` applied to the Keep-running row's
title, or a `contentDescription` on `UpdateDot` itself so it carries its own meaning wherever
it is placed.

### Row 31, Accessibility, Dragging Alternatives (High)

**Rule.** Anything reorderable, resizable or movable by dragging needs a non-drag path too.

**PASS, does not apply.** Mogar has nothing to reorder, resize or move. The only drag in the
app is the bottom panel's dismiss (`BottomPanel.kt:81-89`), which has three non-drag
alternatives (see row 30).

The catalogue row is also explicitly React Native plumbing ("route iOS and Android behavior
through the runtime platform adapter"), which has no Compose equivalent and should not be
given one.

---

## Tier 3: navigation and state

### Row 9, Navigation, Back Behavior (Critical)

**Rule.** Back should be predictable and should not reset the stack or drop you out of the app.

**PASS.**

Mogar has no navigation library; screens are booleans over the map, and each one registers its
own handler: `MainActivity.kt:429` (the bottom panel), `519` (family page), `538` (Settings),
`547` (Keep running), `554` (the tour). The nesting works out correctly: Settings and Keep
running can both be open, and because Keep running registers later it takes the back press
first and returns to Settings rather than to the map.

The declined-permission paths also land somewhere sane rather than leaving the UI lying. Backing
out of the system location prompt returns `granted = false`, which reaches
`onLocationSettled(false)` (`MainActivity.kt:647`) and then
`Family.pauseForPhone` plus an explanatory notice (`MainActivity.kt:389-390`), so the pill
cannot be left claiming to be on over a phone that just refused.

One deliberate exception, which the repo is right about: `ConsentDialog` has
`onDismissRequest = {}` (`Consent.kt:52`), so back does nothing until it is answered. The
reasoning at `Consent.kt:47-48` is that an unanswered box means nothing is sent, so dismissing
it would only bring it back next launch. Both answers are one tap away. Siding with the repo.

### Row 11, Navigation, Modal Escape (High)

**Rule.** Modals and sheets need an obvious way out.

**PASS.**

- Bottom panel: close button, swipe down, back key, tap the map (`BottomPanel.kt:41-45`
  documents all four).
- Full-screen overlays: back arrows at `FamilyPage.kt:126` and `SettingsScreen.kt:82`, a
  full-width Done at `KeepRunning.kt:319-323`, and Skip or Done at `Tutorial.kt:110` and
  `Tutorial.kt:124`.
- Dialogs: all have at least one dismissing button. `WhatsNew.kt:80` has only OK, which is
  correct for a notice.

`Tutorial.kt:110` hides Skip on the last page (`alpha(0f)` and `enabled = !last`) while the
primary button becomes Done, so there is never a page with no exit.

### Row 12, State, Preserve Screen State (Medium)

**Rule.** Coming back to a screen should restore its scroll position and form contents.

**FAIL.**

Two of the three state holders in the app are deliberate and correct. `familyRowScroll` is
hoisted at `MainActivity.kt:430` specifically so a scrolled family row survives a card opening
and closing, with a comment saying so. The map camera is deliberately left where it is when a
card closes (`MainActivity.kt:216-217`).

The gap is `remember` where `rememberSaveable` was meant. `MainActivity.kt:221` uses
`rememberSaveable` for `showSettings`, which shows the intent, but its four neighbours do not:

- `MainActivity.kt:220` `showKeepRunning`
- `MainActivity.kt:222` `showFamilyPage`
- `MainActivity.kt:224` `showTutorial`

A rotation or a process death on any of those three screens drops the person back on the map.
Keep running is the one that hurts: it is a multi-step flow whose whole purpose is sending the
person out to the phone's own settings pages and back, and `KeepRunning.kt:232-236` already
watches `ON_RESUME` precisely because that round trip happens. If the system reclaims the
activity while they are in vivo's settings, they return to the map.

The same applies inside Setup: `SetupScreen.kt:59-65` holds the typed name, phone, family name
and code in plain `remember`, so a rotation during first-run setup clears everything typed.

**Direction.** `rememberSaveable` for the four screen flags and the five Setup fields. There
is no orientation lock in `AndroidManifest.xml`, so rotation is reachable on all five phones.

### Row 10, Navigation, Bottom Tabs (Medium)

**Rule.** At most five primary items in a bottom tab bar.

**PASS, does not apply.** Mogar has no tab bar. Navigation is one map with full-screen overlays
over it, reached from the top card: the family name opens the family page
(`MainActivity.kt:1046`), the gear opens Settings (`MainActivity.kt:1069`). Two destinations,
both labelled. The rule has nothing to bite on.

### Row 8, Touch, Gesture Conflicts (High)

**Rule.** Custom gestures must not break the system's scroll or back gesture. Reserve
horizontal swipes for carousels.

**NEEDS-A-DEVICE.**

What source settles: Mogar's one custom gesture is vertical, not horizontal
(`BottomPanel.kt:82`, `detectVerticalDragGestures`), and it is scoped to the panel rather than
the screen. Its nested-scroll handling (`BottomPanel.kt:60-74`) only claims the pull once the
list inside can no longer scroll, which is the correct arbitration. The one horizontal swipe
is `HorizontalPager` in the tutorial (`Tutorial.kt:103`), which is exactly the carousel the
rule reserves horizontal swipes for.

What source cannot settle: the map fills the window (`MainActivity.kt:838`) and MapLibre
consumes pans across its whole surface. There is no `Modifier.systemGestureExclusion` and no
`windowLayoutInDisplayCutoutMode`, so how a swipe starting at the left or right edge resolves
between MapLibre's pan and the system back gesture is up to the phone's navigation mode and
the vendor's own gesture layer. The family's phones are not uniform here: a vivo on Android 16
and a vivo on Android 12 do not necessarily arbitrate the same way.

**What would settle it:** on each phone, with gesture navigation on, swipe in from the left
edge over the map and see whether you go back or the map pans. Then the same with three-button
navigation. If back loses, the fix is a gesture-exclusion strip, not a change to the map.

---

## Tier 4: the rest of forms

### Row 16, Forms, Inline Validation (Medium)

**Rule.** Validate on blur or on submit with a clear message. Do not validate on every
keystroke in a way that janks.

**FAIL.**

Mogar does neither. There is no `isError` and no `onFocusChanged` anywhere. Validation is
expressed only as a disabled button:

- `SetupScreen.kt:151` `enabled = !busy && name.isNotBlank() && familyName.isNotBlank()`
- `SetupScreen.kt:174` `enabled = !busy && name.isNotBlank() && code.length == 6`
- `FamilyPage.kt:412` `enabled = !busy && value.isNotBlank() && value.trim() != initial`
- `FamilyPage.kt:375` `enabled = !busy && digits != Phone.digits(initial)`

The jank half of the rule is satisfied, and the approach is defensible for this audience: a
button that cannot be pressed wrongly beats a red message. But nothing ever says why it cannot
be pressed. A grey "Join family" button under a five-character code is the failure mode, and
the person affected is the one joining for the first time.

`SetupScreen.kt:218`'s "Type the 6-letter code" subtitle is the nearest thing to a message,
and it is on the card header rather than the field.

**Direction.** A `supportingText` on the code field that counts (the field already knows the
length), rather than full inline validation. The field has no `supportingText` today while
both of its neighbours do.

### Row 17, Forms, Keyboard Type (Medium)

**Rule.** Match the keyboard to the field, and set a sensible return key.

**FAIL**, on the second half and on one inconsistency.

Correct: `SetupScreen.kt:124` and `FamilyPage.kt:369` both use `KeyboardType.Phone` for the
number, `SetupScreen.kt:111` and `SetupScreen.kt:146` use `KeyboardCapitalization.Words` for
the two names, and `SetupScreen.kt:169` uses `KeyboardCapitalization.Characters` for the family
code, which is right: the code alphabet is 24 uppercase letters (`Family.kt:46`), so a letters
keyboard in caps is a better fit than any `KeyboardType`.

Two gaps:

- No field anywhere sets an `imeAction`. All eight text fields get the default return key.
- `FamilyPage.kt:401-409`, the name dialog, passes no `keyboardOptions` at all, so it loses the
  `KeyboardCapitalization.Words` that the identical field on the Setup screen has
  (`SetupScreen.kt:111`). Changing your name later gets a lowercase keyboard; typing it the
  first time does not.

**Direction.** Carry the Setup field's `keyboardOptions` into the dialog, and
`ImeAction.Done` on the single-field dialogs so the return key submits.

### Row 18, Forms, Auto Focus and Next (Low)

**Rule.** Walk the person through a form; do not make them tap every field.

**FAIL.**

No `FocusRequester`, no `focusRequester`, no `KeyboardActions`, no `autoFocus` anywhere in the
module.

Setup has three fields in sequence (name, phone, then either the family name or the code) and
each must be tapped individually, with the keyboard dismissed in between. The three dialogs
(`FamilyPage.kt:355`, `FamilyPage.kt:385`) each open with a single field unfocused, so the
person taps a one-field dialog to raise the keyboard before they can type.

This is the catalogue's only Low-severity row and it is correctly rated: it costs taps, not
comprehension. Worth fixing only where it is nearly free.

**Direction.** The single-field dialogs are the cheap win: a `FocusRequester` requested in a
`LaunchedEffect`, so a dialog that exists solely to type one value opens ready to type.

### Row 19, Forms, Password Visibility (Medium)

**Rule.** Let people toggle a password between hidden and shown.

**PASS, does not apply.** Mogar has no password field and no credential of any kind. Identity
is anonymous Firebase auth (`Family.kt:144-148`), family membership is a six-letter code, and
no `visualTransformation` or `secureTextEntry` equivalent appears in the module. Nothing is
typed blind.

### Row 32, Forms, Authentication Reuse (Critical)

**Rule.** Do not make people retype what they have already given. Support paste and prefilled
values.

**FAIL**, on the paste half.

Reuse is handled where it matters: `SetupScreen.kt:59-60` prefills the name and phone from
whatever this phone already saved, so leaving and rejoining a family does not mean retyping,
and `Family.kt:184` carries the kept photo into the new member record for the same reason.
There is no credential to reuse, so the first half of the rule is moot.

The code field breaks on paste, and this is the exact flow the row describes. The invite
travels as text: `FamilyPage.kt:159` copies it to the clipboard, and the hint at
`strings.xml:150` tells the person to send it to family in a chat. At the other end,
`SetupScreen.kt:165` is `code = it.uppercase().take(6)`.

`take(6)` runs before anything trims. A code arriving from a chat message with a leading
space, a trailing newline, or a surrounding quote becomes six characters that include the
whitespace. `Family.kt:170` does `typedCode.trim().uppercase()`, but by then one real letter
has already been cut: `" ABCDEF"` becomes `" ABCDE"`, which passes
`code.length == 6` at `SetupScreen.kt:174`, trims to five, and fails with
"No family with the code ABCDE". The field also accepts digits and punctuation that can never
appear in a code, since the alphabet is letters only and excludes I and O (`Family.kt:46`).

The person who hits this is joining for the first time, pasting from Messenger, and the
message they get names a code they did not type.

**Direction.** Filter to the code alphabet in `onValueChange` before `take(6)`, exactly the
way `Phone.clean` (`Phone.kt:18`) already filters to digits before `take(10)`. The pattern is
in the repo; it just is not applied here.

---

## Tier 5: animation

### Row 23, Animation, Duration and Easing (Medium)

**Rule.** Micro-interactions 150 to 300 ms, with native-feeling easing: ease-out entering,
ease-in leaving.

**PASS**, with one note, and the easing half is where the row and the repo disagree.

Durations are all in range or defensibly outside it:

- `MainActivity.kt:433-435` and `453-455`, the panel and family box sliding past each other,
  `tween(300)`, at the top of the band.
- `MainActivity.kt:1136-1137`, the notice, `fadeIn(120)` and `fadeOut(220)`: faster in than
  out, which is the right asymmetry.
- `MainActivity.kt:796`, `811`, `821`, camera flights at 700 ms. Outside the band, but these
  are map camera moves across real distance, not micro-interactions. The band does not apply
  and the repo is right.
- `MapLoading.kt:45` and `87`, the loading ripple at 2400 ms with `LinearEasing`. Linear is
  correct for a loader; a loader that eases would read as stuttering.

The one item genuinely over the band: `MainActivity.kt:459`, `Crossfade` with no
`animationSpec`, which is Compose's 400 ms default. It fires when the bottom panel switches
between a member card and the See-all list.

On easing, the row asks for ease-out entering and ease-in leaving. Mogar's `tween(300)` calls
take Compose's default, `FastOutSlowInEasing`, in both directions. That is Material's standard
easing and the Android-native answer, so following the catalogue here would make the app feel
less like the platform, not more. Siding with the repo.

### Row 24, Animation, Respect Reduced Motion (Critical)

**Rule.** Honour the OS reduced-motion setting and simplify animation when it is on.

**FAIL.**

Nothing in the module reads it. No `LocalAccessibilityManager`, no
`Settings.Global.ANIMATOR_DURATION_SCALE`, no `TRANSITION_ANIMATION_SCALE`. Every animation
runs unconditionally:

- `MapLoading.kt:82-105`, an infinitely repeating three-wave ripple, which is the most
  motion-heavy thing in the app and is shown precisely when the phone is struggling
- `MainActivity.kt:433-455`, the two sliding boxes
- `MainActivity.kt:788-797` and `806-822`, the 700 ms camera flights
- `Tutorial.kt:125`, `animateScrollToPage`
- `BottomPanel.kt:79`, `animateContentSize`

Worth stating plainly: Compose has no first-class reduced-motion API, so this is not a missing
one-liner. The Android route is to read the animator duration scale, or
`AccessibilityManager.isReduceMotionEnabled` where available, once and hoist it.

**Direction.** One hoisted boolean, used in the two places that matter most: swap the ripple
for the still pebble that `MapLoading.kt:111-126` already draws, and drop the slide transitions
to fades. The rest can stay as they are.

### Row 25, Animation, Limited Continuous Motion (Medium)

**Rule.** Infinite animation is for loaders and live data, not decoration.

**PASS.**

The module has exactly one `rememberInfiniteTransition`, at `MapLoading.kt:83`, and it is a
loader. It stops properly: `MapLoading.kt:53` returns before composing anything once the state
is `DONE`, so the transition leaves the composition rather than running behind a hidden
overlay. The failed and slow states swap to the still mark (`MapLoading.kt:60`,
`MapLoading.kt:111-126`), so a phone that cannot reach the map is not left with something
spinning at it forever.

No decorative loop anywhere else. The 30-second recheck at `MainActivity.kt:299-304` is state,
not motion.

---

## Tier 6: performance

### Row 20, Performance, Virtualize Long Lists (High)

**Rule.** Use a virtualizing list past roughly 50 items, with stable keys.

**PASS.**

Every list that can grow is lazy and keyed: `FamilyRow.kt:111-114` (`LazyRow`, keyed on uid),
`FamilyRow.kt:225-231` (`LazyColumn`, keyed), `FamilyPage.kt:186-193` (`LazyColumn`, keyed).

`FamilyRow.kt:100-107` is the interesting choice: up to five people it uses a plain `Row` with
weights so everyone is evenly spread, and only switches to the `LazyRow` past that. That is
correct, not a shortcut: virtualizing five fixed items buys nothing and costs the even spread.

The non-lazy scrolling columns (`SettingsScreen.kt:76`, `SetupScreen.kt:87`,
`KeepRunning.kt:248`, `FamilyPage.kt:119`) each hold a bounded handful of rows written out
literally in source. Nothing there can grow with data.

### Row 21, Performance, Image Size and Cache (Medium)

**Rule.** Correctly sized, cached images. Do not load full-resolution images everywhere.

**PASS.**

- Profile pictures are downsampled at the source: `Photos.kt:15-42` decodes with a target
  sample size, crops to the middle square, scales to 128 px and compresses to JPEG at quality
  82. `Photos.kt:13-14` explains why 128: the largest circle it ever fills is 56 dp.
- Decode results are remembered per content key: `People.kt:127`,
  `remember(Photos.key(photo))`, with the key derived from size and content hash
  (`Photos.kt:48`), so it invalidates when the picture changes and not otherwise.
- Map markers are cached by MapLibre under a content-derived id:
  `MainActivity.kt:949-951` only calls `Markers.draw` when `style.getImage(id)` is null, and
  the id at `Markers.kt:44-46` folds in state, selection, photo key, colour and name.

Tutorial slides are seven WebP files in `drawable-nodpi`, 23 to 180 KB each, at 720x1600
(`Tutorial.kt:73-74`). `nodpi` means no density scaling, which is the right call here because
the target phones are 720x1600: the asset is 1:1 rather than upscaled or wastefully
downsampled. `HorizontalPager` keeps roughly three pages composed, so about three decoded
bitmaps are live at once.

**Worth a device check, not a defect:** three 720x1600 ARGB bitmaps is roughly 14 MB resident
while the tour is open, on the vivo 1910 (Android 12, the oldest and smallest-memory phone).
The tour is shown once after setup and then only on request, so the exposure is small.

### Row 22, Performance, Debounce High-Frequency Events (Medium)

**Rule.** Throttle or debounce scroll and search callbacks. Do not run heavy logic on every
event.

**FAIL**, on one site.

Mogar throttles where it costs money or battery, deliberately and with comments: location
uploads are capped at one a minute (`MainActivity.kt:138` and `MainActivity.kt:884`), the
update check is once a day (`MainActivity.kt:281`), and the staleness clock ticks every 30
seconds rather than every frame (`MainActivity.kt:299-304`).

The exception is `MainActivity.kt:706`, `m.addOnCameraMoveListener { project(m) }`. That fires
on every frame of every pan and every zoom, and `project` (`MainActivity.kt:674-680`) walks all
members, calls `projection.toScreenLocation` for each, allocates a new `Map`, and pushes it
into the `dotPoints` state at `MainActivity.kt:356`. That recomposes `EdgeMarkers`
(`MainActivity.kt:402-411`) once per frame, and `EdgeMarkers` is not trivial: it deflates two
rects, and for each off-screen member runs a clash-resolution loop over everyone already
placed (`EdgeMarkers.kt:65-72`).

With five members this is small, and it may well never be the thing that makes the map feel
slow on a Galaxy A13. But it is per-frame allocation plus recomposition during the one gesture
the app is built around, and the idle listener on the next line (`MainActivity.kt:707`) already
covers the case where an exact answer is needed.

**Direction.** Keep the idle listener exact and make the move listener cheap: skip the update
when no projected point has moved more than a pixel or two, or coalesce it to a frame
boundary. Nothing about the edge markers needs to be exact mid-gesture.

---

## Noticed, outside the catalogue

Three things that no catalogue row covers. None is acted on here.

**The app's manifest theme is light-only.** `AndroidManifest.xml:45` sets
`android:theme="@android:style/Theme.Material.Light.NoActionBar"`. That is the window
background Android paints before Compose draws anything, so a phone set to dark should show a
white flash on every cold start, after which `MogarTheme` (`Theme.kt:139`) takes over with the
dark scheme. The theme setting at `SettingsScreen.kt:93-102` offers Dark explicitly, so this is
reachable deliberately and not only via the system setting. A device would confirm how visible
the flash is; the cause is one line.

**The last-crash line on the member card is the only untranslated string in the UI.**
`MemberCard.kt:153`, `"Last crash: $crash"`, is a Kotlin literal rather than a string resource,
and it is the one user-visible string in the module that is not in `strings.xml`. With the
Filipino localisation scrapped this costs nothing today. It is worth knowing it is the single
exception, because it is the thing that will be missed if a second language is ever
reconsidered. It also renders raw exception text to a non-technical reader, in red, on the
person's card.

**Setup cannot be left.** `MainActivity.kt:188-192` shows `SetupScreen` whenever there is no
saved family code, and `SetupScreen` registers no `BackHandler`. Back therefore falls through
to the activity and closes the app. That is arguably correct (there is nowhere to go back to
on first run), but it means a person who opens Mogar, is unsure, and presses back has to
relaunch, and because the Setup fields are plain `remember` (row 12) whatever they typed is
gone. Not in scope for any of the 32 rows, but it sits next to the pairing flow that is being
built.

---

## Scope notes

**What was deliberately not done, per the brief:** the skill was not installed, `uipro init`
was not run, no generator from the catalogue repo was executed, and nothing from it was written
into this repo or any config. `data/stacks/jetpack-compose.csv` (52 rows of Compose
engineering hygiene) was not read. No React Native code is proposed anywhere above, and no
Compose rewrite is modelled on a catalogue snippet. The catalogue repo was cloned read-only to
`/home/user/nextlevelbuilder/ui-ux-pro-max-skill`, outside this repo.

**Where the catalogue was overruled:** row 23 (easing), row 10, row 19 and row 31 (rules that
do not apply), and the parts of rows 9 and 11 that would argue against the consent dialog
being unskippable. Each is argued in place.

**Rows that remain open after this pass:** 27 (font-scale reflow) and 8 (edge-swipe
arbitration over the map), plus the named device follow-ups inside rows 28, 29 and 21. Those
belong to the laptop half of the split, where the emulator and the real phones are.
