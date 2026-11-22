# Daybreak: the KEPT visual redesign

Status: approved by the owner on 2026-11-21. This spec replaces the look and feel described in
`2026-09-05-kept-android-design.md`. It does not change the rules (lock window, give-up time,
breaks, shield, streak, forms) except where the "Behaviour changes" section says so.

## Where the design lives

- **Source files (the source of truth for every value):** `docs/design/daybreak/*.dc.html`.
  Each file is one screen. Inline `style="…"` attributes hold the exact sizes, colours, radii
  and copy. The `<script>` block at the bottom of each file holds the state logic (what
  changes when a habit is ticked, what each recap outcome shows). Read the file before you
  build its screen; do not re-derive values from screenshots.
- **The live canvas (owner can open it, agents cannot):**
  https://claude.ai/artifact/4Skv5tuTPgCpEymxyJUT6a . Every artboard is clickable in Play mode.
- Units: the mocks are 390 × 844 CSS px. Treat 1 px as 1 dp, and font px as sp.
- `SprigA.dc.html` is the reference implementation of the new Sprig, including the animation
  loop. Port it; do not redesign it.

| Artboard file | Screen | Story |
|---|---|---|
| `A1-Welcome` | Welcome (first launch) | Onboarding |
| `A10-Onb-Habits` … `A14-Onb-Ready` | Onboarding steps 1 and 3 to 6 (step 2, the shutdown time from #164, has no mockup) | Onboarding |
| `Main` | Home, 1 to 3 habits | Home |
| `A2-Home-6` | Home, 4 or more habits | Home |
| `A18-Parts` | Home banners, habit row states, buttons | Foundation, Home |
| `A3-Lock` | Lock screen (Tweaks: `habitCount`) | Lock |
| `A4-Break`, `A4-Break-last` | Break the lock, and the 4th break of the week | Break |
| `A5-Recap*` | Recap: kept, slipped, shield, lock off, too late, written off | Recap |
| `A16-Celebrate` | Promise kept moment | Moments |
| `A17-Evolve` | Sprig reaches a new form | Moments |
| `A6-Collection` | Sprig tab | Collection |
| `A7-Settings`, `A15-History`, `A12-Exceptions-settings`, `A13-Permissions-settings` | Settings tab and its pages | Settings |
| `A8-Habits`, `A9-Habit-edit` | Your habits, Add or edit a habit | Habits |

## Owner decisions (2026-11-21)

1. One feature card with one story per area below. Each story ships as its own PR.
2. No Android SDK or emulator in the design session; the dev agent builds and tests as usual.
3. Screens not in the first round of mocks were designed in this round (see the table).
4. All behaviour changes below are approved.
5. Replace the Sprig renderer with the Daybreak Sprig, including interactions.
6. Outfits per form are set (see Sprig).
7. **Weekly looks are dropped for now** (colour + accessory per ISO week). They may come back later.
8. Font: Bricolage Grotesque, bundled.
9. **Dark mode is removed.** The app is light only.
10. Respect reduce-motion; add light haptics.

## Behaviour changes

- **Lock screen:** habits can be ticked on the lock screen itself. "Open KEPT" is removed. A
  photo-proof habit opens the camera straight from the lock screen. When the last habit is
  ticked the lock screen turns into the open state with one button, "Open {app}".
- **Home:** the day and date are gone from the top. The "Shield" pill becomes a small
  "Shield ready" text button that opens an explanation.
- **Home layout** switches to the compact layout at **4 or more** habits.
- **Recap:** "Time off apps" is no longer shown anywhere (recap and share card). Keep recording
  `lockedMillis`; only the display goes.
- **Collection:** "Looks you collected" is removed. The roadmap ("How Sprig grows") screen is
  removed; Collection shows all seven forms instead. Tapping Sprig on Home no longer opens
  the roadmap, because Sprig is now interactive.
- **Weekly looks removed:** no variant colour, no accessory, no "this week's look" text.
- **Dark mode removed.**

## Design tokens

### Colour

| Token | Hex | Use |
|---|---|---|
| ink | `#1E1B3C` | Primary text, primary button |
| inkSoft | `#3A3658` | Secondary text on coloured skies |
| muted | `#5C5878` | Secondary text on cream |
| faint | `#8D88A8` | Chevrons, unticked ring |
| cream | `#FFF9F1` | Page ground, hill, text on dark buttons |
| line | `#EFE4D6` | Dividers, tab bar top border |
| lineSoft | `#F1E8DC` | Card borders, row dividers |
| field | `#E6DCCE` | Input borders |
| rowLine | `#EADFD2` | Unticked habit row border |
| purple | `#5B4BD6` | Accent, ticked dot, selected ring, active tab |
| purpleTint | `#ECE9FF` | Done row, chips, current form card |
| purplePale | `#F3EFFF` | Progress card, stepper ground |
| purpleTrack | `#DCD6FA` | Progress bar track |
| purpleDone | `#B9B0F7` | Done row border |
| ring | `#CFC8E6` | Toggle off track, dashed borders |
| disabled | `#B9B4CC` | Disabled primary button |
| streakGold | `#FFB81F` | Kept day dot |
| shieldBlue | `#2F6FC4` / tint `#D9E6FA` | Shield text, shielded day |
| danger | `#A32D2D` / tint `#FBE3E3` | Lock is off, unlock anyway, costs |
| amber | `#B26A00` / tint `#FFF0D6` | Too late, streak flame |
| green | `#1F7A45` / `#2FA866` / tint `#E6F5EC` | Granted, all set |

Skies (the top band of each screen):

| Sky | Hex | Where |
|---|---|---|
| night | `#2B2760` | Home and Lock with nothing done |
| progress ramp | `#2B2760 #3E3478 #6E5590 #B57F98 #E9A99A #FFD0A6 #FFC23D` | Home: index `round(done/total × 6)` |
| lock ramp | `#2B2760 #332D6C #3E3478 #4A3E86 #5A4A9A` | Lock while locked: index `round(done/total × 4)`; gold when all done |
| gold | `#FFC23D` | All done, Recap kept, Promise kept moment |
| dawn | `#FFD0A6` | Welcome, onboarding step 6 |
| overcast | `#CFCBDD` | Break, Recap slipped and written off |
| tide | `#BFD9F2` | Collection, Recap shield |
| lilac | `#ECE9FF` | Settings |
| lock ground | `#1B1842` | Lock screen hill and ground while locked |

Text on a sky is `#FFF4E4` when the sky index is below the halfway point, else ink.

### Type

Bricolage Grotesque (Google Fonts, SIL OFL 1.1), variable axes `opsz 12–96`, `wght 400–800`.
Source: https://github.com/google/fonts/tree/main/ofl/bricolagegrotesque . Bundle the static
weights 500, 600, 700, 800 as `res/font/` (or the variable file) and include `OFL.txt` in
`app/src/main/assets/licenses/`. Use tabular figures for timers.

| Role | Size / line height | Weight | Tracking |
|---|---|---|---|
| Display | 52 / 0.96 | 800 | −0.03em |
| Headline | 38–46 / 1.0 | 800 | −0.02em |
| Page title | 34–40 / 1.02 | 800 | −0.02em |
| Section | 18 | 700 | 0 |
| Row title | 16–17 | 700 | 0 |
| Body | 15–17 / 1.4 | 500 | 0 |
| Caption | 13 | 500–600 | 0 |
| Small label | 12 | 600–700 | 0 |
| Timer | 84 | 800 | −0.03em, tabular |

Never all caps. Sentence case everywhere.

### Shape and spacing

- Page side padding 20 (lists) or 24 (text). Gaps 8 between rows, 22–28 between groups.
- Radii: habit rows 18, cards and setting groups 22, progress card 24, icon tiles 14, pills
  15–16, buttons fully round (height / 2).
- Buttons: primary 56 tall, ink fill, cream text 17/700. Secondary: 2dp ink outline. Quiet:
  text only. Disabled: `#B9B4CC` fill.
- Touch targets at least 48dp even when the visual is smaller (for example "Shield ready").

### The hill

Every sky ends in a hill: an ellipse top, cream (or the lock ground colour) over the sky.
Geometry: width = screen width + 120dp (60 overhang each side), vertical radius 48dp, the hill
shape extends 4dp below the sky's bottom edge so no sky colour can bleed through at the seam
(a real bug in the first mock pass). Sprig is drawn between the sky and the hill so the hill
hides Sprig's lower body. Compose: `Canvas` path with `arcTo` on a rect of
`(-60, 0, w + 60, 96)` filled down to the bottom.

### Motion

- One orchestrated moment per screen at most (Sprig rising on Break, petals on Recap, pop on
  the evolution). Everything else answers a touch.
- Sky colour, Sprig height and hill colour animate over 700ms (`FastOutSlowIn`). Sprig's
  height change uses an overshoot (`cubic-bezier(0.3, 1.5, 0.5, 1)`, 800ms).
- When the system animator scale is 0 or "Remove animations" is on: no petals, no rise, no
  pop, no idle sway; Sprig still blinks and still changes pose.

### Haptics

- Ticking a habit: `HapticFeedbackConstants.CONFIRM` (API 30+), else `KEYBOARD_TAP`.
- Sprig hop: `CLOCK_TICK`. Unticking: nothing.

## Sprig (Daybreak)

Port `docs/design/daybreak/SprigA.dc.html`. Drawn in a 120 × 120 unit box with a `Canvas`,
scaled to the composable size. The body path runs below the box and is clipped at the
bottom edge only, so hops can rise above the box without clipping the sprout.

- **Body:** `M16 160 V84 C16 52 34 34 60 34 C86 34 104 52 104 84 V160 Z`, `#8B7CF6`.
- **Arms:** two 12-unit round strokes, `#6B5BE0`, pivot at (24, 90) and (96, 90).
- **Eyes:** 5.2 radius, `#1E1B3C`, highlights 1.7 white; joy arcs, shut arcs, sad brows.
- **Sprout by stage:** 0 nub, 1 one leaf, 2 two leaves, 3 two leaves and a pink flower.
- **Mouths:** smile, open (joy), frown (sad), flat (guard).

### Forms, stages and outfits

| Form | Day | Sprout stage | Outfit |
|---|---|---|---|
| Sprig | 0 | 0 | none |
| Bud | 3 | 1 | red striped scarf |
| Bloom | 7 | 3 | yellow cardigan |
| Thicket | 14 | 3 | scarf and cardigan |
| Grove | 30 | 3 | leaf crown |
| Ancient | 60 | 3 | round glasses and a white beard |
| Celestial | 100 | 3 | starry cape and a gold halo |

Outfit shapes are in `SprigA.dc.html` (`sweater`, `scarf`, `crown`, `sage`, `star` groups).
Sprig always wears the outfit of its current form, on every screen. A wilted Sprig keeps its
outfit, desaturated. Locked forms (Collection, Recap next-form) are drawn as a silhouette:
the same drawing tinted solid `#1E1B3C` at 16–22% alpha, outfit included.

### Poses

| Pose | Where | Notes |
|---|---|---|
| idle | Home while habits are left, Collection | gentle sway, follows touch |
| cheer | All done, Recap kept, moments | arms up, bouncing, joy eyes, open mouth |
| guard | Lock screen | one arm raised like a stop sign, flat mouth |
| droop | Break, bad recaps | sprout bent 32°, frown, sad brows, optional tear |
| wave | Welcome, onboarding, Collection hero | right arm waving |
| sleep | Idle 12 seconds with no touch | shut eyes, slow breath, "z z" rising |

### Interactions (all screens where Sprig is larger than 100dp)

Constants are from `SprigA.dc.html`; keep them.

- **Follow:** eyes and a slight lean track the finger (pointer) within Sprig's bounds; ease
  factor `min(1, dt × 9)`.
- **Tap:** hop (14 units up, squash and stretch, arms up), decays over ~0.6s. Haptic tick.
- **Three taps within 450ms each:** tickle wobble (22 rad/s, 7° amplitude, fades over 1.4s).
- **Press and drag more than 20px:** petting: joy eyes, stronger blush, tear stops.
- **12 seconds untouched while idle:** falls asleep. Any touch wakes it.
- **Blink:** every 2 to 5.5 seconds, 130ms.
- **Tear (Break only):** falls from the left eye on a 1.8s loop while drooping and not petted.
- Performance: one frame clock per visible Sprig, paused when off screen or when the app is
  in the background. Small Sprigs (under 100dp, for example in lists) do not animate.
- Accessibility: Sprig is a button. `contentDescription` = "Sprig, {form}, {mood}". Double tap
  makes it hop.

## Screens

Copy is final. Values below that come from data are in `{braces}`.

### Welcome (`A1-Welcome`)
Dawn sky. "KEPT", headline "Habits first. Phone after.", line "Your apps stay locked until
today's habits are done." Sprig (Bud stage, scarf, waving, 350dp) rises over a cream hill.
Primary "Pick my habits" goes to onboarding step 1. Caption "This is Sprig. It grows every
day you keep your promise."

### Onboarding (`A10`–`A14`)
Six steps with a 6-segment progress bar and a back arrow. Continue sits in a fixed footer.
1. **Habits:** "What will you do every day?" Preset rows toggle on tap (purple ring and check
   when picked). "Add your own" opens Add a habit. Continue disabled with none picked;
   footer counts "2 habits picked". Respect `HabitLimits.MAX_HABITS`.
2. **Shutdown time** (#164, no mockup): "Pick a shutdown time", line "A shutdown time helps
   you stop doomscrolling at night." Three single-select rows, 10 PM, 11 PM and Midnight, each
   with "Apps lock until 7:00 am. Phone, messages and maps still work."; none preselected.
   Continue is disabled until one is picked; text button "Skip" leaves night lockdown off. The
   choice is written on "Start today".
3. **Lock window:** a 6am–midnight bar shows the locked band. Two rows with − / + in 30 minute
   steps: "Lock apps from" and "Give up at". Sentence under the bar updates live. Give-up is
   at least 2 hours after the start.
4. **What stays open:** "Always open" chips (Phone, Messages, Maps, Camera, Settings, Clock),
   search, and a list of installed apps with switches ("Stays open" / "Locks"). Count line.
5. **Permissions:** one card per permission with a tag (Required, Recommended, For photo
   habits, On) and a "Grant" button, or a green check when granted. If a required one is
   missing the button reads "Continue without the lock" and a red line explains the cost
   (`RequiredPermissions` copy).
6. **How Sprig works:** dawn sky with Sprig (stage 0) waving, three rules with duotone icons
   (growth, breaks, shield), primary "Start today".

### Home (`Main`, `A2-Home-6`, `A18-Parts`)
- **Roomy (1–3 habits):** full-height sky, headline "{Words} to go." ("One", "Two", "Three") or
  "Promise kept.", line "Apps unlock when you finish, or at {give-up}." / "Apps are open. See
  you tomorrow." Streak pill top right ("{n} day streak", never wraps). Sprig 240dp centred,
  rising as habits are done. Cream hill, then the habit rows (68dp), "Last 7 days" dots,
  "Shield ready".
- **Compact (4+ habits):** sky 260dp with headline left (180dp wide) and Sprig right (180dp).
  Only the habit list scrolls; the sky, "Today" title, the week row and the tab bar stay
  put. Rows are 56dp, one line, detail on the right.
- **Shield ready:** blue text button with a shield icon. Tap opens a dark card above it:
  "Your weekly free pass" / "Miss a day and the shield saves your streak. You get a new one
  every week." Tap again or anywhere else closes it. When the shield is used this week the
  button reads "Shield used this week" in muted grey and explains when the next one comes.
- **Week row:** 7 circles 30dp with weekday initials: kept gold fill, shielded blue tint with
  blue ring, today dashed purple, missed `#E6E2EE`, lock-off dashed grey hollow.
- **Banners** (`A18-Parts`), shown above "Today" in this order when they apply: Lock is off
  (red, "Fix" opens Permissions), first landing (green, "Got it"), too late for today (amber).
  No habits: a dashed "Add your first habit" row in place of the list.
- **Habit row states:** to do, done (purple tint, strikethrough, "Done"), done late (grey,
  "Too late"), photo habit ("Snap photo" with camera icon). Hold a done row to undo.

### Lock (`A3-Lock`)
Same structure as compact Home. Sky 280dp, lock ramp colours, ground `#1B1842`. Headline
"{App} can wait.", line "Finish your promised habits to unlock." Sprig in guard pose. Rows use
the dark style (border `#4A4590`, done `#3E3895` with a gold check). Footer: "Phone" (outlined,
one tap, always) and "Break the lock". When everything is done: gold sky, cream ground,
headline "{App} is open.", line "Promise kept. Enjoy it.", Sprig cheers, footer becomes one
"Open {App}" button.

### Break the lock (`A4-Break`, `A4-Break-last`)
Overcast sky 360dp. Headline "Break the lock?". Sprig (230dp, droop, tear) rises from behind
the hill on open (from +260 units, 1s, delay 0.3s), then the bubble "Don't let me wilt." pops
(0.4s, at 1.2s). Over the 60 second countdown Sprig's saturation goes from 1 to 0.25 and the
bubble text alpha from 1 to 0.35. "What it costs" lists three rows with duotone icons in soft
round tiles: "Sprig drops a level and wilts until tomorrow.", "Your {n} day streak stays.",
"{left} of 3 breaks left this week." Countdown 84sp tabular: "You can unlock in" → "You can
unlock now". Primary "Never mind, go back". Below it "Unlock anyway ({s}s)" disabled, then
"Unlock for {minutes} min" in red. **Last resort** (4th break): the cost list is replaced by a
red callout "This is your fourth break this week, the last resort." and "Today is written
off. It won't count toward your {n} day streak, and your shield can't save it."

### Recap (`A5-Recap*`)
Sky 400dp. Two stats side by side: "{done} of {total}" habits done, "{n}" locks broken. A
progress card: current form mini Sprig, bar with one dot per day between forms and a white
knob on today, next form silhouette, "{Next} in {n} days", "Day {d} of {target}". On kept
days the bar grows from yesterday to today (1.2s, delay 0.9s) and the knob pops; 14 petals
fall once. At the final form the card reads "Sprig is {Form}, the final form." with no bar.

| Outcome | Sky | Headline | Line | Pose | Streak |
|---|---|---|---|---|---|
| kept | gold | Promise kept. | {n} days running. | cheer | kept |
| shield | tide | Shield saved it. | Your weekly shield kept the {n}-day streak alive. | idle | kept |
| lock off | `#DCD6EA` | The lock was off. | That one is on us. Today does not count, but your {n}-day streak is safe. | idle | kept |
| slipped | overcast | The promise slipped. | Streak reset. Tomorrow is fresh. | droop | reset |
| too late | `#E9C3B0` | Too late to count. | Everything got ticked after the give-up time. Streak reset. | droop | reset |
| written off | overcast | Day written off. | A fourth break this week. Streak reset, and the shield could not save it. | droop | reset |

Buttons: "Share this look" (kept) or "See my Sprigs" (others), and "Done". The share card
uses the new Sprig and drops "off apps".

### Moments (`A16-Celebrate`, `A17-Evolve`)
- **Promise kept:** full-screen gold. Sprig cheers (260dp) with a pop and a fading ring, 18
  petals, "Promise kept.", "Day {n} of your streak. Apps are open.", "Tap to carry on".
- **Sprig evolves:** night sky, "{n} days kept", "Sprig is now {Form}", Sprig in the new outfit
  (280dp) with a gold ring, "New outfit: {description}. Next up is {next}, at day {day}."
  Buttons "Share" and "Carry on". Replaces the old reveal screen.

### Collection (`A6-Collection`)
Tide sky 300dp. "{Form}", "Day {n} of your streak", a pill "{Next} in {n} days". Sprig waving
in its outfit. "Forms" with "{k} of 7 reached", then a 3-column grid of all seven forms:
reached (white card, full colour), current (purple ring, "You are here"), locked (dashed,
silhouette, "Day {n}"). Tapping a reached form opens the share sheet for that form.

### Settings (`A7-Settings`, `A15-History`, `A12-Exceptions-settings`, `A13-Permissions-settings`)
Lilac sky 190dp with "Settings" and a small Sprig. Groups:
- "Your promise": Habits ({count}), Lock apps from, Give-up time (with explanation).
- "Lock": Exceptions ({n} apps), Unlock length after a break, Permissions ("All set" green or
  "{n} off" red).
- "Reminders and privacy": switches for Reminders ("Three nudges a day") and "Send anonymous
  usage data" (with the existing privacy line).
- History ("Best streak {n}") opens History: last 14 days with a dot, date, "{done} of
  {total}" and the outcome label (Kept, Shield used, Missed, Too late, Lock was off, Written off).
- Under the groups: "KEPT never shares your app usage, screen time, or location with anyone."
- Changing the lock window while apps are locked keeps today's "Change it anyway" confirm,
  restyled.

### Habits (`A8-Habits`, `A9-Habit-edit`)
- **Your habits:** back arrow, "Your habits", "Apps stay locked until every one is done. Two or
  three is plenty." Rows with icon tile, title, "{target} {unit}, tap when done" or "Photo
  check-in". Fixed footer "Add a habit" and "{n} of 10 habits". At 10 the button is disabled
  and the footer shows `HabitLimits.LIMIT_REACHED`.
- **Add or edit a habit:** "What will you do every day?" text field, quick picks (Exercise 30
  min, Read 10 pages, Practice 20 min, Study 45 min). "How much?" stepper (minutes in steps of
  5, others by 1). For a name that is not a quick pick, unit chips appear: min, pages,
  times, glasses (start values 20, 10, 1, 8). "1 times" reads "Once a day". "Prove it with a
  photo" switch hides "How much?". "Save habit" disabled until there is a name. The icon is
  picked from the name (keyword map; fall back to a pencil). Edit mode: title "Edit habit"
  and a red quiet "Remove habit"; removing the last habit is blocked with the existing copy.

## Fit and finish checklist (every story)

- Edge to edge: skies run under the status bar; content gets `statusBarsPadding()`; the tab
  bar and footers get `navigationBarsPadding()`.
- Status bar icons: light on night, lock and evolve skies; dark on all others. Switch when the
  sky crosses the halfway point.
- No sky colour visible between sky and hill at any density (hill overlaps by 4dp).
- Text never wraps into Sprig or under the tab bar: check at font scale 1.0, 1.3 and 2.0, and
  at 360 × 640dp and 412 × 915dp. Headlines may wrap; pills and stats may not.
- All text meets 4.5:1 contrast on its background (sky text included).
- Every tappable thing is at least 48 × 48dp and has a TalkBack label.
- Reduce motion respected (see Motion).
- No leftover dark-theme or variant code paths.

## Test cases

Unit tests (JVM, `app/src/test`):
- Sky index for done/total on Home (0/2, 1/2, 2/2, 0/6 … 6/6) and Lock.
- Headline words: "One to go." … "Ten to go.", "Promise kept.".
- Layout switch: roomy at 1–3 habits, compact at 4–10.
- Outfit for each form, silhouette for locked forms.
- Recap outcome mapping for every row of the outcome table, including streak kept vs reset.
- Progress card: day in span, days to next form, final form.
- Break: saturation and bubble alpha at 60, 30, 0 seconds; last-resort switch at the 4th break.
- Add habit: unit start values, step sizes, "Once a day", save disabled with blank name.
- Lock window: 30 minute steps, give-up at least 2 hours after start.

Screenshot tests (no device needed; Roborazzi on Robolectric is the suggested tool) for every
artboard state in the table at the top, at 412 × 915dp, font scale 1.0 and 2.0.

Manual QA on the emulator (`scripts/verify.sh` plus a walk): every acceptance line of each
story, TalkBack on Home and Lock, and reduce motion on Break and Recap.

## Stories (one PR each, in this order)

Feature card: #121. Design source in the repo: #122.

1. #123 Daybreak foundation: font, tokens, light-only theme, shared parts, hill and sky scaffold.
2. #124 New Sprig: renderer, poses, outfits, interactions, share card.
3. #125 Remove weekly looks.
4. #126 Home: roomy and compact layouts, shield, banners, week row.
5. #127 Lock screen: tick habits on the lock screen, open state.
6. #128 Break the lock: rise and plea, costs, last resort.
7. #129 Recap: six outcomes, progress card, petals; hide time off apps.
8. #130 Moments: promise kept and Sprig evolves.
9. #131 Collection: forms grid; remove roadmap and looks.
10. #132 Onboarding: welcome and five steps.
11. #133 Settings: groups, history, exceptions, permissions.
12. #134 Habits: your habits, add and edit.
