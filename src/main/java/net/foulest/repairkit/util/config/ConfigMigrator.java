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
package net.foulest.repairkit.util.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.foulest.repairkit.util.DebugUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Brings an older configuration file up to date without discarding user edits.
 * <p>
 * Only whole top-level categories are added: if a new release introduces
 * {@code advancedTweaks}, an existing {@code system_tweaks.json} receives that
 * category with its defaults. Values inside categories the user already has are
 * never added back, because a deliberately deleted tweak must stay deleted.
 * The previous file is kept as {@code <name>.bak}.
 *
 * @author Foulest
 */
public final class ConfigMigrator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigMigrator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Adds categories that exist in the packaged default but not in the user
     * file.
     *
     * @param userFile the user's configuration file
     * @param resource the packaged default resource, for example {@code config/system_tweaks.json}
     * @return the categories that were added
     */
    public static @NotNull List<String> migrate(@NotNull Path userFile, @NotNull String resource) {
        @NotNull List<String> added = new ArrayList<>();

        if (!Files.isRegularFile(userFile)) {
            return added;
        }

        JsonObject user;
        JsonObject defaults;

        try (Reader reader = Files.newBufferedReader(userFile, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);

            if (!element.isJsonObject()) {
                return added;
            }

            user = element.getAsJsonObject();
        } catch (IOException | RuntimeException ex) {
            // A malformed file is reported by the loader; do not overwrite it.
            return added;
        }

        try (InputStream input = ConfigMigrator.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (input == null) {
                return added;
            }

            JsonElement element = JsonParser.parseReader(
                    new java.io.InputStreamReader(input, StandardCharsets.UTF_8));

            if (!element.isJsonObject()) {
                return added;
            }

            defaults = element.getAsJsonObject();
        } catch (IOException | RuntimeException ex) {
            DebugUtil.warn("Failed to read the packaged default for " + resource, ex);
            return added;
        }

        for (String category : defaults.keySet()) {
            if (!user.has(category)) {
                user.add(category, defaults.get(category).deepCopy());
                added.add(category);
            }
        }

        if (added.isEmpty()) {
            return added;
        }

        try {
            Files.copy(userFile, userFile.resolveSibling(userFile.getFileName() + ".bak"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(userFile, GSON.toJson(user), StandardCharsets.UTF_8);
            DebugUtil.debug("Updated " + userFile.getFileName() + " with new categories: "
                    + String.join(", ", added));
        } catch (IOException ex) {
            DebugUtil.warn("Failed to migrate " + userFile, ex);
            added.clear();
        }

        return added;
    }
}
