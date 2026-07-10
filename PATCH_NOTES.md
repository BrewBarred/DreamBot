# DreamMan — Patch B.4 (v1.0.15): Watchers, conditional responses & loot-table learning

**Drop type:** drag-and-drop over your project root (IntelliJ **closed**), reopen, Rebuild
Project → Build Artifacts → DreamMan → Rebuild. Source only — no `.iml`/`.idea` files.

---

## The idea you had — built

Instead of bolting a check onto every action by hand, DreamMan now has a **watcher system**:
conditions that are evaluated automatically **between actions, only while the player is safe**
(idle), each firing a small **response chain** — your "emergency list" — when they trip. Watchers
live in two places, exactly as you described:

**Global watchers** (new **Watchers tab**) are the always-on background checks: `HP < 15 → eat`,
`Prayer < 10 → drink`, `Run < 20% → rest`. Add a watcher, pick a condition + its argument, then
build the response chain from the normal action selector. They're checked before every queue
action and never interrupt a click already in flight.

**Per-action watchers** (new **Watchers…** button in the Task Builder) attach to one action in a
task. This is your Loot example: give the Loot action a watcher `if inventory full → bank` and
tick **"instead of this action"** — when the bag is full the response runs *instead* of looting
for that pass, then the task carries on. Leave "instead" off and the response runs as a safety
chain alongside. Either way, only that action carries it; nothing else is touched.

## Conditions (the deferred ones, now reusable)

`Inventory full`, `Inventory contains / missing item`, `HP below` (absolute `15` or `40%` of
max), `Prayer below`, `Run energy below %`, `Inside area` (`x1,y1,x2,y2[,z]`), `Outside area`,
`On coordinate` (`x,y[,z]`). Every condition is read defensively — if the world can't be read it
reports false rather than throwing into the loop — and each carries a per-fire cooldown so a
still-true condition doesn't spam its chain.

## New action: Manage inventory (InventoryManager)

The "scan my inventory and act" action you wanted. It reads a small rule script and applies one
matching operation per idle poll:

```
drop:Bones,Ashes ; bury:Big bones ; eat:Shrimps,Trout ; drink:Prayer potion(4) ; cook:Raw shrimps
```

Verbs: **drop, bury, eat, drink, cook**. Names are **exact** (case-insensitive) so "Bones" never
touches "Dragon bones". It's the natural response for an "inventory full" watcher — attach it to
Loot to make room by rule instead of always banking. Completes when no rule has anything left to
act on, draining the inventory in human-paced passes.

## Loot-table learning (basic readout)

The `LootTracker` from earlier now **counts kills per NPC and attributes drops** seen on your
kill tiles to the NPC that died there — each pile counted once. The **Watchers tab** shows a live
readout: per NPC, kills recorded and the items seen, as totals and a rough **per-kill rate**
(e.g. `Cowhide  14  (0.93/kill)`). Refresh/Reset buttons included; it auto-updates while visible.
This rides on the same ownership logic that already makes Loot prefer your own drops and skip
ironman-blocked piles, so it costs nothing extra at runtime. (This is the foundation; a fuller
drop-rate analysis with confidence over larger samples is the natural next step.)

## How it fits together (a worked example)

*Cannon-free cowhide farming on an ironman:*
1. Task: `Interact Attack Cow (until gone)` → `Loot Cowhide`.
2. Attach to the **Loot** action a per-action watcher: `if inventory full → Manage inventory`
   with rules `drop:Bones` (or `→ Bank`, "instead of this action").
3. Add a **global** watcher: `HP < 12 → InventoryManager eat:Cooked chicken` (or an Eat step).
4. Run. The bot fights and loots its own drops; when the bag fills it clears bones instead of
   looting; if HP dips it eats — all between actions, without you wiring checks into each step.
   The Watchers tab fills in Cow's learned drop table as you go.

## Under the hood (for your future work)

Watchers serialize as JSON through the existing action codec — a trigger's whole response chain
round-trips exactly like any action list — so per-action watchers ride inside each action's saved
params (reserved `__triggers` key) and global watchers save with the profile. No schema change to
anything that already persisted. The engine consults a small `WatcherEngine` each loop that
returns IDLE / RUNNING / REPLACED, keeping the queue paused while a response chain steps one
action per loop (same pacing and pause-safety as the main queue).

## Verified here

20/20 feature probes under the client-faithful isolated classloader: condition threshold math
(absolute + percent), full trigger round-trip (condition, flags, cooldown, nested response
actions with their own params), attached-watcher folding through the action codec, WatcherEngine
outcomes, InventoryManager rule parsing + completion, LootTracker per-NPC kill counts + single-
count drop attribution + ownership ranking, and global-watcher profile persistence + the Watchers
tab. Patch B regression suite 28/28. UI-health probe: 0 `no ComponentUI` errors, progress bar
alive, every button skinned. Full tree (incl. `examples/`) compiles, 0 errors. The Watchers tab
was rendered and visually confirmed.

## Check in your client

1. Watchers tab: add `HP below 15`, build a response (e.g. an Eat/InventoryManager step). It
   persists across a restart.
2. Task Builder: select an action → **Watchers…** → add `Inventory full → Bank`, tick "instead
   of this action". Run a task with that action and a full bag — it banks instead.
3. Add a Manage inventory action with `drop:Bones` and confirm it clears bones in passes.
4. Kill NPCs with Interact(Attack) + Loot nearby, then watch the Watchers tab's loot table fill
   in per-NPC kills and drops.
