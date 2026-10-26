# Mogar UI/UX pass, follow-up, 2026-10-26

Re-scores 8 rows from `design-review/ui-ux-pass-2026-10.md` (branch
`claude/determined-cerf-538my9`, commit `4f19cbf`, Mogar read at `3cf4260`) against the
pairing-flow build that landed after it (`17d7c44`, `bf5761c`). Mogar read here at `bf5761c`
(main). Same catalogue, same rules, same source-only scope: nothing was built, signed,
installed or run, and no app code was changed by this pass either.

**Note on scope before the results.** Two of the eight rows this pass was asked to re-check,
row 1 (Icon Button Labels) and row 19 (Password Visibility), were **PASS** in the original
pass, not FAIL — row 1 outright, row 19 as "does not apply" (Mogar has no password field).
The other six (5, 7, 12, 22, 24, 29) were FAIL, as expected. Both are scored below anyway,
since the brief named them by row number and category and those match exactly; the point of
re-checking "does the original verdict still hold" works the same whether that verdict was
PASS or FAIL. Flagging it here rather than silently correcting it, since it changes what
"re-score" means for those two rows: confirm a pass, not chase a fail.

## Result

All eight verdicts are unchanged. Six FAILs stand on the same defect, in the same shape, as
the original pass; two PASSes stand because the pairing build extended the same pattern
correctly rather than breaking it. Every citation below is current against `bf5761c`; six of
the eight needed a line-number update because the build restructured `FamilyPage.kt`,
`FamilyRow.kt`, `SetupScreen.kt`, `People.kt` or `Family.kt` upstream of the cited line. In
every case the surrounding code the citation was about is byte-identical to the original pass
— only insertions above it moved the line down. Confirmed directly: diffing each touched file
against `3cf4260` shows no hunk touching the cited logic in any of the eight rows; the lines
either sit in a file the build never touched, or sit below the build's insertions with nothing
else changed around them.

| Row | Category | Verdict | Shifted? |
|---|---|---|---|
| 1 | Accessibility, Icon Button Labels | **PASS** (unchanged) | Yes, citations only |
| 5 | Accessibility, Decorative Icons | **FAIL** (unchanged) | Yes, citations only |
| 7 | Touch, Touch Spacing | **FAIL** (unchanged) | Yes, citations only |
| 12 | State, Preserve Screen State | **FAIL** (unchanged, wider) | Yes, citations only |
| 19 | Forms, Password Visibility | **PASS, does not apply** (unchanged) | Yes, citations only |
| 22 | Performance, Debounce High-Frequency Events | **FAIL** (unchanged) | No, file untouched |
| 24 | Animation, Respect Reduced Motion | **FAIL** (unchanged) | No, file untouched |
| 29 | Theming, Light/Dark Contrast | **FAIL** (unchanged) | Yes, one citation |

---

### Row 1, Accessibility, Icon Button Labels

**PASS**, still.

Every icon-only control the original pass found labelled is still labelled, at these current
lines: `MainActivity.kt:1073`, `:1093`, `:1099` (unchanged file), `FamilyPage.kt:140` (back
arrow, was `:127`), `SettingsScreen.kt:83` (unchanged), `FamilyPage.kt:157-158` (rename pencil,
was `:145`), `FamilyRow.kt:176` (scroll arrows, was `:163`), `FamilyRow.kt:235` (close-list
chevron, was `:222`), `MemberCard.kt:117`, `EdgeMarkers.kt:83` and `:89` (unchanged),
`FamilyRow.kt:200-201` (the `.clickable{}.semantics{}` pair, was `:187`), `EdgeMarkers.kt:88`
(unchanged).

**What shifted and why it still passes.** The pairing build added two new icon-only controls to
`FamilyRow.kt`'s `PersonRow` and `PairedChildRow` (expand/collapse a paired profile's other
phones, and unpair a device), and both were given names on arrival:
`FamilyRow.kt:319` (`R.string.expand_paired_devices` / `collapse_paired_devices`) and
`FamilyRow.kt:354` (`R.string.unpair_device`). The build extended the pattern this row already
scored a PASS on rather than breaking it.

### Row 5, Accessibility, Decorative Icons

**FAIL**, still, on the same single item.

The decorative sites the original pass checked are all still `contentDescription = null` and
still genuinely decorative, now at `SettingsScreen.kt:362` (unchanged), `FamilyRow.kt:324`
(the leaf location pin, was `:275`), `SetupScreen.kt:99` (hero icon, was `:94`),
`MemberCard.kt:197` and `:202` (unchanged), `WhatsNew.kt:65` and `SettingsScreen.kt:376`
(unchanged), `FamilyPage.kt:275` and `:352` (the pair-device and leave-family icons sitting
beside their own `NavRow`/dialog text, was `:325` for the one of these that existed before),
`People.kt:158` (avatar image, was `:138`).

The exception is unchanged too: `UpdateDot` (`Updates.kt:374-382`, untouched by the build) is
still a bare `Box` with no semantics, standing for state rather than decoration, with Settings'
two instances handled the same partial way as before (`SettingsScreen.kt:111`, `:245`,
unchanged).

**What's new and correctly decorative, not a regression.** `FamilyPage.kt:275`'s `Share` icon
on the new "Pair another device" `NavRow`, and `SetupScreen.kt`'s new `Share` icon on the
"I have a pairing code" `ChoiceCard` (`SetupScreen.kt:188`) both sit beside their own title
text inside a `Surface(onClick = ...)`, the exact shape every other `NavRow`/`ChoiceCard` icon
already uses. Correctly `null`, not a new instance of the defect.

### Row 7, Touch, Touch Spacing

**FAIL**, still, in the same one place, unchanged code.

`PERSON_GAP = 4.dp` is still 4 dp, now at `FamilyRow.kt:108` (was `:95`), still applied to both
layout branches (`FamilyRow.kt:115` and `:124`, was `:102` and `:111`), still the full-column
tap target at `FamilyRow.kt:200` (was `:187`). Diffing `FamilyRow.kt` against `3cf4260` shows
the `People`/`PersonColumn`/`ScrollArrow` composables untouched; the pairing build's changes are
entirely below them, in `FamilyList`/`PersonRow`/the new `PairedChildRow`. The build did not
touch the family row's person columns at all.

The rest of the row's correct 8 dp spacing holds too, now at `FamilyPage.kt:206` and `:253`
(was `:189` and `:236`) and `FamilyRow.kt:230` and `:240` (was `:227`). `MainActivity.kt:1039`
and `:1061-1066`, `MemberCard.kt:123` and `:181` are unchanged.

### Row 12, State, Preserve Screen State

**FAIL**, still, and the surface it covers grew by one field.

`MainActivity.kt:220-224`, `:430` and `:216-217` are unchanged: the four screen-flag booleans
are still plain `remember` except `showSettings`, and the map camera is still deliberately left
alone. What moved is `SetupScreen.kt:59-65`, now `SetupScreen.kt:63-70`: the build added a third
setup path ("I have a pairing code") with its own `pairCode` field, declared the same way as its
four siblings — `var pairCode by remember { mutableStateOf("") }` — and replaced the old
`joining: Boolean` with a `mode: SetupMode` enum, also plain `remember`. Diffing confirms this:
the five original fields (`name`, `phone`, `familyName`, `code`, `joining`/now `mode`) are
untouched in kind, and `pairCode` was inserted using the identical unfixed pattern rather than
`rememberSaveable`. A rotation during first-run setup now drops six typed/selected values
instead of five, not fewer.

### Row 19, Forms, Password Visibility

**PASS, does not apply**, still.

`Family.kt:147-151` (was `:144-148`) is still anonymous Firebase auth, unchanged in substance —
the only lines inserted before it in the whole file are an `import java.util.UUID` and two new
fields on `Member` (`personId`, `pairedAtMillis`), which is the entire source of the 3-line
shift.

The pairing build's own new credential-shaped thing, the pairing code, does not change the
verdict either. It is typed in plain text with no `visualTransformation`
(`FamilyPage.kt:432-444`, `SetupScreen.kt:192-201`), exactly like the existing family join code
that the original pass already judged not a password: a short link code meant to be read and
shared, not a secret meant to be hidden. Nothing in the module gained a field that needs a
show/hide toggle.

### Row 22, Performance, Debounce High-Frequency Events

**FAIL**, still, on the same one site, no citation changes at all.

`MainActivity.kt` is untouched by the pairing build (confirmed by diff against `3cf4260`), so
`MainActivity.kt:706`'s per-frame camera-move listener, `:674-680`'s `project`, `:356`'s
`dotPoints` state, and `EdgeMarkers.kt:65-72`'s clash-resolution loop are all exactly as they
were. The build added no new scroll, search, or camera-type high-frequency callback anywhere in
`FamilyPage.kt`, `FamilyRow.kt` or `SetupScreen.kt` — the new paired-device list uses the same
`LazyColumn`/`LazyRow` recomposition path the original pass already separately scored (row 20,
out of scope here), not a raw per-event listener.

### Row 24, Animation, Respect Reduced Motion

**FAIL**, still, on the same sites, no citation changes at all.

`MapLoading.kt`, `MainActivity.kt`, `Tutorial.kt` and `BottomPanel.kt` are all untouched by the
pairing build (confirmed by diff against `3cf4260`), so every animation the original pass found
unconditional is still unconditional at the same lines. The build's own new UI in `FamilyRow.kt`
(expand/collapse of a paired profile's other phones) uses a plain `if (expanded)` with no
`AnimatedVisibility` or `animateContentSize` at all — not an addition to this row's list, and
not an improvement either, just a toggle with no motion to gate.

### Row 29, Theming, Light/Dark Contrast

**FAIL**, still, on the same two sites.

`MainActivity.kt:1105-1121` (the sharing pill's dark-mode glyph on `onPrimary`) and `Theme.kt`
(`:33-36`, `:38-74`, `:40`, `:59`, `:67`) are untouched by the pairing build. The second
computable failure moved: `FamilyRow.kt:272`'s battery-percentage block is now
`FamilyRow.kt:308-313`, with the actual defect on line `312`:
`color = if (battery < LOW_BATTERY) colors.error else MogarColors.FamilyGreen`. Still the same
hardcoded `MogarColors.FamilyGreen` (`#26803F`) foreground against the dark scheme's
`surfaceContainerLowest` (`#1C1D23`), still 3.4:1 against the 4.5:1 that 15 sp bold text needs.
This is the `PersonRow` composable the pairing build restructured most (it now branches on
`childCount` to show either the new expand arrow or the plain location pin), but the battery
text sits above that branch, untouched by it.

The new `PairedChildRow` introduces one new piece of conditionally-colored text
(`FamilyRow.kt:346-350`, the paired device's label turning red when its location is off), and it
uses `colors.error` and `colors.onSurfaceVariant` — semantic tokens, not a hardcoded hex — so it
does not add a third site to this row.

The "needs a device" residue from the original pass is unchanged too: `MemberCard.kt:147`,
`Markers.kt:130-132`, `EdgeMarkers.kt:104` are untouched, and `People.kt:155` and `:161`'s
hardcoded `Color.White` avatar fills are now `People.kt:175` and `:181` (the entire shift in
this file is one 20-line, UI-free function — `groupPeople`/`PersonGroup` — inserted above them;
the `Avatar` composable itself is byte-identical).

---

## Scope notes

Rows 10, 19 (above), 27 and the rest of the original 32 are out of scope for this pass except
where named. Rows 2, 3, 4, 6, 13, 15, 16, 17 and 18 were fixed by the pairing build and are
treated as closed, not re-checked here. No app code was read for anything beyond confirming
these eight rows; nothing was changed.
