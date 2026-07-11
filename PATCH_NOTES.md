# DreamMan — Patch B.5 (v1.0.16): Concurrent Checks, library search, skill overlay v2 + fixes

**Drop type:** drag-and-drop over your project root (IntelliJ **closed**), reopen, Rebuild
Project → Build Artifacts → DreamMan → Rebuild. Source only — no `.iml`/`.idea` files.

---

## Watchers → **Checks** (renamed, redesigned, and now concurrent)

**Renamed.** Everything user-facing now says **Checks**: the tab, the builder's **Checks…**
button, the dialog titles. (Internally the classes are still `Trigger`/`Watcher…` — only the
labels changed, so your saved data is untouched.)

**Redesigned tab (was a 5/10 — agreed).** The old layout stacked one chunky card per watcher
inside a split pane you had to drag before anything was legible. The Checks tab is now full-width:
a **compact list on the left** (one line per check: ● on / ○ off + a plain-English summary) and a
**clear detail editor on the right** for the selected check. No dragging to see it, no bulky
cards.

**Argument cleanup.** The value field now only appears when the condition actually takes one, with
an italic hint of what it means (e.g. "absolute (15) or percent of max (40%)"). *Inventory is
full* is a boolean — it no longer shows a nonsensical value box. And since "how full" is often
what you really want, there's a new **Empty slots below N** condition.

**Checks can respond with TASKS, not just actions.** The response picker now lists your whole
library as gold task entries alongside the built-in actions, so a check can fire an entire saved
task.

**Concurrent checks (the important one).** Checks are no longer one-at-a-time. If you're dying and
set two checks — *run away* and *eat* — they now **make progress together** instead of running
away first and eating over your gravestone. The engine evaluates every check each cycle (so a
check can fire mid-action, not only when idle), keeps all fired responses active at once, and
**interleaves their steps fairly** so no single check locks out the others. Response actions run
with an "emergency" flag so things like eating work **while you're moving**.

> **Honest note on threading:** true OS threads can't safely click the game — all DreamBot input
> has to come from the one script thread, so genuine parallel input isn't possible without risking
> corrupt clicks. What this does instead is interleave the checks inside the loop: several make
> progress within the same tick window, which produces the run-away-*and*-eat outcome you wanted
> without thread-locking or unsafe input. In practice it behaves like the background threading you
> described.

## Loot Tracker is its own tab

Split out from Checks (they were two different things sharing a pane). **Loot Tracker** is now its
own top-level tab with the kill counts and learned drop tables; Checks is purely about checks.

## Task Library: search + origin filter (with a real save-safety guarantee)

A **🔍 search box** (matches name or description) and an **origin filter** — *All / Built by me /
Imported / Default* — sit above the library. Tasks now remember where they came from: ones you
build are "user", imported ones are marked "imported", and this persists.

Under the hood the library was refactored around a **master list**: search and the filter only
change the *view*, never the underlying data. This is a guarantee, not a hope — I verified that
**saving, exporting, use-counts, TaskRef resolution and builder propagation all read the master
list**, so filtering the library can never drop a hidden task from a save or miss it on an edit.

## Button fixes (from your screenshots)

- **Pause button sizing** — it was drawn with a two-glyph "▮▮" that overflowed the compact icon
  button. Now a single ⏸ / ▶ glyph, correctly sized.
- **Library up/down arrows changing colour and sticking** — the flash was setting an opaque
  background the flat-button UI ignored, so the raw colour bled through and stayed. It now flashes
  the property the UI actually paints from, and clears it cleanly. (Sorting owns library order, so
  those arrows are cosmetic nudges.)

## Pause-drag reorder (seamless)

Pausing now genuinely frees the queue for editing: you can **drag to reorder while paused** (only a
live, running queue blocks it). If you move the tile that was executing, execution **follows it to
its new position and resumes from the same action**, as though it never moved — the action cursor
isn't reset.

## Skill overlays v2 (icons, bars, ETAs, expand/collapse)

Each tracked skill is now one mini canvas:

- **Expanded card:** the same skill **icon from the menu**, the login→current level (e.g. `1→40`),
  a **progress bar to the next level** with **time-to-level** and **remaining XP** — and, when you
  set a goal (right-click the tracker tile), a **second bar to the goal** with time-to-goal,
  remaining-to-goal and a %.
- **Minimized:** the simple **3-component overlay** you asked for — **icon + progress bar +
  floating "+xp gained"** text. Click a card's title to collapse it; click a chip to expand it.
  (Above 10 tracked skills they default to chips so they never blanket the screen.)
- Goals accept a **target level or an exact XP**, and the bars/ETAs update live from your current
  XP/hr.

## Verified here

27/27 feature probes under the client-faithful isolated classloader, including: booleans take no
argument + the new empty-slots condition; **concurrent checks — both responses step in the same
cycle, the emergency flag is set, and REPLACED still consumes the action's pass**; paused-reorder
index remap keeps the cursor; **master/view split — search hides but capture still saves all
tasks, and propagation reaches hidden tasks**; origin filter + round-trip; the picker offers
library tasks; tabs renamed (Checks present, Loot Tracker split out, Watchers gone); builder button
says Checks…; skill overlay v2 numerics (level fraction, time-to-level, goal remaining,
time-to-goal) with the expanded card and 3-component chip rendered. Patch B regression suite 28/28.
UI-health probe: 0 `no ComponentUI` errors, progress bar alive, every button skinned. Full tree
(incl. `examples/`) compiles, 0 errors. The redesigned Checks tab and both overlay states were
rendered and confirmed.

## Check in your client

1. Checks tab: add two checks (e.g. HP below 15 → eat, Run energy below 20 → a rest task). Note
   the value box disappears for *Inventory is full*.
2. Take damage near a safe spot with "run away" + "eat" checks set — you eat while moving, not
   after.
3. Task Library: search by name; switch the filter between Built by me / Imported. Import a file
   and confirm it shows under Imported. Filter to one task, save, reload — nothing is lost.
4. Pause mid-run, drag the executing tile elsewhere, resume — it continues from where it was.
5. Track a few skills, set a goal on one (right-click its tile), watch the level + goal bars and
   ETAs; click a title to shrink it to the icon+bar+xp chip, click the chip to expand.
