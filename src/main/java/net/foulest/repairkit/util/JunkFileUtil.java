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

import net.foulest.repairkit.util.config.ConfigLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Temporary-file cleanup that stays inside known temporary folders.
 * <p>
 * The previous implementation searched every indexed volume for
 * {@code .tmp}, {@code .old}, and {@code .dmp} files and deleted anything whose
 * last <em>access</em> time was old. Last-access timestamps are disabled on many
 * Windows installations, so recently used files could be deleted. Cleanup is
 * now limited to the two temporary directories, uses last-write time, and never
 * follows links.
 *
 * @author Foulest
 */
public final class JunkFileUtil {

    private JunkFileUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Runs the cleanup described by {@code config/junk_files.json}.
     *
     * @return the cleanup result
     */
    public static @NotNull CleanupResult removeJunkFiles() {
        @NotNull ConfigLoader loader = new ConfigLoader(FileUtil.configFile("junk_files.json"));
        @NotNull Map<String, Object> config = loader.category("junkFiles");

        if (config.isEmpty() || !ConfigLoader.flag(config, "enabled", true)) {
            return new CleanupResult(0, 0, 0, false, "Junk file cleanup is disabled.");
        }

        int olderThanDays = Math.max(1, ConfigLoader.integer(config, "olderThanDays", 7));
        @NotNull Instant cutoff = Instant.now().minus(olderThanDays, ChronoUnit.DAYS);

        @NotNull Set<Path> roots = new LinkedHashSet<>();

        if (ConfigLoader.flag(config, "cleanUserTempFiles", true)) {
            addIfSafe(roots, userTemp());
        }

        if (ConfigLoader.flag(config, "cleanSystemTempFiles", true)) {
            addIfSafe(roots, WindowsUtil.windowsDirectory().resolve("Temp"));
        }

        for (String extra : ConfigLoader.strings(config, "additionalPaths")) {
            addIfSafe(roots, expand(extra));
        }

        @NotNull List<Path> excluded = new ArrayList<>();

        for (String excludedPath : ConfigLoader.strings(config, "excludedPaths")) {
            Path path = expand(excludedPath);

            if (path != null) {
                excluded.add(path.toAbsolutePath().normalize());
            }
        }

        long files = 0;
        long bytes = 0;
        long failures = 0;

        for (Path root : roots) {
            @NotNull CleanupResult result = cleanDirectory(root, cutoff, excluded);
            files += result.filesDeleted();
            bytes += result.bytesFreed();
            failures += result.failures();
        }

        boolean recycleBinEmptied = false;

        if (ConfigLoader.flag(config, "emptyRecycleBin", false)) {
            recycleBinEmptied = emptyRecycleBin();
        }

        @NotNull String message = "Deleted " + files + " file(s), freed "
                + bytes + " byte(s) from " + roots.size() + " temporary folder(s)."
                + (failures > 0 ? " " + failures + " file(s) could not be deleted." : "")
                + (recycleBinEmptied ? " Recycle Bin emptied." : "");

        return new CleanupResult(files, bytes, failures, recycleBinEmptied, message);
    }

    private static @Nullable Path userTemp() {
        String temp = System.getenv("TEMP");

        if (temp == null || temp.isBlank()) {
            temp = System.getenv("TMP");
        }

        if (temp == null || temp.isBlank()) {
            temp = System.getProperty("java.io.tmpdir");
        }

        return temp == null || temp.isBlank() ? null : Path.of(temp);
    }

    private static void addIfSafe(@NotNull Set<Path> roots, @Nullable Path path) {
        if (path == null) {
            return;
        }

        @NotNull Path normalized = path.toAbsolutePath().normalize();

        if (!isSafeCleanupRoot(normalized)) {
            DebugUtil.warn("Refusing to clean an unsafe path: " + normalized, null);
            return;
        }

        if (Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            roots.add(normalized);
        }
    }

    /**
     * Checks whether a directory is a safe cleanup root.
     * <p>
     * Only temporary and cache roots qualify: the final path component must be
     * {@code Temp}, {@code Tmp}, {@code Cache}, or {@code Caches}, except that
     * descendants of the Windows temporary directory are also permitted.
     * Drive roots, the Windows directory (except {@code Windows\Temp}), Program
     * Files, user profile roots, and existing reparse-point ancestors are rejected.
     * Configured roots still require review; this is not a filesystem sandbox.
     *
     * @param path the absolute, normalized path
     * @return {@code true} when it is safe to delete files under it
     */
    public static boolean isSafeCleanupRoot(@NotNull Path path) {
        @NotNull Path absolute = path.toAbsolutePath().normalize();

        // A drive root has no parent and may never be a cleanup root.
        if (absolute.getParent() == null) {
            return false;
        }

        // Reject protected ancestors before looking for a Temp/Cache component.
        // A profile called "Temp" must never qualify as a cleanup directory.
        for (Path current = absolute; current != null; current = current.getParent()) {
            Path name = current.getFileName();
            if (name != null && (name.toString().equalsIgnoreCase("Program Files")
                    || name.toString().equalsIgnoreCase("Program Files (x86)"))) {
                return false;
            }
            Path parent = current.getParent();
            if (parent != null && parent.getFileName() != null
                    && parent.getFileName().toString().equalsIgnoreCase("Users")
                    && current.equals(absolute)) {
                return false;
            }
            try {
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    BasicFileAttributes attrs = Files.readAttributes(current,
                            BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attrs.isSymbolicLink() || attrs.isOther()) {
                        return false;
                    }
                }
            } catch (IOException | SecurityException ex) {
                return false;
            }
        }
        Path profile = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (absolute.equals(profile) || profile.startsWith(absolute)) {
            return false;
        }
        for (String variable : List.of("ProgramFiles", "ProgramFiles(x86)", "ProgramW6432")) {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank()
                    && absolute.startsWith(Path.of(value).toAbsolutePath().normalize())) {
                return false;
            }
        }

        @NotNull Path windows = WindowsUtil.windowsDirectory().toAbsolutePath().normalize();

        if (absolute.equals(windows)) {
            return false;
        }

        if (absolute.startsWith(windows)) {
            // Inside the Windows directory only the Temp folder is acceptable.
            return absolute.startsWith(windows.resolve("Temp"));
        }

        // Only an actual temporary/cache root qualifies; finding a matching
        // component above Documents or another unrelated directory is unsafe.
        Path element = absolute.getFileName();
        if (element != null) {
            @NotNull String name = element.toString().toLowerCase(Locale.ROOT);

            if (name.equals("temp") || name.equals("tmp") || name.equals("cache")
                    || name.equals("caches") || name.equals("temporary files")) {
                return true;
            }
        }

        return false;
    }

    private static @Nullable Path expand(@NotNull String value) {
        @NotNull String expanded = value
                .replace("%TEMP%", System.getenv().getOrDefault("TEMP", ""))
                .replace("%TMP%", System.getenv().getOrDefault("TMP", ""))
                .replace("%USERPROFILE%", System.getenv().getOrDefault("USERPROFILE", ""))
                .replace("%LOCALAPPDATA%", System.getenv().getOrDefault("LOCALAPPDATA", ""))
                .replace("%windir%", WindowsUtil.windowsDirectory().toString());

        try {
            return Path.of(expanded);
        } catch (RuntimeException ex) {
            DebugUtil.warn("Invalid cleanup path: " + value, ex);
            return null;
        }
    }

    private static @NotNull CleanupResult cleanDirectory(@NotNull Path root, @NotNull Instant cutoff,
                                                         @NotNull List<Path> excluded) {
        long[] counters = new long[3];

        // Recheck immediately before traversal, including every existing ancestor.
        if (!isSafeCleanupRoot(root)) {
            return new CleanupResult(0, 0, 1, false, "Unsafe cleanup root refused.");
        }

        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 32,
                    new SimpleFileVisitor<>() {
                        @Override
                        public @NotNull FileVisitResult preVisitDirectory(@NotNull Path directory,
                                                                          @NotNull BasicFileAttributes attrs) {
                            if (attrs.isSymbolicLink() || attrs.isOther()) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }

                            @NotNull Path normalized = directory.toAbsolutePath().normalize();

                            for (Path excludedPath : excluded) {
                                if (normalized.startsWith(excludedPath)) {
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                            }

                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public @NotNull FileVisitResult visitFile(@NotNull Path file,
                                                                  @NotNull BasicFileAttributes attrs) {
                            if (!attrs.isRegularFile()) {
                                return FileVisitResult.CONTINUE;
                            }

                            if (attrs.lastModifiedTime().toInstant().isAfter(cutoff)) {
                                return FileVisitResult.CONTINUE;
                            }

                            try {
                                long size = attrs.size();

                                if (Files.deleteIfExists(file)) {
                                    counters[0]++;
                                    counters[1] += size;
                                }
                            } catch (IOException ex) {
                                counters[2]++;

                                if (counters[2] <= 5) {
                                    DebugUtil.warn("Failed to delete junk file: " + file, ex);
                                }
                            }

                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public @NotNull FileVisitResult visitFileFailed(@NotNull Path file,
                                                                        @NotNull IOException ex) {
                            counters[2]++;
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException ex) {
            DebugUtil.warn("Failed to walk the temporary directory: " + root, ex);
        }

        if (counters[0] > 0) {
            DebugUtil.debug("Cleaned " + counters[0] + " file(s) from " + root);
        }

        return new CleanupResult(counters[0], counters[1], counters[2], false, "");
    }

    private static boolean emptyRecycleBin() {
        @NotNull CommandResult result = CommandUtil.runPowerShell(
                "Clear-RecycleBin -Force -ErrorAction Stop", Duration.ofMinutes(2));

        if (!result.succeeded()) {
            DebugUtil.warn("Failed to empty the Recycle Bin", null);
        }

        return result.succeeded();
    }

    /**
     * The outcome of a cleanup.
     *
     * @param filesDeleted     number of files deleted
     * @param bytesFreed       number of bytes freed
     * @param failures         number of files that could not be deleted
     * @param recycleBinEmptied whether the Recycle Bin was emptied
     * @param message          diagnostic summary
     * @author Foulest
     */
    public record CleanupResult(long filesDeleted, long bytesFreed, long failures,
                                boolean recycleBinEmptied, @NotNull String message) {
    }
}
