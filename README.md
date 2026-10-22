> **Fork identity: contribution fork.** Derived from [Foulest/RepairKit](https://github.com/Foulest/RepairKit). Hardening and release-pipeline work is proposed in [PR #1](https://github.com/SeanShaffer88/RepairKit/pull/1). It is not merged into this default branch. Inherited badges and download links refer to Foulest/RepairKit, not a release of the proposed hardening. See [fork provenance](PROVENANCE.md) for ownership and reporting routes.

# RepairKit

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Build](https://github.com/Foulest/RepairKit/actions/workflows/build.yml/badge.svg)](https://github.com/Foulest/RepairKit/actions/workflows/build.yml)
[![Downloads](https://img.shields.io/github/downloads/Foulest/RepairKit/total.svg)](https://github.com/Foulest/RepairKit/releases)

**RepairKit** is an all-in-one Java-based Windows repair and maintenance toolkit. It combines the routine repairs a
technician performs on a slow or infected PC with a library of portable diagnostic tools, in one self-contained
Windows executable that ships its own Java runtime.

[Softpedia](https://softpedia.com/get/System/OS-Enhancements/RepairKit.shtml)
• [MajorGeeks](https://m.majorgeeks.com/files/details/repairkit.html)
• [Uptodown](https://repairkit.en.uptodown.com/windows)
• [AlternativeTo](https://alternativeto.net/software/repairkit/about)

### 

[![RepairKit Banner](https://i.imgur.com/EfAmXt4.png)](https://github.com/Foulest/RepairKit/releases)

### 

## Install & Setup Guide

<p align="center">
  <a href="https://youtu.be/EMVeKIvVPcg" title="Watch on YouTube"><img src="https://i.imgur.com/OOyZmUe.png" alt="Watch on YouTube"></a>
</p>

## Requirements

- 64-bit Windows 10 or 11, or Windows Server 2016 or later.
- Administrator rights. RepairKit requests the highest available execution level and refuses to start without
  elevation.
- No Java installation is required: the release ZIP contains a trimmed OpenJDK 17 runtime (`jre/`).

## Features

### Automatic Repairs

The panel is a checklist of independent repairs. Each repair reports its own result (succeeded, partially succeeded,
failed, skipped, or cancelled), so the summary reflects what actually happened instead of assuming success.

- **Create Restore Point** – creates a System Restore checkpoint and verifies that it exists before other changes run.
- **Delete System Policies** – removes restrictive policy keys and values left behind by malware or previous tools.
- **Registry Tweaks** – applies the categories enabled in `config/registry_tweaks.json`.
- **System Tweaks** – repairs scheduled tasks, re-registers shell components, and removes the `defaultuser0` account.
- **Disable Windows Services** – disables the services listed in `config/services_tweaks.json`.
- **Disable Windows Features** – disables legacy features listed in `config/features_tweaks.json`.
- **Windows Defender Tweaks** – applies the Defender preferences in `config/windows_defender.json`.
- **Remove Junk Files** – cleans temporary/cache folders that are older than the configured age.
- **Remove Bloatware** – removes matching Appx packages for every user and from the provisioned image.
- **Repair Disk Issues** – runs DISM `RestoreHealth` and `sfc /scannow` and reports their exit codes.
- **Update Outdated Programs** – upgrades outdated WinGet packages, honouring `config/update_programs.json`.
- **Advanced** – network-stack reset, product-key clearing, and boot clock changes. Off by default, listed separately,
  and marked as high risk because they change boot, network, or licensing settings.

Safety behaviour:

- Every registry value that is changed is written to a `.reg` backup in
  `%LOCALAPPDATA%\RepairKit\backups\<timestamp>-repairs` before the change, including "value did not exist" markers so
  that importing the backup removes values RepairKit created. Deleted keys are exported with `reg export` first.
- The confirmation dialog lists every selected repair with its risk level, warns when no restore point will be created,
  and warns when the system drive is BitLocker-protected or the PC is domain joined.
- Cancel stops the current command and marks the remaining repairs as cancelled; the window cannot be closed silently
  while a run is in progress.
- A full log is kept in `%LOCALAPPDATA%\RepairKit\logs`, viewable in the app.

> **Note:** The previously available **Remove Malware** option has been replaced by **Windows Defender Tweaks**, which
> applies recommended Defender settings. Manual scans are available through Emsisoft or Sophos in Useful Programs.

![Automatic Repairs](https://i.imgur.com/mnkUQV1.png)

### Useful Programs

32 portable tools and vendor links, in three pages, extracted to a per-launch random folder:

- **CPU-Z**, **HWMonitor**, **HWiNFO** – hardware identification and sensor monitoring.
- **HeavyLoad** – stress testing.
- **WizTree**, **Everything** – disk usage and instant file search.
- **Emsisoft Emergency Kit**, **Sophos Scan & Clean** – on-demand malware scanners.
- **CrystalDiskInfo**, **CrystalDiskMark** – drive health and benchmarks.
- **Recuva**, **DiskGenius** – data recovery and partition management.
- **Rufus** – bootable USB creation.
- **Autoruns**, **Process Explorer**, **Process Monitor**, **TCPView** – Sysinternals diagnostics.
- **WinMTR**, **BlueScreenView**, **RegScanner**, **USBDeview**, **USBLogView** – network and crash diagnostics.
- **NVCleanstall**, **Display Driver Uninstaller** – driver maintenance.
- Links to **7-Zip**, **Notepad++**, **Twinkle Tray**, **FanControl**, **Bitwarden**, **Sophos Home**,
  **uBlock Origin**, and **Osprey**.

Each bundled tool is extracted into `%LOCALAPPDATA%\RepairKit\tools\<tool>-<random>` immediately before launch, the
executable is verified to be inside that folder, and its SHA-256 hash is written to the log. Extraction folders older
than a day are deleted on the next start.

![Useful Programs](https://i.imgur.com/NAb78GU.png)

### System Shortcuts

Twelve settings pages and management consoles: Apps & Features, Startup Apps, Windows Update, Windows Security,
Display, Storage, Sound, System Information, Task Manager, Device Manager, Disk Cleanup, and System Configuration.
Shortcuts that do not work in Safe Mode are disabled with an explanation.

![System Shortcuts](https://i.imgur.com/t5pOrr8.png)

## Configuration

Settings live in the `config` folder next to the executable (falling back to `%LOCALAPPDATA%\RepairKit\config` when the
program folder is read-only). Bundled defaults are copied on first use. Configuration files are validated before a run;
a malformed file is reported instead of silently skipping part of the plan.

| File | Purpose |
| --- | --- |
| `registry_tweaks.json` | Registry categories applied by **Registry Tweaks** |
| `system_policies.json` | Policy keys removed by **Delete System Policies** |
| `system_tweaks.json` | Scheduled tasks, shell components, and the advanced network/boot options |
| `services_tweaks.json` | Service start types changed by **Disable Windows Services** |
| `features_tweaks.json` | Optional Windows features disabled by **Disable Windows Features** |
| `windows_defender.json` | Defender, firewall, and ASR settings |
| `junk_files.json` | Temporary folders, age threshold, exclusions, Recycle Bin opt-in |
| `bloatware.json` | Appx package name patterns |
| `disk_issues.json` | WMI, DISM, and SFC toggles |
| `update_programs.json` | WinGet package IDs or substrings to exclude from upgrades |

**Only modify these files if you know what you are doing.** Every command uses an absolute system path, values are
validated before they are passed to a command, and a failed action is reported rather than ignored.

## Download and Run

1. Download `RepairKit-X.X.X.zip` from the [Releases page](https://github.com/Foulest/RepairKit/releases/latest).
2. Verify it against `SHA256SUMS.txt` from the same release.
3. Extract the ZIP (for example with [7-Zip](https://7-zip.org)).
4. Run `RepairKit-X.X.X.exe` and accept the User Account Control prompt.

RepairKit does not install itself and does not write outside `%LOCALAPPDATA%\RepairKit` except for the changes listed in
the confirmation dialog. It contains no telemetry. The only network request the app makes on its own is an update check
against the public GitHub Releases API; it can be ignored or the version skipped.

## Building

Requires JDK 17 or later (Gradle downloads the dependencies).

```bat
gradlew.bat build        :: compile and run the unit tests
gradlew.bat createExe    :: build the Windows launcher (launch4j), requires Windows
gradlew.bat release      :: build the launcher, stage the distribution, write SHA256SUMS.txt, and create the ZIP
```

On macOS or Linux use `./gradlew` instead. The packaged ZIP is written to `build/dist/RepairKit-X.X.X.zip`.

`createExe` is a Windows-only task because launch4j needs to run on Windows. The code signing step is enabled by setting
`REPAIRKIT_SIGNTOOL` (path to `signtool.exe`) and `REPAIRKIT_CERT_SHA1`; without them the build logs that signing was
skipped. Release artifacts are otherwise unsigned and SmartScreen may warn on first run.

### Project layout

```
src/main/java/net/foulest/repairkit/
  RepairKit.java            application window and startup validation
  Messages.java             ResourceBundle access
  model/                    repair options, tasks, results, statuses
  service/                  repair catalogue, runner, tool launcher, catalogues
  util/                     process, registry, file, UI, update, and OS helpers
  util/config/tasks/        configuration-driven task builders
src/main/resources/
  config/                   default configuration files
  bin/                      bundled tool archives and 7zr.exe
  icons/                    application and tool icons
  messages.properties       user-facing text
```

User-facing text is in `messages.properties`; adding `messages_<locale>.properties` translates the interface without
code changes. Missing keys fall back to the key itself.

## Tests

`./gradlew test` runs the JUnit 5 suite. The tests cover version comparison, command-result handling, configuration
parsing, junk-file path safety, and the integrity of the tool/shortcut catalogues. Windows-specific behaviour is
exercised by the Windows CI job, which builds the launcher.

## Getting Help

For support or queries, open an issue in the [Issues section](https://github.com/Foulest/RepairKit/issues). Security
reports should follow [SECURITY.md](SECURITY.md).

## License

RepairKit is licensed under the GNU General Public License v3.0; see [LICENSE](LICENSE). Third-party components and
licences are listed in [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md). Fork ownership, upstream credit, and the route for
reporting issues are described in [PROVENANCE.md](PROVENANCE.md).
