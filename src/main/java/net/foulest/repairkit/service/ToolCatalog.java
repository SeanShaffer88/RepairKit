/*
 * RepairKit - an all-in-one Java-based Windows repair and maintenance toolkit.
 * Copyright (C) 2026 Foulest (https://github.com/Foulest)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package net.foulest.repairkit.service;

import net.foulest.repairkit.model.Tool;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The Useful Programs catalogue.
 * <p>
 * The previous implementation repeated about forty lines of layout code per
 * entry across 1,454 lines; this table plus {@link net.foulest.repairkit.panels.UsefulPrograms}
 * renders the same entries with one card builder.
 *
 * @author Foulest
 */
public final class ToolCatalog {

    private static final String ICONS = "icons/";

    private ToolCatalog() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Gets the catalogue, grouped into pages.
     *
     * @return the pages
     */
    public static @NotNull List<List<Tool>> pages() {
        return List.of(pageOne(), pageTwo(), pageThree());
    }

    private static @NotNull List<Tool> pageOne() {
        return List.of(
                bundled("cpu-z", "CPU-Z", "Version 2.20.2",
                        "Identify your system hardware details.",
                        "CPU-Z.png", "CPU-Z.7z", "CPU-Z.exe"),
                bundled("hwmonitor", "HWMonitor", "Version 1.66.0",
                        "Monitor hardware temperatures and voltages.",
                        "HWMonitor.png", "HWMonitor.7z", "HWMonitor.exe"),
                bundled("hwinfo", "HWiNFO", "Version 8.50-6020",
                        "Detailed hardware information and sensors.",
                        "HWiNFO.png", "HWiNFO.7z", "HWiNFO.exe"),
                bundled("heavyload", "HeavyLoad", "Version 4.0.0.400",
                        "Stress system components to test stability.",
                        "HeavyLoad.png", "HeavyLoad.7z", "HeavyLoad.exe"),
                bundled("wiztree", "WizTree", "Version 4.31",
                        "Show which files and folders use the most space.",
                        "WizTree.png", "WizTree.7z", "WizTree.exe", true),
                bundled("everything", "Everything", "Version 1.5.0.1418b",
                        "Instant file search across indexed volumes.",
                        "Everything.png", "Everything.7z", "Everything-RepairKit.exe"),
                bundled("emsisoft", "Emsisoft Emergency Kit", "Version: Auto-Updated",
                        "Scan for malware with Emsisoft.",
                        "Emsisoft.png", "Emsisoft.7z", "Emsisoft.exe", true),
                bundled("sophos", "Sophos Scan & Clean", "Version: Auto-Updated",
                        "Scan for malware with Sophos.",
                        "Sophos.png", "Sophos.7z", "Sophos.exe", true),
                bundled("crystaldiskinfo", "CrystalDiskInfo", "Version 9.9.2",
                        "Monitor drive health and SMART status.",
                        "CrystalDiskInfo.png", "CrystalDiskInfo.7z", "CrystalDiskInfo.exe"),
                bundled("crystaldiskmark", "CrystalDiskMark", "Version 9.0.3",
                        "Benchmark drive read and write speeds.",
                        "CrystalDiskMark.png", "CrystalDiskMark.7z", "CrystalDiskMark.exe"),
                bundled("recuva", "Recuva", "Version 1.54.120",
                        "Recover deleted files.",
                        "Recuva.png", "Recuva.7z", "recuva.exe"),
                bundled("diskgenius", "DiskGenius", "Version 6.2.0.1829",
                        "Partition management and data recovery.",
                        "DiskGenius.png", "DiskGenius.7z", "DiskGenius.exe")
        );
    }

    private static @NotNull List<Tool> pageTwo() {
        return List.of(
                bundled("rufus", "Rufus", "Version 4.15.2396",
                        "Create bootable USB drives.",
                        "Rufus.png", "Rufus.7z", "rufus.exe"),
                bundled("autoruns", "Autoruns", "Version 14.3",
                        "Review every program that starts with Windows.",
                        "Autoruns.png", "Autoruns.7z", "Autoruns.exe"),
                bundled("process-explorer", "Process Explorer", "Version 17.06",
                        "Inspect processes, handles, and DLLs.",
                        "ProcessExplorer.png", "ProcessExplorer.7z", "ProcessExplorer.exe"),
                bundled("process-monitor", "Process Monitor", "Version 4.04",
                        "Trace file system, registry, and network activity.",
                        "ProcessMonitor.png", "ProcessMonitor.7z", "ProcessMonitor.exe"),
                bundled("tcpview", "TCPView", "Version 4.19",
                        "See active network connections.",
                        "TCPView.png", "TCPView.7z", "TCPView.exe"),
                bundled("winmtr", "WinMTR", "Version 1.0.0",
                        "Trace network routes to diagnose connectivity.",
                        "WinMTR.png", "WinMTR.7z", "WinMTR.exe", true),
                bundled("bluescreenview", "BlueScreenView", "Version 1.55",
                        "Read Windows crash dump information.",
                        "BlueScreenView.png", "BlueScreenView.7z", "BlueScreenView.exe"),
                bundled("regscanner", "RegScanner", "Version 2.75",
                        "Search the registry for specific values.",
                        "RegScanner.png", "RegScanner.7z", "RegScanner.exe"),
                bundled("usbdeview", "USBDeview", "Version 3.07",
                        "List USB devices that have been connected.",
                        "USBDeview.png", "USBDeview.7z", "USBDeview.exe"),
                bundled("usblogview", "USBLogView", "Version 1.26",
                        "Show USB connection and removal events.",
                        "USBLogView.png", "USBLogView.7z", "USBLogView.exe"),
                bundled("nvcleanstall", "NVCleanstall", "Version 1.19.0",
                        "Install NVIDIA drivers without extras.",
                        "NVCleanstall.png", "NVCleanstall.7z", "NVCleanstall.exe", true),
                bundled("ddu", "Display Driver Uninstaller", "Version 18.1.5.6",
                        "Remove display drivers completely.",
                        "DDU.png", "DDU.7z", "Display Driver Uninstaller.exe")
        );
    }

    private static @NotNull List<Tool> pageThree() {
        return List.of(
                link("7zip", "7-Zip", "Free",
                        "Open-source file archiver.",
                        "7-Zip.png", "https://7-zip.org/download.html"),
                link("notepad-plus-plus", "Notepad++", "Free",
                        "Open-source text editor.",
                        "Notepad++.png", "https://notepad-plus-plus.org/downloads"),
                link("twinkle-tray", "Twinkle Tray", "Free",
                        "Control monitor brightness.",
                        "TwinkleTray.png", "https://twinkletray.com"),
                link("fancontrol", "FanControl", "Free",
                        "Control fan speeds.",
                        "FanControl.png", "https://getfancontrol.com"),
                link("bitwarden", "Bitwarden", "Free",
                        "Open-source password manager.",
                        "Bitwarden.png", "https://bitwarden.com/download"),
                link("sophos-home", "Sophos Home", "Paid",
                        "Home antivirus from Sophos.",
                        "Sophos.png", "https://home.sophos.com"),
                link("ublock-origin", "uBlock Origin", "Free",
                        "Browser content blocker.",
                        "uBlockOrigin.png", "https://ublockorigin.com"),
                link("osprey", "Osprey", "Free",
                        "Browser protection extension.",
                        "Osprey.png", "https://osprey.ac")
        );
    }

    private static @NotNull Tool bundled(@NotNull String id, @NotNull String name,
                                         @NotNull String subtitle, @NotNull String description,
                                         @NotNull String icon, @NotNull String archive,
                                         @NotNull String executable) {
        return new Tool(id, name, subtitle, description, ICONS + icon, archive, executable,
                null, false);
    }

    private static @NotNull Tool bundled(@NotNull String id, @NotNull String name,
                                         @NotNull String subtitle, @NotNull String description,
                                         @NotNull String icon, @NotNull String archive,
                                         @NotNull String executable, boolean windows10Only) {
        return new Tool(id, name, subtitle, description, ICONS + icon, archive, executable,
                null, windows10Only);
    }

    private static @NotNull Tool link(@NotNull String id, @NotNull String name,
                                      @NotNull String subtitle, @NotNull String description,
                                      @NotNull String icon, @NotNull String url) {
        return new Tool(id, name, subtitle, description, ICONS + icon, null, null, url, false);
    }

    /**
     * Gets all bundled tools.
     *
     * @return the bundled tools
     */
    public static @NotNull List<Tool> bundledTools() {
        return pages().stream()
                .flatMap(List::stream)
                .filter(Tool::isBundled)
                .toList();
    }
}
