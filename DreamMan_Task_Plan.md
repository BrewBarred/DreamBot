# DreamMan — Repair & Upgrade Plan (for review)

**Project:** DreamBot script framework "DreamMan" (Java Swing, runs inside the DreamBot client)
**Prepared:** 8 July 2026
**Status:** Decisions locked — API: Node.js in Docker · UI: pure Swing. Awaiting final go-ahead.

Each patch below is a **whole deliverable**: a drag-and-drop zip that mirrors your project
structure and contains **only new/edited files**, plus a `PATCH_NOTES.md` explaining every
change and how to test it in the client. Patches are ordered so each one leaves the app in a
working state. You can approve all of them, reorder, or drop any.

---

## Patch 0 — Project housekeeping (recommended first)

**Goal:** a clean skeleton so every later patch is unambiguous and small.

- Move dead/legacy code out of `main/` into an `/archive` folder (nothing deleted):
  `menu/OSRSMenu.java` (880 lines, referenced nowhere), `task/Task.java` + `task/Action.java`
  (fully commented out), `actions/Dig.java`, `actions/Fetch.java`, `actions/Fetchb.java`
  (all commented out), `data/Libraryb.java`, `managers/DataManBackup.java`,
  `menu/JLibraryList2.java` (only used by the dev console), `data/ActionType.java`,
  root `tutorial.java`, and the `examples/` folder ("FUCNTIONINGNGING", "SoCloseooseoe" etc.).
- Remove `main/data/library/WebDriver-387...pkg` (~61 MB binary sitting in your data folder
  and bloating git — there's a 62 MB blob in `.git/objects`).
- Consolidate the five duplicate library data folders
  (`library/`, `main/data/library/Library/`, `default/`, `lumbridge*`, `objects/`) into one
  `default` collection + document the collection feature.
- Add a proper `.gitignore` (`out/`, `.venv/`, `.idea/workspace.xml`, `*.tmp`).
- Fix the IntelliJ module file: it's currently `type="PYTHON_MODULE"` — corrected `.iml`
  provided so IntelliJ treats this as a Java project with `main` as a source root.

**Acceptance:** project compiles exactly as before; `main/` contains only live code.

---

## Patch 1 — Critical engine & logic fixes

**Goal:** the script loop, settings, and menu behave the way the code *claims* they do.
These are all confirmed bugs (full register in Appendix A).

- **Saving is currently a no-op** — `DreamBotMenu.saveAll()` has the actual
  `dataMan.saveEverything(...)` call commented out, so every "Save" button shows a success
  toast and saves nothing. Patch 1 wires saveAll to a real sink (local files come in Patch 2,
  so in this patch it at least stops lying to the user).
- **Data loading can deadlock forever** — `updateAll()` sets `isDataLoading = true`, and if
  you're not logged in yet it schedules a retry **without resetting the flag**, so every retry
  returns instantly and your data never loads if the menu opens before login.
- **Two settings are inverted** — "Start Script on Load" and "Exit on Stop Warning" do
  `field = !checkbox.isSelected()`, so ticking them turns the feature *off*. The snapshot
  field names (`startScriptOnLoadDisabled`) are also inverted vs the UI; I'll normalise to
  positive names with backward-compatible JSON mapping.
- **Two whole settings pages are unreachable** — "Gameplay" and "Interfaces" cards are built
  but missing from the side-menu button list, so *Data Orbs* and *Ammo-picking* can never be
  toggled. Bonus: `chkDataOrbs` is assigned twice (Gameplay + Interfaces), so save/load reads
  the wrong checkbox even if you could reach it.
- **Preset page-down double-fires** — `btnPageDown` has **two** ActionListeners attached, so
  one click advances two pages / double-toasts.
- **`postLoginNew()` fires on every login** — it's called before the "is this a new name?"
  check in `DreamBotMan.onGameStateChange`, defeating its documented purpose (world-hops
  trigger "new login" logic).
- **Status bar never resets** — `setStatus()` sets `"Status: " + text` but the reset timer
  compares against bare `text`, so the "Day-dreaming..." auto-revert never runs.
- **Task Builder restore crashes** — `unpackTaskBuilder` calls
  `actionSelector.create(snap.target)` with a *target string* (e.g. `"3217, 3238, 0"`)
  instead of an action type, returning null → NPE inside `setSelectedAction`. Also
  `setSelectedItem(snap.selected)` pushes a raw `Map` into a `JComboBox<Action>`.
- **`refreshTaskListTab(int)` logic is inverted** (`index = index == -1 ? selected : -1`) and
  it calls `ensureIndexIsVisible` on the *builder* list instead of the task list.
- **Shared-reference corruption** — "Add" from the Task Library puts the *same* Task object
  into the queue; editing or executing then mutates both copies. Queue additions become deep
  copies (the deep-copy constructor already exists).
- **Thread-safety pass** — `pause()/resume()` mutate Swing buttons from the script thread;
  `onLoop` reads the `DefaultListModel` queue while the EDT mutates it. Fixes: EDT-wrap UI
  mutations, snapshot the task for execution, tighten the `queue == null` startup race, and
  collapse the two competing "end of list" paths (index `-1` vs `>= size`).
- Small NPE fixes: `addTemplateToBuilder` dereferences before its null-check; `create()`
  result unchecked in `unpackTaskBuilder`'s action loop.
- Remove the destructive **double-click-to-delete** on the Task List (replaced by the Remove
  button + Delete key with confirm) — double-click will *edit in builder* instead, matching
  what users expect.

**Acceptance:** checklist in PATCH_NOTES reproducing each bug before/after in the client.

---

## Patch 2 — Local save/load (no server required)

**Goal:** everything you build survives restarts *today*, with Supabase gone. The server
becomes an optional sync layer later (Patch 7), not a requirement.

- New `LocalStore` that persists **settings, task list, task library, presets, and the
  builder draft** as JSON under a stable per-user folder
  (`<user.home>/DreamMan/<character>/…`), written atomically (same tmp-rename strategy your
  `FileManager` already uses).
- Tasks serialize via your existing **`ActionData` (type + params)** pattern — this also
  fixes the latent crash where Gson tries to deserialize the abstract `Action` class directly
  (the current `unpackTaskLibrary`/`unpackTaskList` approach cannot work without it).
- Auto-save timer + all Save buttons wired to LocalStore; load on start and on character
  switch (per-character profiles, since everything is keyed by player name).
- **Import/Export a task or preset as a `.json` file** — instant sharing with friends, and
  it's the exact format the future marketplace will serve.
- Fix `FileManager`'s relative path `main/data/library/` — it breaks the moment the script
  runs as a `.jar` inside DreamBot (files land in whatever the client's working directory is,
  or nowhere). Library collections move to the same DreamMan home folder; bundled defaults
  ship as classpath resources copied on first run.

**Acceptance:** build a task → restart client → task, library, presets, settings all restore.

---

## Patch 3 — Loops & queue flow (the feature you described)

**Goal:** "queue, loop, repeat ×N" actually exists — right now there is **no loop feature at
all** (only a TODO and a commented `getLoopsLeft()` in `DreamBotMan.onLoop`).

- **Per-task repeat count** (`×N` on each queue entry, editable inline).
- **Whole-queue loop count** with an ∞ option; live "Loop 2/5" indicator.
- Progress bar reflects (task in queue) × (loop) progress; executing row highlight kept.
- Clean queue lifecycle: run → complete → auto-loop or pause; Skip / Run-from-here context
  actions; loop counts saved with presets and tasks (extends the Patch 2 JSON format).

**Acceptance:** queue two tasks, set queue loops = 3, watch it run 3 passes and pause.

---

## Patch 4 — Action pack (real modularity)

**Goal:** the modular action system currently contains exactly **one action (Walk)**.
This patch delivers a usable starter set, all following your existing pattern
(`copy / serialize / deserialize / createParamPanel`), registered in `JActionSelector`:

- **Interact** — nearest NPC/Object by name with a chosen action verb (Attack, Chop down,
  Mine, Fish, Talk-to, Open…), with **radius filter** and optional tile anchor, fed by the
  JLibrary + nearby scan you already built.
- **Loot** — pick up ground item by name within radius.
- **Drop** — drop all matching inventory items.
- **Bank** — open nearest bank / deposit-all / deposit-except / withdraw N.
- **Wait** — random delay between min–max ms (humanising glue between actions).
- **Walk** — kept, plus target picker from library/nearby list instead of typing names.
- JActionSelector fixes: selection restore actually works (current `setSelectedItem(copy)`
  is ignored because the copy isn't in the model), proper renderer, safe `create()`.

**Acceptance:** build "Walk → Interact(Chop) → Loot → Drop → Wait" purely from the UI and
run it end-to-end.

---

## Patch 5 — UI overhaul

**Goal:** one sleek, consistent, discoverable interface (currently two clashing themes:
the blood-red/black MenuHandler style vs the GitHub-dark LibraryPanel palette).

- Single design system: shared colour/typography tokens used by *every* tab including
  LibraryPanel; consistent buttons, focus/hover states, spacing, and titled sections.
- **Drag-and-drop reordering** in the Task List and Action Builder (arrows kept as fallback).
- **Right-click context menus** everywhere: presets (Load / Save current queue / Rename /
  Delete) replacing the undiscoverable Shift-click / Ctrl+Shift-click / Delete-key combos;
  tasks (Edit / Duplicate / Repeat ×N / Remove); library (Add to queue / Edit / Export).
- Toast system rework: readable duration, queue > 1, success/fail styling (currently 300 ms
  and capped at one — they flicker and drop).
- Task detail panel on single-click (name, description, action chain preview) — the TODO you
  left in the click handler.
- Empty-state hints ("Library is empty — build a task or import one"), tooltips on every
  control, consistent confirm dialogs, window title/version cleanup, always-on-top becomes a
  setting (currently hardcoded).
- Pure Swing, zero new dependencies (locked in).

**Acceptance:** side-by-side screenshots per tab in PATCH_NOTES; no regressions in function.

---

## Patch 6 — MySQL database + API for ghost-server.nz

**Goal:** a production-ready schema you upload to your homeserver, plus the thin API the app
will talk to.

**Important architecture note:** the desktop app should **not** connect straight to MySQL
port 3306. Cloudflare's proxy (orange cloud) only forwards HTTP/HTTPS, so a raw JDBC
connection won't pass through it — and exposing MySQL to the internet is how homeservers get
owned. The right shape (and the one your code is already built for, since DataMan speaks
REST) is:

```
DreamMan (Java) ──HTTPS──▶ Cloudflare ──▶ ghost-server.nz API ──localhost──▶ MySQL
```

Deliverables:

- **`schema.sql`** (MySQL 8, utf8mb4, InnoDB) — ready to `mysql < schema.sql`:
  - `users` (marketplace identity, token auth) and `characters` (your RS characters —
    everything is currently keyed by character name only, which collapses the moment two
    people or two accounts use it),
  - `character_state` (settings JSON, last location/inventory/worn/skills — mirrors your
    `saveEverything` payload),
  - `tasks`, `presets` (ActionData JSON from Patch 2 — same format on disk and on server),
  - marketplace-ready tables for later: `scripts`, `script_versions` (JSON definition
    **and/or** jar blob + SHA-256 checksum), `ratings`, `downloads`, `reports`. My strong
    recommendation for the market: share the **JSON task definitions** rather than compiled
    jars — safer for users, previewable/rateable, and it slots straight into your planned
    one-click compile (compile locally from the JSON). The schema supports both.
- **Minimal REST API** with per-user token auth, endpoints mirroring what DataMan already
  calls (`GET /player/{name}/{column}`, `POST /player/save`, plus `/tasks` CRUD), so the
  Java-side change in Patch 7 is tiny. Stack: **Node.js (Express) in Docker** —
  `docker-compose.yml` (MySQL 8 + API containers), `.env` template, setup README for the
  homeserver & Cloudflare (including a Cloudflare Tunnel option so you don't have to
  port-forward at all), and a seed/test script.
- Note: your old Supabase anon key is hardcoded in `DataMan.java` (and burned). The new
  design keeps secrets out of the client — the app stores only *your* user token, entered
  once in Settings.

**Acceptance:** `curl` examples in the README return/store a test payload through
ghost-server.nz.

---

## Patch 7 — Swap the app to your server (last step, as requested)

- `DataMan` v2: configurable base URL + token (Settings tab), same async pattern, all the
  commented-out save/load paths reimplemented against the new API.
- **Offline-first:** local files (Patch 2) remain the source of truth; server sync is
  background with last-write-wins on timestamp, and failures never block the UI.
- Kill switch back to local-only if the server is down.

**Acceptance:** save on one PC, load on another through ghost-server.nz.

---

## How I'll verify patches

I can't run the DreamBot client here, so for every patch I compile your project against
stub signatures of the `org.dreambot.api` classes you use — that catches type/syntax/logic
errors before delivery — and PATCH_NOTES includes an in-client test checklist for the parts
only you can run (login, walking, interactions).

---

## Appendix A — Full bug register

| # | File | Severity | Bug |
|---|------|----------|-----|
| 1 | DreamBotMenu.saveAll() | Critical | Real save call commented out — all Save buttons are no-ops that toast "success". |
| 2 | DreamBotMenu.updateAll() | Critical | `isDataLoading` never reset on the not-logged-in path → retry timer no-ops forever; data never loads. |
| 3 | DreamBotMenu.unpackTaskLibrary / (commented) unpackTaskList | Critical | Gson asked to deserialize abstract `Action` inside `Task` — impossible without the ActionData pattern; library load crashes/corrupts. |
| 4 | DreamBotMenu.createScriptPanel() | High | "Start Script on Load" and "Exit on Stop Warning" logic inverted (`= !isSelected()`). |
| 5 | DreamBotMenu.createSettingsTab() | High | "Gameplay" & "Interfaces" cards missing from menu buttons → Data Orbs / Ammo settings unreachable; menu grid sized 10 for 8 buttons. |
| 6 | DreamBotMenu (fields) | High | `chkDataOrbs` assigned in two panels → snapshot/save/sync read the wrong checkbox. |
| 7 | DreamBotMenu.createPresetControlPanel() | High | `btnPageDown` gets two ActionListeners → double page-advance per click. |
| 8 | DreamBotMan.onGameStateChange() | High | `postLoginNew()` invoked on every login, outside the name-change check. |
| 9 | DreamBotMenu.unpackTaskBuilder() | High | `create(snap.target)` passes a target string as a type → null → NPE; `setSelectedItem(Map)` pushed into `JComboBox<Action>`. |
| 10 | FileManager.DIR | High | Relative path `main/data/library/` breaks under the client / inside a jar; library data lands in the client CWD or nowhere. |
| 11 | Task Library "Add" / double-click | High | Same `Task` reference shared between library and queue → edits/execution mutate both. |
| 12 | DreamBotMan.onLoop / pause / resume | High | Swing mutations off the EDT (`btnPlayPause.setText` etc.); queue model read on script thread while EDT mutates; startup race on `queue`; two competing end-of-list paths. |
| 13 | DreamBotMenu.setStatus() | Medium | Reset timer compares `"Status: "+text` to `text` → never reverts to default status. |
| 14 | DreamBotMenu.refreshTaskListTab(int) | Medium | Inverted index ternary; `ensureIndexIsVisible` called on the builder list, not the task list. |
| 15 | TaskBuilder.addTemplateToBuilder() | Medium | Dereferences `getSelectedAction()` before the null check. |
| 16 | JActionSelector.setSelectedAction() | Medium | `setSelectedItem(copy)` ignored — copy isn't in the combo model → selection restore silently fails. |
| 17 | JActionSelector registry | Medium | Only `Walk` registered — modular action system has one action. |
| 18 | DreamBotMenu task list double-click | Medium | Double-click silently deletes a task (destructive, unconfirmed, unconventional). |
| 19 | Toast system | Low | 300 ms lifetime, queue cap 1 → unreadable, drops messages. |
| 20 | DreamBotMenu / DreamBotMan | Low | `setVisible(true)` called twice; always-on-top hardcoded; title "v1" vs javadoc "15.0.0-Elite". |
| 21 | createTaskLibraryTab() | Low | Button row `GridLayout(1,3)` holds 4 buttons. |
| 22 | Dead fields/methods | Low | `chkVisualEffects`, `nearbyList`, `consoleArea`, `getPresetsForDatabase()`, `isTaskDescriptionRequired`… unused. |
| 23 | DataMan.java | Low | Supabase anon key hardcoded in source (now moot; informs new design). |
| 24 | Repo hygiene | Low | 61 MB WebDriver pkg in data dir (+62 MB git blob), `examples/` inside repo, `.iml` marked PYTHON_MODULE, no .gitignore, 5 duplicate library data folders. |

Missing features (not bugs): task/queue **looping** (Patch 3), **local persistence**
(Patch 2), action variety (Patch 4).

---

## Appendix B — Decisions

1. **API stack** (Patch 6): ✔ LOCKED — Node.js (Express) in Docker.
2. **UI dependencies** (Patch 5): ✔ LOCKED — pure Swing, zero new dependencies.
3. **Patch order:** awaiting your go-ahead. Recommended first drop: Patch 0 + Patch 1
   together (housekeeping + critical fixes). Note: a drag-and-drop zip can add/overwrite
   files but can't delete them, so Patch 0's moves/removals ship as a short checklist in
   PATCH_NOTES plus an optional `cleanup.bat` you can run once from the project root.
