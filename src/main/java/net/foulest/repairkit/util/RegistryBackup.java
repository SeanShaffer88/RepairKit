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

import com.sun.jna.platform.win32.WinReg;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import com.sun.jna.platform.win32.Advapi32Util;

/**
 * Writes a {@code .reg} restore file for the registry values that a repair
 * changes.
 * <p>
 * The file is written incrementally and flushed after every value, so a crash
 * or a killed process still leaves a usable backup of everything that was
 * changed before the crash. Values are restored by double-clicking the file;
 * absent values are recorded as deletions so that importing the backup removes
 * values that RepairKit created.
 *
 * @author Foulest
 */
public final class RegistryBackup implements AutoCloseable {

    private static final String HEADER = "Windows Registry Editor Version 5.00";

    // regedit expects "version 5.00" files to be UTF-16LE with a byte-order mark.
    private static final String BYTE_ORDER_MARK = "\uFEFF";

    private final Path directory;
    private final Path regFile;
    private final Path manifestFile;
    private final BufferedWriter writer;
    private final BiPredicate<String, Path> keyExporter;
    private final StringBuilder manifest = new StringBuilder();
    private final AtomicInteger records = new AtomicInteger();
    private final AtomicInteger exports = new AtomicInteger();

    private RegistryBackup(Path directory, Path regFile, Path manifestFile,
                           BufferedWriter writer) {
        this(directory, regFile, manifestFile, writer, (qualified, target) ->
                CommandUtil.run(WindowsUtil.system32("reg.exe"), Duration.ofMinutes(1),
                        "export", qualified, target.toString(), "/y").succeeded()
                        && Files.isRegularFile(target));
    }

    RegistryBackup(Path directory, Path regFile, Path manifestFile,
                   BufferedWriter writer, BiPredicate<String, Path> keyExporter) {
        this.directory = directory;
        this.regFile = regFile;
        this.manifestFile = manifestFile;
        this.writer = writer;
        this.keyExporter = keyExporter;
    }

    /** Refuses registry mutation when no backup could be created. */
    public static @NotNull RegistryBackup requireAvailable(@Nullable RegistryBackup backup) {
        if (backup == null) {
            throw new IllegalStateException("Registry changes require a writable backup.");
        }
        return backup;
    }

    /**
     * Creates a new backup.
     *
     * @param label a short label used in the folder name
     * @return the backup
     * @throws IOException when the backup cannot be created
     */
    public static @NotNull RegistryBackup create(@NotNull String label) throws IOException {
        @NotNull String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .format(LocalDateTime.now());
        @NotNull Path directory = WindowsUtil.backupDirectory().resolve(stamp + "-" + label);
        Files.createDirectories(directory);

        @NotNull Path regFile = directory.resolve("registry-backup.reg");
        @NotNull BufferedWriter writer = Files.newBufferedWriter(regFile, StandardCharsets.UTF_16LE);
        writer.write(BYTE_ORDER_MARK);
        writer.write(HEADER);
        writer.write(System.lineSeparator());
        writer.write(System.lineSeparator());
        writer.flush();

        @NotNull RegistryBackup backup = new RegistryBackup(directory, regFile,
                directory.resolve("manifest.txt"), writer);
        backup.manifest.append("RepairKit registry backup").append(System.lineSeparator());
        backup.manifest.append("Created: ").append(LocalDateTime.now()).append(System.lineSeparator());
        backup.manifest.append(System.lineSeparator());
        return backup;
    }

    /**
     * Records the current value of a registry value before it is changed.
     *
     * @param hive      the hive
     * @param keyPath   the key path
     * @param valueName the value name, empty for the default value
     */
    public synchronized void recordValue(WinReg.HKEY hive, @NotNull String keyPath,
                                         @NotNull String valueName) {
        // A failed read is not evidence that a value was absent. Propagate it
        // rather than writing a deletion marker that would lose the old value.
        @NotNull Map<String, Object> values = Advapi32Util.registryKeyExists(hive, keyPath)
                ? Advapi32Util.registryGetValues(hive, keyPath) : Map.of();
        recordValueSnapshot(RegistryUtil.qualifiedPath(hive, keyPath), valueName, values);
    }

    synchronized void recordValueSnapshot(@NotNull String qualified, @NotNull String valueName,
                                          @NotNull Map<String, Object> values) {
        boolean exists = values.containsKey(valueName);
        Object oldValue = values.get(valueName);
        String formatted = formatValue(valueName, exists, oldValue);

        try {
            writer.write("[" + qualified + "]");
            writer.write(System.lineSeparator());
            writer.write(formatted);
            writer.write(System.lineSeparator());
            writer.write(System.lineSeparator());
            writer.flush();
            records.incrementAndGet();
            manifest.append("VALUE ").append(qualified);
            if (!valueName.isEmpty()) {
                manifest.append("\\").append(valueName);
            }
            manifest.append(exists ? " (had a value)" : " (did not exist)");
            manifest.append(System.lineSeparator());
        } catch (IOException ex) {
            DebugUtil.warn("Failed to write the registry backup", ex);
            throw new IllegalStateException("Registry backup could not be persisted; change refused.", ex);
        }
    }

    private static @NotNull String formatValue(@NotNull String valueName, boolean exists,
                                               @Nullable Object oldValue) {
        @NotNull String name = valueName.isEmpty() ? "@" : "\"" + escape(valueName) + "\"";

        if (!exists) {
            return name + "=-";
        }

        if (oldValue instanceof Integer number) {
            return name + "=dword:" + String.format("%08x", number);
        }

        if (oldValue instanceof String text) {
            return name + "=\"" + escape(text) + "\"";
        }

        if (oldValue instanceof String[] strings) {
            @NotNull StringBuilder builder = new StringBuilder(name).append("=hex(7):");
            @NotNull String joined = String.join("\u0000", strings) + "\u0000\u0000";

            for (byte value : joined.getBytes(StandardCharsets.UTF_16LE)) {
                builder.append(String.format("%02x,", value));
            }

            builder.setLength(builder.length() - 1);
            return builder.toString();
        }

        throw new IllegalStateException("Registry value type cannot be backed up; change refused.");
    }

    private static @NotNull String escape(@NotNull String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Exports a complete key before it is deleted.
     *
     * @param hive    the hive
     * @param keyPath the key path
     */
    public synchronized void recordKeyDeletion(WinReg.HKEY hive, @NotNull String keyPath) {
        @NotNull String qualified = RegistryUtil.qualifiedPath(hive, keyPath);
        int index = exports.incrementAndGet();
        @NotNull Path target = directory.resolve("deleted-key-" + index + ".reg");

        if (keyExporter.test(qualified, target)) {
            manifest.append("KEY ").append(qualified).append(" exported to ")
                    .append(target.getFileName()).append(System.lineSeparator());
        } else {
            exports.decrementAndGet();
            manifest.append("KEY ").append(qualified)
                    .append(" could not be exported before deletion")
                    .append(System.lineSeparator());
            DebugUtil.warn("Failed to export registry key before deletion: " + qualified, null);
            throw new IllegalStateException("Registry key backup failed; deletion refused: " + qualified);
        }
    }

    /**
     * Checks whether nothing was recorded.
     *
     * @return {@code true} when no value or key was backed up
     */
    public boolean isEmpty() {
        return records.get() == 0 && exports.get() == 0;
    }

    /**
     * Gets the backup directory.
     *
     * @return the backup directory
     */
    public @NotNull Path directory() {
        return directory;
    }

    /**
     * Gets the {@code .reg} restore file.
     *
     * @return the restore file
     */
    public @NotNull Path regFile() {
        return regFile;
    }

    /**
     * Closes the backup and writes the manifest.
     */
    @Override
    public synchronized void close() {
        try {
            writer.close();
        } catch (IOException ex) {
            DebugUtil.warn("Failed to close the registry backup", ex);
        }

        try {
            Files.writeString(manifestFile, manifest.toString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            DebugUtil.warn("Failed to write the registry backup manifest", ex);
        }

        if (isEmpty()) {
            try {
                FileUtil.deleteRecursively(directory);
            } catch (IOException ex) {
                DebugUtil.warn("Failed to remove an empty backup directory", ex);
            }
        } else {
            DebugUtil.debug("Registry backup written to " + regFile);
        }
    }
}
