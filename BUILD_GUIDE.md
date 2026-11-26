# HopSync - Build and Distribution Guide

Everything runs through the Gradle wrapper (`./gradlew`, or `gradlew.bat` on Windows). The app name, vendor and
version come from `version.properties` (see [VERSION_GUIDE.md](VERSION_GUIDE.md)).

## Tasks

| Task | Output | What it does |
|------|--------|--------------|
| `./gradlew build` | `desktop/build/libs/HopSync.jar` | Compiles, runs every test and builds the self-contained JAR (FlatLaf, JNA and fonts inside). |
| `./gradlew run` | | Launches the app from the sources. |
| `./gradlew jpackage` | `desktop/build/installer/` | Native installer of the OS it runs on: `.msi` on Windows, `.dmg` on macOS, `.deb` on Linux. Bundles a Java runtime. |
| `./gradlew portableZip` | `desktop/build/distributions/HopSync-<version>-portable.zip` | The JAR plus `HopSync.bat`, `HopSync.sh`, `HopSync.command` and a README, for players who already have Java 21. |
| `./gradlew updateVersion -PnewVersion=<v>` | `version.properties` | Sets `app.version` (e.g. `26.2.0-b.1`) and prints the installer version derived from it. |

## Installers (`jpackage`)

`jpackage` comes with the JDK 21 that Gradle uses to compile, and only builds installers for the OS it runs on,
so a release builds on Windows, macOS and Linux (the release workflow does it for you). What each one gets:

- **Common**: name and vendor `HopSync`, `--app-version` derived from `app.version` (see [VERSION_GUIDE.md](VERSION_GUIDE.md#installer-version)), files named
  with `app.version` (`HopSync-26.2.0-b.1.msi`), `-Xmx1024m`,
  `-Dfile.encoding=UTF-8`, icons from `packaging/`. Those icons (`icon.png` 512 px, `icon.ico` with 16 to 256 px,
  `icon.icns` with the 1x and 2x sizes) are written by `./gradlew brandIcons` from `kit/LogoIcon`, each size drawn
  at its exact pixels; run it after changing the mark and commit the result, never edit them by hand.
  `./gradlew brandAssets` does the same for the brand sheet's assets in `brand/` (`tokens.json`, `svg/`, `png/`,
  `pattern.svg`); its output is deterministic, so a run without changes leaves `git status` clean.
- **Windows (`.msi`)**: `app.win.upgrade.uuid` (never change it: it lets a new installer replace the old one,
  including the TCraftClient installers), Start menu entry, desktop shortcut and folder chooser.
  Needs the [WiX Toolset](https://wixtoolset.org/) on the PATH when building locally.
- **macOS (`.dmg`)**: identifier `app.hopsync.client`. Unsigned: the first time, players open it from
  System Settings → Privacy & Security → Open Anyway (macOS 15 dropped right-click → Open). If it does not open on another Mac,
  `./scripts/bash/diagnose-app.sh` inspects the built `.dmg`.
- **Linux (`.deb`)**: package `hopsync`, with a menu entry. Needs `dpkg-deb` and `fakeroot`.

Running `jpackage` again replaces the previous installer.

## Releasing

1. Merge `develop` into `main` (see [BRANCHING.md](BRANCHING.md)) with the new version already set:
   `./gradlew updateVersion -PnewVersion=26.2.0`, commit `chore: release 26.2.0`.
2. Tag the commit with exactly `app.version` and push the tag:
   ```bash
   git tag 26.2.0
   git push origin 26.2.0
   ```
3. The **Release** workflow (`.github/workflows/release.yml`) checks that the tag matches `app.version`, builds
   and tests on the three OSes, runs `jpackage` on each and `portableZip` on Linux, and drafts a GitHub Release
   with the `.msi`, `.dmg`, `.deb`, the portable ZIP and `SHA256SUMS.txt` (the installers are not signed, so
   players can check the hashes). Test builds (`-a.N`, `-b.N`, `-rc.N`) are marked as prereleases.
   Its notes start with the guide to each file from `.github/release-downloads.md` (`{version}` is filled
   in), followed by the changelog GitHub generates.
4. Review the draft on GitHub, add a short intro and what's new above the guide, and publish it.

## Before publishing

- Install the new version over the previous one and check that packs, settings and installed mods are kept.
- Open the app on a clean system (no settings) to see the first-run wizard.

## Troubleshooting

- **"Version …: expected YY.MAJOR.FIXES or YY.MAJOR.FIXES-(a|b|rc).N"**: fix `app.version` with `updateVersion`;
  old tags such as `b26.2.0` are no longer valid.
- **Windows: jpackage cannot find WiX**: install WiX and open a new terminal so the PATH is reloaded.
- **The version shown in the app is old**: `./gradlew clean build`.
