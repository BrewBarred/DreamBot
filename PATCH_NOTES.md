# DreamMan — Patch B.6 (v1.0.17): XP/hour everywhere + tracker/overlay parity

**Drop type:** drag-and-drop over your project root (IntelliJ **closed**), reopen, Rebuild
Project → Build Artifacts → DreamMan → Rebuild. Source only — no `.iml`/`.idea` files.

---

## XP/hour on the overlay

The on-screen skill overlay's expanded card now shows **xp/hour** on its own line, under the
level bar (e.g. `132k xp/hr`). It was already in the tracker tab; now both surfaces show it.

## Tracker tab now mirrors the overlay

The overlay card draws a **level progress bar**; the tracker tab was showing the level as text
only. The tab's detail block now leads with the same rounded **level progress bar**, so everything
the overlay shows is also in the menu:

| | Overlay card | Tracker tab |
|---|---|---|
| Skill icon | ✓ | ✓ |
| Login→current level | ✓ | ✓ |
| Level progress bar | ✓ | ✓ (added) |
| XP to level + time-to-level | ✓ | ✓ |
| **XP/hour** | ✓ (added) | ✓ |
| Gained this session | ✓ | ✓ |
| Goal bar + to-goal + time-to-goal | ✓ | ✓ |
| Projection (Nh) | — | ✓ (tab extra) |

Both stay live from your current XP/hr, and the card grows to fit the extra row (goal cards are
a touch taller).

## Verified here

The overlay's drawn text was captured directly and confirmed to include `132k xp/hr` alongside
`Lvl 1→40`, `To lvl … · time`, `+gained`, and `To goal … · time`. The tracker panel's components
were inspected: level bar (40/100), `XP/HR: 131,998`, gained, to-level, time-to-level, projection,
goal label, and goal bar (66%) all present. Patch B regression suite 28/28. UI-health probe: 0
`no ComponentUI` errors, progress bar alive, every button skinned. Full tree (incl. `examples/`)
compiles, 0 errors.

## Check in your client

1. Track a skill and gain some XP — the overlay card now shows an xp/hr line.
2. Open the Skill Tracker tab — the tracked skill's detail shows the level bar plus the same
   xp/hr, gained, to-level/time, and goal bar you see on the overlay.
