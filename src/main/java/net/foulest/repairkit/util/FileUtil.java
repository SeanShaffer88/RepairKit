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

import net.foulest.repairkit.RepairKit;
import net.foulest.repairkit.util.config.ConfigMigrator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.stream.Stream;

/**
 * File, resource, and configuration path operations.
 * <p>
 * Configuration files live next to the executable when that folder is
 * writable (the portable layout) and fall back to
 * {@code %LOCALAPPDATA%\RepairKit\config} when RepairKit is installed in a
 * read-only location.
 *
 * @author Foulest
 */
public final class FileUtil {

    private FileUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Gets the directory that holds configuration files.
     *
     * @return the configuration directory
     */
    public static @NotNull Path configDirectory() {
        for (Path candidate : List.of(applicationDirectory().resolve("config"),
                Path.of(System.getProperty("user.dir"), "config"))) {
            if (Files.isDirectory(candidate) && Files.isWritable(candidate)) {
                return candidate;
            }
        }

        @NotNull Path fallback = WindowsUtil.dataDirectory().resolve("config");

        try {
            Files.createDirectories(fallback);
        } catch (IOException ex) {
            DebugUtil.warn("Failed to create the configuration directory: " + fallback, ex);
        }

        return fallback;
    }

    /**
     * Gets the folder that contains the running executable or classes.
     * <p>
     * The user's current working directory is not reliable: a shortcut can set
     * any "Start in" folder, and launching from a shell leaves it at the shell's
     * location. The code source points at the launcher (or the class directory
     * during development), which is where the shipped {@code config} folder
     * lives.
     *
     * @return the application directory
     */
    public static @NotNull Path applicationDirectory() {
        try {
            @NotNull Path codeSource = Path.of(RepairKit.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());

            if (Files.isRegularFile(codeSource)) {
                Path parent = codeSource.getParent();
                return parent == null ? Path.of(System.getProperty("user.dir")) : parent;
            }

            if (Files.isDirectory(codeSource)) {
                return codeSource;
            }
        } catch (RuntimeException | java.net.URISyntaxException ex) {
            DebugUtil.debug("Could not resolve the application directory: " + ex.getMessage());
        }

        return Path.of(System.getProperty("user.dir"));
    }

    /**
     * Resolves a configuration file, copying the bundled default when the file
     * does not exist yet.
     *
     * @param fileName the configuration file name
     * @return the configuration file path, which may not exist when the bundled
     * resource is missing
     */
    public static @NotNull Path configFile(@NotNull String fileName) {
        @NotNull Path file = configDirectory().resolve(fileName);

        if (Files.isRegularFile(file)) {
            ConfigMigrator.migrate(file, "config/" + fileName);
            return file;
        }

        try (InputStream input = RepairKit.class.getClassLoader()
                .getResourceAsStream("config/" + fileName)) {
            if (input == null) {
                DebugUtil.warn("Missing bundled configuration file: " + fileName, null);
                return file;
            }

            copyAtomically(input, file);
        } catch (IOException ex) {
            DebugUtil.warn("Failed to create configuration file: " + file, ex);
        }

        return file;
    }

    /**
     * Copies a bundled resource to a file atomically.
     * <p>
     * Writing to a sibling temporary file and moving it into place avoids the
     * window where a crash leaves a half-written executable behind.
     *
     * @param resource the classpath resource
     * @param target   the destination file
     * @return the written file
     * @throws IOException when the resource is missing or cannot be written
     */
    public static @NotNull Path copyResource(@NotNull String resource, @NotNull Path target)
            throws IOException {
        try (InputStream input = RepairKit.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing resource: " + resource);
            }

            copyAtomically(input, target);
        }

        return target;
    }

    private static void copyAtomically(@NotNull InputStream input, @NotNull Path target)
            throws IOException {
        @NotNull Path parent = target.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        @NotNull Path temporary = Files.createTempFile(parent, target.getFileName().toString(), ".tmp");

        try (OutputStream output = Files.newOutputStream(temporary)) {
            input.transferTo(output);
        }

        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            // Some file systems do not support atomic moves; fall back to a
            // normal replace so the operation still succeeds.
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Creates a unique directory for one tool launch.
     * <p>
     * The random name is the security boundary: another process cannot predict
     * the path and plant an executable that RepairKit would later run elevated.
     *
     * @param toolId the tool identifier, used as a readable prefix
     * @return the new directory
     * @throws IOException when the directory cannot be created
     */
    public static @NotNull Path createToolDirectory(@NotNull String toolId) throws IOException {
        @NotNull Path tools = WindowsUtil.toolsDirectory();
        Files.createDirectories(tools);
        return Files.createTempDirectory(tools, sanitize(toolId) + "-");
    }

    /**
     * Deletes tool directories left over from earlier runs.
     */
    public static void cleanupToolDirectories() {
        @NotNull Path tools = WindowsUtil.toolsDirectory();

        if (!Files.isDirectory(tools)) {
            return;
        }

        try (Stream<Path> children = Files.list(tools)) {
            children.filter(Files::isDirectory)
                    .filter(FileUtil::isOlderThanOneDay)
                    .forEach(FileUtil::deleteQuietly);
        } catch (IOException ex) {
            DebugUtil.warn("Failed to enumerate the tool directory", ex);
        }
    }

    private static boolean isOlderThanOneDay(@NotNull Path path) {
        try {
            Instant modified = Files.getLastModifiedTime(path).toInstant();
            return modified.isBefore(Instant.now().minus(Duration.ofDays(1)));
        } catch (IOException ex) {
            return false;
        }
    }

    private static void deleteQuietly(@NotNull Path path) {
        try {
            deleteRecursively(path);
        } catch (IOException ex) {
            DebugUtil.warn("Failed to delete: " + path, ex);
        }
    }

    /**
     * Deletes a file or directory tree without following links.
     * <p>
     * Repair points and junctions are deleted as links, never traversed, so a
     * malicious link cannot redirect deletion into another directory.
     *
     * @param root the root to delete
     * @throws IOException when a file cannot be deleted
     */
    public static void deleteRecursively(@NotNull Path root) throws IOException {
        if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 64,
                new SimpleFileVisitor<>() {
                    @Override
                    public @NotNull FileVisitResult preVisitDirectory(@NotNull Path directory,
                                                                      @NotNull BasicFileAttributes attrs) {
                        if (attrs.isSymbolicLink() || attrs.isOther()) {
                            try {
                                Files.deleteIfExists(directory);
                            } catch (IOException ex) {
                                DebugUtil.warn("Failed to delete link: " + directory, ex);
                            }
                            return FileVisitResult.SKIP_SUBTREE;
                        }

                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public @NotNull FileVisitResult visitFile(@NotNull Path file,
                                                              @NotNull BasicFileAttributes attrs)
                            throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public @NotNull FileVisitResult postVisitDirectory(@NotNull Path directory,
                                                                       @Nullable IOException ex)
                            throws IOException {
                        if (ex != null) {
                            throw ex;
                        }

                        Files.deleteIfExists(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    /**
     * Computes the SHA-256 digest of a file.
     *
     * @param file the file
     * @return the lowercase hexadecimal digest
     * @throws IOException when the file cannot be read
     */
    public static @NotNull String sha256(@NotNull Path file) throws IOException {
        try {
            @NotNull MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;

                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /**
     * Loads an icon from the classpath.
     *
     * @param path the resource path
     * @return the icon, or an empty icon when the resource is missing
     */
    public static @NotNull ImageIcon getImageIcon(@NotNull String path) {
        try (InputStream input = RepairKit.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                DebugUtil.warn("Missing icon resource: " + path, null);
                return new ImageIcon(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB));
            }

            BufferedImage image = ImageIO.read(input);

            if (image == null) {
                DebugUtil.warn("Unreadable icon resource: " + path, null);
                return new ImageIcon(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB));
            }

            return new ImageIcon(image);
        } catch (IOException ex) {
            DebugUtil.warn("Failed to load icon: " + path, ex);
            return new ImageIcon(new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB));
        }
    }

    /**
     * Gets the newest log file.
     *
     * @return the newest log file, or {@code null} when there is none
     */
    public static @Nullable Path newestLogFile() {
        @NotNull Path directory = WindowsUtil.logDirectory();

        if (!Files.isDirectory(directory)) {
            return null;
        }

        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .max(Comparator.comparing(path -> path.toFile().lastModified()))
                    .orElse(null);
        } catch (IOException ex) {
            return null;
        }
    }

    /**
     * Opens a file with its associated application.
     *
     * @param file the file or directory
     * @return {@code true} when the shell accepted the request
     */
    public static boolean open(@NotNull Path file) {
        if (!Files.exists(file)) {
            return false;
        }

        if (Files.isDirectory(file)) {
            return WindowsUtil.openDirectory(file);
        }

        return WindowsUtil.openUri(file.toAbsolutePath().toString());
    }

    private static @NotNull String sanitize(@NotNull String value) {
        @NotNull String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isEmpty() ? "tool" : sanitized;
    }
}
