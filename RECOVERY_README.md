# DreamMan — IDE / Build Recovery (fixes the broken build + missing script)

**This unbreaks your project. Apply it before anything else.** It's independent of Patches 2 & 3
— it only fixes the IntelliJ config, not your code.

## What went wrong (and what these files fix)

Two separate problems:

1. **Build fails with "package … does not exist".** The `DreamMan.iml` shipped in Patch 0
   replaced your working module file and accidentally removed the **`repository2`** library
   reference (the DreamBot client jars — which provide *both* `org.dreambot.api.*` **and**
   `com.google.gson.*`) and pointed the source root at `/main` instead of the module root.
   That's my mistake. Nothing is lost — the library definition still exists in
   `.idea/libraries/repository2.xml`; the module just stopped referencing it.

2. **Script disappears from DreamBot after building.** Your artifact lost the
   **`'DreamMan' compile output`** element, so `DreamMan.jar` was being built with only
   `META-INF/MANIFEST.MF` inside and **none of your compiled classes**. DreamBot scans the jar,
   finds no `@ScriptManifest`, and lists nothing (it still logs "refreshed scripts", which is
   just the folder re-scan — not a match).

This recovery contains two files that restore your original, known-working setup:
- `DreamMan.iml` — re-adds the `repository2` library + annotations, sets the source root back to
  the module root, and excludes `archive/` (so the files the cleanup moved aren't compiled).
- `.idea/artifacts/DreamMan.xml` — puts the `module-output` (your compiled classes) back inside
  `DreamMan.jar`.

## Important: the version number

DreamBot shows the version from the **`@ScriptManifest` annotation in `DreamBot.java`**, not from
the jar's `MANIFEST.MF`. To bump it, edit that file:

```java
@ScriptManifest(author = "ETA", name = "DreamBot", version = 3.0, category = Category.MISC)
```

Change `version = 3.0` to `3.1` (etc.). Editing `META-INF/MANIFEST.MF` does nothing to the
displayed version — that's what sent you down the wrong path.

## How to apply (do it with IntelliJ CLOSED)

IntelliJ rewrites these files on exit, so:

1. **Close IntelliJ completely.**
2. Unzip this over your module folder — the one that contains `main` and your current
   `DreamMan.iml`:
   `C:\Users\Elayj\IdeaProjects\DreamMan\DreamMan\`
   It overwrites `DreamMan.iml` and `.idea\artifacts\DreamMan.xml`.
3. **Reopen the project in IntelliJ.** Give it a moment to index.
4. Build → Build Artifacts… → DreamMan → **Rebuild**.
5. In DreamBot: refresh scripts (or reopen the SDN/script manager, tick **Local**). "DreamBot"
   by author "ETA" should appear.

## If you prefer to fix it by hand (or to verify)

**Restore the library (fixes the build):**
File → Project Structure → **Modules** → DreamMan → **Dependencies** tab. If `repository2` isn't
listed: click **+** → **Library** → pick `repository2`. If it's not offered, use **+** → **JARs or
Directories** and select `C:\Users\Elayj\DreamBot\BotData\repository2`. Apply.

**Check the source root (fixes "package does not match"):**
Same window → **Modules** → DreamMan → **Sources** tab. The folder that *contains* `main` (the
module root) must be marked **Sources** (blue). `main` itself must **not** be the source root —
your packages are `main.scripts`, `main.menu`, etc.

**Restore the artifact (fixes the disappearing script):**
File → Project Structure → **Artifacts** → DreamMan. In **Output Layout**, click the
`DreamMan.jar` node, then in **Available Elements** on the right, double-click
**`'DreamMan' compile output`** to drop it into the jar. You should end up with both:
```
DreamMan.jar
├─ META-INF
│  └─ MANIFEST.MF
└─ 'DreamMan' compile output      ← this is the part that was missing
```
Output directory must be `C:\Users\Elayj\DreamBot\Scripts`. Apply.

## Verify the jar actually has classes

After building, open `C:\Users\Elayj\DreamBot\Scripts\DreamMan.jar` with 7-Zip / WinRAR.
It should contain `main\scripts\DreamBot.class` **and many other class files** — not just a
`META-INF` folder. If you only see `META-INF`, the compile-output element still isn't in the
artifact (redo the Artifacts step above).

## Notes

- Don't bundle Gson into the jar — the DreamBot client already provides it at runtime (that's
  why `repository2` resolves `com.google.gson`). Adding it yourself can cause conflicts.
- None of my future patches will ship an `.iml` again — that was the thing that bit you.
- Once this build is green, Patch 2 and Patch 3 will compile and run normally (I verified the
  source compiles cleanly against the DreamBot API + Gson).
