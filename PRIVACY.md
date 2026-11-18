# HopSync privacy policy

Last updated: 2026-11-17

HopSync is a desktop app that keeps Minecraft modpacks in sync. It has no accounts of its own, runs no server and
collects no personal data: no analytics, no telemetry, no crash reports, no advertising and no identifiers. Everything
it knows stays on your computer, except what this page lists.

## Network connections

HopSync only connects to the services below. Each request carries what any HTTP request carries: your IP address and
a user agent naming the app and its version (`JJGarciaMartinez/hopsync/<version> (https://github.com/JJGarciaMartinez/hopsync)`).
Nothing else about you or your computer is sent.

| Service | When | What it receives |
|---------|------|------------------|
| GitHub API (`api.github.com`), HopSync's own releases | Once each time HopSync starts, unless you turn off **Check for new HopSync versions** (*Buscar versiones nuevas de HopSync*) in Settings › General (*Ajustes › General*) | A request for the list of HopSync releases, to tell you when a newer version is out |
| The link of each pack you add (usually GitHub) | When you add a pack, open it in the list, or check it for changes (also at start if **Check for changes on open** (*Buscar cambios al abrir*) is on) | A request for the pack's manifest |
| The download addresses listed in a pack's manifest (for example `cdn.modrinth.com` or GitHub), chosen by whoever publishes the pack | When you apply an update you reviewed | Requests for the mod and config files |
| Modrinth API (`api.modrinth.com`) | Only for packs you create in HopSync: searching mods, listing versions, and checking those packs | Your search text and the Minecraft version and mod loader of the pack |
| GitHub (`github.com`, `api.github.com`) with your account | Only if you connect your GitHub account in Settings › Account (*Ajustes › Cuenta*), and when you publish a pack | The sign-in requests, and the pack you publish (see below) |

Links you open from HopSync (a mod's page, a release page, GitHub's sign-in page) open in your web browser.

These services handle the requests under their own policies:
[GitHub General Privacy Statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement),
[Modrinth Privacy Policy](https://modrinth.com/legal/privacy). The sites that host a pack's link and files are chosen by
whoever publishes the pack.

## Publishing a pack

If you connect your GitHub account and publish a pack you created, HopSync creates a **public** repository in your
account and uploads the pack to it: its name, mod list, server address, the configs and the mod files you chose to
include, and a README. Anyone can see that repository. HopSync shows you the files it will share and asks you to
confirm them before publishing.

Your GitHub sign-in token is kept in your system's keychain (Keychain on macOS, Credential Manager on Windows, Secret
Service on Linux), or only in memory for the current session when there is no keychain. It is only sent to GitHub,
and it never appears on screen or in the logs. **Disconnect** forgets it on this computer; to revoke it, remove HopSync
under [Authorized OAuth Apps](https://github.com/settings/applications) on GitHub.

## Data on your computer

HopSync keeps its data in your user's settings folder: `%APPDATA%\HopSync` on Windows,
`~/Library/Application Support/HopSync` on macOS and `$XDG_CONFIG_HOME/hopsync` (usually `~/.config/hopsync`) on
Linux. There it saves your settings and packs, the last manifest of each pack, the packs you create, and log files
(up to three files of 1 MB each). In each game folder it uses, it keeps a `.hopsync` folder recording the files it
installed.

None of this leaves your computer on its own. **Copy diagnostic info** in Settings › About (*Ajustes › Acerca de*) puts your app version,
system, folder paths and packs on your clipboard, and **Open logs folder** shows the logs, so you can attach them to a
bug report if you choose to. Read them before sharing: they include folder paths, which may contain your user name.

Uninstalling HopSync removes the app; delete the settings folder above to remove its data too. The files it installed
in your game folders stay there.

## Changes and contact

Changes to this policy are published in this file, and its history is in the repository. For questions, open an issue
at <https://github.com/JJGarciaMartinez/hopsync/issues>.
