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
package net.foulest.repairkit.util;

import com.sun.jna.Platform;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinReg;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Windows environment, elevation, and operating system detection.
 * <p>
 * Everything here uses native APIs or registry values instead of parsing
 * localized console output, so the results are the same on every display
 * language.
 *
 * @author Foulest
 */
public final class WindowsUtil {

    private static final String NT_CURRENT_VERSION =
            "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion";

    /** Build number of the first Windows 10 release. */
    private static final int WINDOWS_10_BUILD = 10240;

    /** Build number of the first Windows 11 release. */
    private static final int WINDOWS_11_BUILD = 22000;

    /** Build number of Windows Server 2016. */
    private static final int SERVER_2016_BUILD = 14393;

    private static volatile OsInfo cachedOsInfo;
    private static volatile Boolean bitLockerStatus;
    private static volatile boolean bitLockerQueried;

    private WindowsUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Checks whether the JVM is running on Windows.
     *
     * @return {@code true} on Windows
     */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /**
     * Checks whether the JVM is 64-bit.
     *
     * @return {@code true} for a 64-bit runtime
     */
    public static boolean is64Bit() {
        return Platform.is64Bit();
    }

    /**
     * Checks whether the current process is elevated.
     * <p>
     * This uses the process token rather than the output of {@code net session},
     * which fails open when the Server service is stopped and is localized.
     *
     * @return {@code true} when the process has an elevated token
     */
    public static boolean isAdministrator() {
        if (!isWindows()) {
            return false;
        }

        WinNT.HANDLEByReference token = new WinNT.HANDLEByReference();

        if (!Advapi32.INSTANCE.OpenProcessToken(Kernel32.INSTANCE.GetCurrentProcess(),
                WinNT.TOKEN_QUERY, token)) {
            DebugUtil.warn("Failed to open the process token", new IOException("OpenProcessToken failed"));
            return false;
        }

        try {
            WinNT.TOKEN_ELEVATION elevation = new WinNT.TOKEN_ELEVATION();
            IntByReference returned = new IntByReference();

            if (!Advapi32.INSTANCE.GetTokenInformation(token.getValue(),
                    WinNT.TOKEN_INFORMATION_CLASS.TokenElevation, elevation, elevation.size(), returned)) {
                return false;
            }

            return elevation.TokenIsElevated > 0;
        } finally {
            Kernel32.INSTANCE.CloseHandle(token.getValue());
        }
    }

    /**
     * Gets the Windows directory.
     *
     * @return the Windows directory
     */
    public static @NotNull Path windowsDirectory() {
        String systemRoot = System.getenv("SystemRoot");

        if (systemRoot == null || systemRoot.isBlank()) {
            systemRoot = System.getenv("windir");
        }

        return Paths.get(systemRoot == null || systemRoot.isBlank()
                ? "C:\\Windows"
                : systemRoot);
    }

    /**
     * Resolves a system binary to its absolute path in System32.
     *
     * @param executable the executable name, for example {@code sc.exe}
     * @return the absolute path
     */
    public static @NotNull Path system32(@NotNull String executable) {
        return windowsDirectory().resolve("System32").resolve(executable);
    }

    /**
     * Resolves a system binary to its absolute path in SysWOW64.
     *
     * @param executable the executable name
     * @return the absolute path
     */
    public static @NotNull Path sysWow64(@NotNull String executable) {
        return windowsDirectory().resolve("SysWOW64").resolve(executable);
    }

    /**
     * Gets the per-user application data directory.
     *
     * @return the local application data directory
     */
    public static @NotNull Path localAppData() {
        String localAppData = System.getenv("LOCALAPPDATA");

        if (localAppData == null || localAppData.isBlank()) {
            localAppData = System.getProperty("user.home") + File.separator
                    + "AppData" + File.separator + "Local";
        }

        return Paths.get(localAppData);
    }

    /**
     * Gets the RepairKit data directory.
     *
     * @return the RepairKit data directory
     */
    public static @NotNull Path dataDirectory() {
        return localAppData().resolve("RepairKit");
    }

    /**
     * Gets the log directory.
     *
     * @return the log directory
     */
    public static @NotNull Path logDirectory() {
        return dataDirectory().resolve("logs");
    }

    /**
     * Gets the registry backup directory.
     *
     * @return the registry backup directory
     */
    public static @NotNull Path backupDirectory() {
        return dataDirectory().resolve("backups");
    }

    /**
     * Gets the private tool extraction directory.
     *
     * @return the tool extraction directory
     */
    public static @NotNull Path toolsDirectory() {
        return dataDirectory().resolve("tools");
    }

    /**
     * Checks whether Windows is running in Safe Mode.
     *
     * @return {@code true} in Safe Mode
     */
    public static boolean isSafeMode() {
        try {
            return Advapi32Util.registryGetIntValue(WinReg.HKEY_LOCAL_MACHINE,
                    "SYSTEM\\CurrentControlSet\\Control\\SafeBoot\\Option", "OptionValue") == 1;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Gets information about the installed Windows version.
     *
     * @return the operating system information
     */
    public static @NotNull OsInfo osInfo() {
        if (!isWindows()) {
            // Registry access would fail with an UnsatisfiedLinkError off Windows,
            // so report the raw platform name instead.
            return new OsInfo(System.getProperty("os.name", "Unknown"), "", 0,
                    false, false, false, false);
        }

        OsInfo cached = cachedOsInfo;

        if (cached != null) {
            return cached;
        }

        String productName = readRegistryString(NT_CURRENT_VERSION, "ProductName");
        String displayVersion = readRegistryString(NT_CURRENT_VERSION, "DisplayVersion");
        String buildString = readRegistryString(NT_CURRENT_VERSION, "CurrentBuildNumber");
        String installationType = readRegistryString(NT_CURRENT_VERSION, "InstallationType");

        if (productName == null) {
            productName = System.getProperty("os.name", "Windows");
        }

        int build = parseBuild(buildString);
        boolean server = (installationType != null
                && installationType.toLowerCase(Locale.ROOT).contains("server"))
                || productName.toLowerCase(Locale.ROOT).contains("server");

        boolean supported;
        boolean windows11 = false;

        if (build <= 0) {
            // A registry read failure must not block the application; allow the run
            // and log the uncertainty instead.
            supported = true;
        } else if (server) {
            supported = build >= SERVER_2016_BUILD;
        } else {
            supported = build >= WINDOWS_10_BUILD;
            windows11 = build >= WINDOWS_11_BUILD;
        }

        boolean windows10EndOfLife = !server && build >= WINDOWS_10_BUILD && build < WINDOWS_11_BUILD;

        OsInfo info = new OsInfo(productName, displayVersion == null ? "" : displayVersion,
                build, server, windows11, supported, windows10EndOfLife);
        cachedOsInfo = info;
        return info;
    }

    private static @Nullable String readRegistryString(String key, String value) {
        try {
            return Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, key, value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static int parseBuild(@Nullable String buildString) {
        if (buildString == null) {
            return 0;
        }

        try {
            return Integer.parseInt(buildString.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * Checks whether the computer is joined to an Active Directory domain.
     * <p>
     * The user domain differs from the computer name for domain accounts, which
     * is reliable without starting another process.
     *
     * @return {@code true} when the computer appears to be domain joined
     */
    public static boolean isDomainJoined() {
        String userDomain = System.getenv("USERDOMAIN");
        String computerName = System.getenv("COMPUTERNAME");

        if (userDomain == null || computerName == null) {
            return false;
        }

        return !userDomain.isBlank() && !userDomain.equalsIgnoreCase(computerName);
    }

    /**
     * Checks whether the system drive is protected by BitLocker.
     * <p>
     * The check runs PowerShell once and caches the result. It returns
     * {@code null} when BitLocker cannot be queried, for example on Windows Home.
     *
     * @return {@code true} when protection is on, {@code null} when unknown
     */
    public static @Nullable Boolean isSystemDriveBitLockerProtected() {
        if (!isWindows()) {
            return null;
        }

        Boolean cached = bitLockerStatus;

        if (cached != null || bitLockerQueried) {
            return cached;
        }

        synchronized (WindowsUtil.class) {
            if (bitLockerQueried) {
                return bitLockerStatus;
            }

            @NotNull CommandResult result = CommandUtil.runPowerShell(
                    "$v = Get-BitLockerVolume -MountPoint $env:SystemDrive "
                            + "-ErrorAction Stop; Write-Output ([int]$v.ProtectionStatus)",
                    Duration.ofSeconds(30));

            bitLockerQueried = true;

            for (String line : result.output()) {
                try {
                    int status = Integer.parseInt(line.trim());

                    // 1 = On, 0 = Off, 2 = Unknown.
                    bitLockerStatus = status == 1 ? Boolean.TRUE
                            : status == 0 ? Boolean.FALSE : null;
                    return bitLockerStatus;
                } catch (NumberFormatException ignored) {
                    // Keep looking for a numeric line.
                }
            }

            return null;
        }
    }

    /**
     * Opens a URI with the Windows shell.
     * <p>
     * Using ShellExecute avoids {@code cmd /c start} and its quoting rules, and
     * supports both http(s) URLs and {@code ms-settings:} handlers.
     *
     * @param uri the URI to open
     * @return {@code true} when the shell accepted the request
     */
    public static boolean openUri(@NotNull String uri) {
        if (!isWindows()) {
            return false;
        }

        WinDef.INT_PTR result = Shell32.INSTANCE.ShellExecute(null, "open", uri, null, null,
                WinUser.SW_SHOWNORMAL);

        if (result == null || result.intValue() <= 32) {
            long code = result == null ? -1 : result.intValue();
            DebugUtil.warn("ShellExecute failed for " + uri, new IOException("ShellExecute code " + code));
            return false;
        }

        return true;
    }

    /**
     * Opens a directory in File Explorer.
     *
     * @param directory the directory to open
     * @return {@code true} when Explorer was started
     */
    public static boolean openDirectory(@NotNull Path directory) {
        try {
            Files.createDirectories(directory);
            new ProcessBuilder(system32("explorer.exe").toString(), directory.toString()).start();
            return true;
        } catch (IOException ex) {
            DebugUtil.warn("Failed to open directory: " + directory, ex);
            return false;
        }
    }

    /**
     * Information about the installed Windows version.
     *
     * @param productName         the product name reported by Windows
     * @param displayVersion      the release name, for example 24H2
     * @param build               the build number
     * @param server              whether this is a server edition
     * @param windows11           whether this is Windows 11
     * @param supported           whether RepairKit supports this version
     * @param windows10EndOfLife  whether this is a Windows 10 release that no longer receives updates
     * @author Foulest
     */
    public record OsInfo(@NotNull String productName, @NotNull String displayVersion, int build,
                         boolean server, boolean windows11, boolean supported,
                         boolean windows10EndOfLife) {

        /**
         * Gets a human-readable description.
         *
         * @return the description
         */
        public @NotNull String describe() {
            String version = displayVersion.isEmpty() ? "" : " " + displayVersion;
            String buildText = build > 0 ? " (build " + build + ")" : "";
            return productName + version + buildText;
        }
    }
}
