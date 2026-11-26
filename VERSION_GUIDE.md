# Versioning System - HopSync

## Version format

Every HopSync version is `YY.MAJOR.FIXES`, with an optional test stage at the end:

- **YY** (Year): last two digits of the **planned** release year (work in December 2025 for a 2026 release is
  `26.1.0`).
- **MAJOR**: version number within that year (starts at 1, never 0).
- **FIXES**: fix number of that version (0 for its first release).
- **Stage** (test builds only): `-a.N` (alpha), `-b.N` (beta) or `-rc.N` (release candidate), where `N` counts
  the builds of that stage from 1 to 29.

A test build always names the version it is heading to, and the release drops the stage:

```
26.2.0-a.1 → 26.2.0-b.1 → 26.2.0-b.2 → 26.2.0-rc.1 → 26.2.0  (release)
                                                         ↓
                              26.2.1-b.1 → 26.2.1  (first fix, with an optional beta)
                                                         ↓
                                         26.3.0-b.1 → 26.3.0  (next version)
```

| Version | Meaning |
|---------|---------|
| `26.2.0-a.1` | First alpha of 26.2.0: early development, unstable |
| `26.2.0-b.1` | First beta of 26.2.0: feature-complete, testing |
| `26.2.0-b.2` | Second beta of 26.2.0 |
| `26.2.0-rc.1` | First release candidate of 26.2.0: final testing |
| `26.2.0` | **Release** of 26.2.0 |
| `26.2.1` | First fix of 26.2.0 |
| `27.1.0` | First release of 2027 |

Test builds show "(test build)" next to the version in the app, and their GitHub Releases are prereleases. A
test build is offered newer test builds and releases; a release copy is only offered releases.

Tags from before this scheme (`b26.2.0`, `beta-v26.1.4`) are ignored by the app's check for newer versions.

## Installer version

`jpackage --app-version` only takes one to three numbers, and a Windows installer replaces the installed one only
when its number is higher. So Gradle derives the installers' number from `app.version`:

```
YY.MAJOR.(FIXES × 100 + stage + N)      a = 0, b = 30, rc = 60, release = 90 (N = 0)
```

| `app.version` | Installer version |
|---------------|-------------------|
| `26.2.0-a.1` | `26.2.1` |
| `26.2.0-b.1` | `26.2.31` |
| `26.2.0-b.2` | `26.2.32` |
| `26.2.0-rc.1` | `26.2.61` |
| `26.2.0` | `26.2.90` |
| `26.2.1-b.1` | `26.2.131` |
| `26.2.1` | `26.2.190` |

Each version sorts above the one before it, on every platform. The limits come from Windows (at most
`255.255.65535`) and from the formula: N up to 29 and FIXES up to 654. The installer version shows in Windows'
installed apps and in the macOS app info; everything players download or see in HopSync uses `app.version`
(`HopSync-26.2.0-b.1.msi`), because `jpackage` renames its output after building.

## `version.properties`

| Property | Used for |
|----------|----------|
| `app.version` | What the app shows (status bar, About), the JAR, ZIP and installer names, the release tag, and the installer version derived from it |
| `app.name`, `app.vendor` | Name and vendor of the app and its installers |
| `app.win.upgrade.uuid` | Lets a new `.msi` replace the installed one. Never change it |

Gradle reads the file when it configures the build and copies it into the JAR, where `AppVersion` reads it at
runtime.

## Changing the version

```bash
./gradlew updateVersion -PnewVersion=26.2.0-b.1
./gradlew updateVersion -PnewVersion=26.2.0
```

The task rewrites `app.version`, prints the installer version, and refuses a version outside the scheme or the
limits above. Editing the file by hand works too; `jpackage` then refuses a bad version.

## Releases

A release is a tag equal to `app.version` (`26.2.0-b.1`, `26.2.0`) pushed to GitHub; the Release workflow builds
the installers and drafts the GitHub Release. See [BUILD_GUIDE.md](BUILD_GUIDE.md#releasing).
