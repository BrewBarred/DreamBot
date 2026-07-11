# DreamMan — Consolidated Base v-base-2 (bug fixes + smart Interact)

Replace your entire `main/` with this (IntelliJ closed, back up first; your resources/.idea/.iml
stay). Verified compiling (0 errors) and rendering here.

## Fixed this round
- **Whole app is now consistently gold** — the leftover red headings ("Task Builder", "Action
  List:") and orange add buttons are retinted to the OSRS gold theme. The **"Add to builder…" and
  "Add to library…" buttons are now prominent gold** (they were always present — verified by a
  real click test — just hard to see in the half-themed layout).
- **Button text contrast** — dark text on gold fills, white on red, so labels are readable.
- **Play/Pause icon now stays in sync** with the real paused state (pausing from the in-game
  overlay flips the Swing button too).
- **In-game "Menu" button now works** — it force-raises the window (setVisible + de-iconify +
  always-on-top toggle + toFront). The old version silently failed because the click ran off the
  UI thread.
- **Overlay is ~1/3 wider** (fits longer State/Task lines) and the **button row is now attached
  directly under the overlay panel** and spans its width — one linked component, fewer moving
  parts across layouts.

## New: Interact has real stopping conditions
Interact now decides for itself when it's finished (the queue only advances when it returns done),
via a **"Wait until"** field with a smart default:
- **auto** – combat verbs (attack/fight/kill) wait until the target is dead/out of range;
  everything else interacts once.
- **once** – interact and move on.
- **gone** – wait until the target is dead / despawned / out of range (your "Attack Cow" case).
- **inv-full** – keep chopping/mining until the bag is full.
It only re-clicks while the player is idle, so it won't spam mid-action. All six actions now have
sensible stop conditions (Walk=arrived, Loot=none-left, Drop=empty, Bank=done, Wait=timer,
Interact=above).

## Still pending (next, per our design chat)
The big one — the **Custom Library location/travel model** (cities → hotspots → one-click
walk/teleport), then **Teleport** + **Find-nearest-bank** actions, then **Kill** emergencies
(eat/run/teleport at low HP), then drag-to-reorder, the single-click **breakdown panel** (repeat
moved into it + dials for repeat/loop), the **10-loop free cap** (VIP flag), auto-delay checkbox,
save/load UX cleanup, and the **stop→replay teardown** bug.
