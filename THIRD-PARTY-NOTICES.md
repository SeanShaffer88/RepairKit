# Third-Party Notices

RepairKit is distributed under the GNU General Public License v3.0. It also contains or links against the components
below. Each component remains under its own licence, and that licence governs the component.

This file is informational, not legal advice. When in doubt, the licence text published by the component's author
prevails.

## Java libraries bundled in the executable

| Component | Version | Licence | Project |
| --- | --- | --- | --- |
| JNA (Java Native Access) | 5.19.1 | LGPL 2.1 or later, or Apache 2.0 (dual-licensed) | https://github.com/java-native-access/jna |
| Gson | 2.14.0 | Apache 2.0 | https://github.com/google/gson |
| two-slices | 0.9.7 | Apache 2.0 | https://github.com/sshtools/two-slices |

JetBrains Annotations 26.1.0 (Apache 2.0) is used at compile time only and is not included in the distribution.

## Bundled Java runtime

The release ZIP contains a trimmed OpenJDK 17 runtime (Eclipse Temurin 17.0.11) licensed under the GNU General Public
License version 2 with the Classpath Exception. The runtime keeps its own `legal/` directory with the full notices of
every component it contains.

## Bundled tools

The tools below are redistributed unmodified, exactly as published by their vendors, inside the archives in
`src/main/resources/bin`. RepairKit only extracts and starts them; it does not modify them. **The vendor's own licence
and terms of use apply**, including any restriction on commercial use, and the vendor, not RepairKit, provides support
for them.

| Tool | Vendor / project | Notes |
| --- | --- | --- |
| 7-Zip (`7zr.exe`) | Igor Pavlov – https://7-zip.org | GNU LGPL 2.1 or later, with the unRAR restriction |
| CPU-Z, HWMonitor | CPUID – https://www.cpuid.com | Freeware |
| HWiNFO | Martin Malík – https://www.hwinfo.com | Freeware for personal use |
| HeavyLoad | JAM Software – https://www.jam-software.com/heavyload | Freeware |
| WizTree | Antibody Software – https://diskanalyzer.com | Free for personal use |
| Everything | voidtools – https://www.voidtools.com | Freeware |
| Emsisoft Emergency Kit | Emsisoft – https://www.emsisoft.com/en/home/emergency-kit | Freeware |
| Sophos Scan & Clean | Sophos – https://www.sophos.com/en-us/free-tools/virus-removal-tool | Freeware |
| CrystalDiskInfo, CrystalDiskMark | Crystal Dew World – https://crystalmark.info | MIT |
| Recuva | Piriform – https://www.ccleaner.com/recuva | Freeware |
| DiskGenius | Eassos – https://www.diskgenius.com | Free and paid editions; free edition is bundled |
| Rufus | Pete Batard – https://rufus.ie | GNU GPL v3 |
| Autoruns, Process Explorer, Process Monitor, TCPView | Microsoft Sysinternals – https://learn.microsoft.com/sysinternals | Microsoft Sysinternals licence |
| WinMTR | Appnor – https://sourceforge.net/projects/winmtr | GNU GPL v2 |
| BlueScreenView, RegScanner, USBDeview, USBLogView | NirSoft – https://www.nirsoft.net | Freeware |
| NVCleanstall | TechPowerUp – https://www.techpowerup.com/nvcleanstall | Freeware |
| Display Driver Uninstaller | Wagnardsoft – https://www.wagnardsoft.com | Freeware |

Tool names and icons are trademarks of their respective owners and are used only to identify the tools. Their presence
does not imply any endorsement of RepairKit.

Everything is bundled under a custom executable name (`Everything-RepairKit.exe`) so that it can be started from a
private folder without conflicting with an installed copy.

## Build tools (not distributed)

Gradle, the Gradle Shadow plugin, the launch4j Gradle plugin (BSD 3-Clause), launch4j itself, and JUnit 5 (EPL 2.0) are
used to build and test RepairKit. They are not part of the release ZIP.

## Reporting a licensing problem

If you believe a component is redistributed incorrectly, open an issue at
https://github.com/Foulest/RepairKit/issues so it can be corrected.
