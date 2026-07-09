# DreamMan — Patch A (UI overhaul + in-game overlay + on-canvas buttons + login/logout)

**Drop type:** drag-and-drop over your project root. Apply after Patches 0-4.
**Includes the Wait/Rand fix.** Built against the **real DreamBot API** (verified on their javadocs).
**Compiles:** full tree, 0 errors (also with the JetBrains annotations lib absent).

## UI overhaul (part 1)
- New `Theme.java` — palette/fonts/spacing + `Theme.install()` pushing a flat-dark look into every
  component app-wide (tabs, buttons, scrollbars, inputs, tooltips, lists). Elevated red/black.
- Custom flat buttons (rounded, hover/press; semantic red/green fills; crimson primary Play) and
  thin flat scrollbars.
- **Task queue = cards** with icon chip, name, action-chain preview, running highlight + `×N` badge.
- **Task List polish:** proper header row (title + task-count pill) replacing the old centered
  heading; **hover highlight**; **right-click menu** on cards (Edit in builder / Duplicate /
  Set repeat ×N… / Run from here / Remove); cleaned window title.
- **Resize width-growth bug fixed** (min frame size, tracker width capped, double-parented button
  fixed).

## In-game overlay (`Overlay.java`)
Status panel drawn on the game canvas via `onPaint(Graphics)` (confirmed safe), like your example:
script name + uptime, state/task, loop progress, custom stat rows, progress bar. **Per-script
adjustable:** `Overlay.setVisible/setPosition/setTitle`, and push rows with
`menu.putOverlayStat("Logs","125")`.

## On-canvas buttons (`CanvasButtons.java`) — universal, every script
Clickable buttons drawn on the game screen (like "Open settings / Skip / +30m / -30m"), routed via
DreamBot's `HumanMouseListener.onMouseClicked`. This build wires: **Open settings**, **Pause /
Resume**, **Skip task**, **+30 min**, **-30 min**. The +/-30m buttons set a run-time limit; when it
elapses the script logs out and stops, and the overlay shows "Time left".

## Login / Logout (full account manager path)
- **Login** = `LoginUtility.login()` (logs into the configured account).
- **Logout** = `getTabs().logout()`.
- **Login** and **Logout** buttons added to the control bar; **Stop-with-logout** is used by the
  run-time limit. All isolated in the script via a small `ScriptControls` bridge.

## Humanised delays (foundation)
`Rand` gains delay tiers: `quickDelay()` (jitter), `pauseDelay()` (realistic between-actions),
`afkDelay(multiplier)` (semi-AFK you can scale). These back the Wait action and the upcoming
auto-delay checkbox.

## Files
New: `Theme.java`, `Overlay.java`, `CanvasButtons.java`, `ScriptControls.java`.
Edited: `DreamBotMenu.java`, `MenuHandler.java`, `DreamBotMan.java`, `Rand.java`.

## Test in client
- Task List: hover a card (border brightens), right-click (context menu), check the header count.
- In game: the overlay panel and the button row appear; click **Open settings** (menu focuses),
  **Skip**, **+30 min** (overlay shows "Time left" counting down), **Pause/Resume**.
- Control bar: **Login** / **Logout**.

## Note on the two API-sensitive bits
`HumanMouseListener` and `Tabs.logout()`/`LoginUtility.login()` were verified against DreamBot's
javadocs, and all DreamBot calls are wrapped in try/catch so a mismatch degrades gracefully rather
than crashing. If a click-listener isn't dispatched on your client build, the on-canvas buttons
simply won't respond (no error) — tell me and I'll switch to the canvas MouseListener approach.

## Next (foundation already in): auto-delay checkbox in the Task Builder with +/-50/100ms steppers.
