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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.foulest.repairkit.Messages;
import net.foulest.repairkit.RepairKit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

/**
 * Update checking.
 * <p>
 * Release tags are compared as semantic versions, not strings. The old
 * implementation used {@code tag_name.equals(version)}, which offered a
 * "downgrade" whenever the repository was ahead of the running build and
 * ignored every tag with a {@code v} prefix.
 *
 * @author Foulest
 */
public final class UpdateUtil {

    private static final String RELEASES_API =
            "https://api.github.com/repos/Foulest/RepairKit/releases/latest";
    private static final String RELEASES_PAGE =
            "https://github.com/Foulest/RepairKit/releases/latest";
    private static final String SKIPPED_VERSION_KEY = "skippedVersion";

    private UpdateUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Checks for a newer release and offers it to the user.
     * <p>
     * Safe to call from the event dispatch thread; the network request runs on
     * a background thread and the dialog is shown on the event dispatch thread.
     */
    public static void checkForUpdates() {
        @Nullable Version current = currentVersion();

        if (current == null) {
            return;
        }

        CompletableFuture
                .supplyAsync(UpdateUtil::fetchLatestRelease)
                .thenAccept(release -> {
                    if (release == null || release.version() == null) {
                        return;
                    }

                    if (release.prerelease() || !release.version().isNewerThan(current)) {
                        return;
                    }

                    @NotNull String skipped = skippedVersion();

                    if (release.version().toString().equals(skipped)) {
                        DebugUtil.debug("Update " + release.version() + " was skipped by the user.");
                        return;
                    }

                    SwingUtilities.invokeLater(() -> showUpdateDialog(release));
                })
                .exceptionally(ex -> {
                    DebugUtil.warn("Update check failed", ex);
                    return null;
                });
    }

    private static void showUpdateDialog(@NotNull Release release) {
        SoundUtil.playSound(ConstantUtil.EXCLAMATION_SOUND);

        Object[] options = {
                Messages.get("common.yes"),
                Messages.get("update.skip"),
                Messages.get("common.no")
        };

        int choice = JOptionPane.showOptionDialog(null,
                Messages.get("update.available.message", release.version(), currentVersion()),
                Messages.get("update.available.title"),
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);

        if (choice == 0) {
            WindowsUtil.openUri(RELEASES_PAGE);
        } else if (choice == 1) {
            saveSkippedVersion(release.version().toString());
        }
    }

    /**
     * Fetches the latest release metadata.
     *
     * @return the release, or {@code null} when it cannot be read
     */
    private static @Nullable Release fetchLatestRelease() {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) URI.create(RELEASES_API).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "RepairKit/" + versionString());

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                DebugUtil.warn("Update check returned HTTP " + connection.getResponseCode(), null);
                return null;
            }

            @NotNull StringBuilder body = new StringBuilder();

            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;

                while ((line = reader.readLine()) != null) {
                    body.append(line);

                    if (body.length() > 256 * 1024) {
                        return null;
                    }
                }
            }

            JsonObject json = JsonParser.parseString(body.toString()).getAsJsonObject();
            String tag = json.has("tag_name") && !json.get("tag_name").isJsonNull()
                    ? json.get("tag_name").getAsString()
                    : null;
            boolean prerelease = json.has("prerelease") && json.get("prerelease").getAsBoolean();

            return new Release(Version.parse(tag), prerelease);
        } catch (IOException | RuntimeException ex) {
            DebugUtil.warn("Failed to read the latest release", ex);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Gets the version of this build.
     *
     * @return the version, or {@code null} when the version resource is missing
     */
    public static @Nullable Version currentVersion() {
        return Version.parse(versionString());
    }

    /**
     * Gets the version string of this build.
     *
     * @return the version string, or {@code unknown}
     */
    public static @NotNull String versionString() {
        @NotNull Properties properties = new Properties();

        try (InputStream input = RepairKit.class.getResourceAsStream("/version.properties")) {
            if (input == null) {
                DebugUtil.warn("version.properties is missing from the build", null);
                return "unknown";
            }

            properties.load(input);
            return properties.getProperty("version", "unknown");
        } catch (IOException ex) {
            DebugUtil.warn("Failed to read version.properties", ex);
            return "unknown";
        }
    }

    private static @NotNull String skippedVersion() {
        try {
            return Preferences.userNodeForPackage(UpdateUtil.class)
                    .get(SKIPPED_VERSION_KEY, "");
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private static void saveSkippedVersion(@NotNull String version) {
        try {
            Preferences.userNodeForPackage(UpdateUtil.class).put(SKIPPED_VERSION_KEY, version);
        } catch (RuntimeException ex) {
            DebugUtil.warn("Failed to remember the skipped version", ex);
        }
    }

    private record Release(@Nullable Version version, boolean prerelease) {
    }
}
