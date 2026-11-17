## Which file do I download?

| File | System | What it is |
|------|--------|------------|
| `HopSync-{numeric}.msi` | Windows 10 and 11 (64-bit) | Installer. **Recommended for Windows.** |
| `HopSync-{numeric}.dmg` | macOS on Apple silicon (M1 or newer) | Disk image with the app. **Recommended for Mac.** |
| `hopsync_{numeric}_amd64.deb` | Debian, Ubuntu, Linux Mint and other Debian-based distributions (64-bit) | Package. **Recommended for Linux.** |
| `HopSync-{version}-portable.zip` | Windows, macOS (Intel or Apple silicon) and any Linux | The app without an installer. Needs Java 21. |

The `.msi`, `.dmg` and `.deb` bring their own Java: there is nothing else to install.

### Windows: `HopSync-{numeric}.msi`

Open it and follow the steps. It adds HopSync to the Start menu and the desktop, and lets you choose the install folder. A newer `.msi` replaces the installed version and keeps your packs and settings.

The installer is not signed yet, so Windows may show "Windows protected your PC". Choose **More info › Run anyway**.

### macOS: `HopSync-{numeric}.dmg`

Open it and drag **HopSync** into **Applications**. It runs on Macs with an Apple chip (M1 or newer); on an Intel Mac, use the portable ZIP.

The app is not signed yet. The first time, right-click HopSync in Applications and choose **Open**, or go to **System Settings › Privacy & Security** and choose **Open Anyway**.

### Linux: `hopsync_{numeric}_amd64.deb`

Install it from your software center, or from a terminal:

```bash
sudo apt install ./hopsync_{numeric}_amd64.deb
```

HopSync then appears in the applications menu.

### Any system: `HopSync-{version}-portable.zip`

For computers where you cannot or prefer not to install anything, or that the other files do not cover. It needs **Java 21 or newer**, for example from [adoptium.net](https://adoptium.net/).

Unzip it anywhere and open the launcher for your system: `HopSync.bat` on Windows, `HopSync.command` on macOS or `HopSync.sh` on Linux.

