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
package net.foulest.repairkit;

import net.foulest.repairkit.util.DebugUtil;
import org.jetbrains.annotations.NotNull;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Centralized user-facing text.
 * <p>
 * All text is loaded from {@code messages.properties}. Translators can add
 * {@code messages_<locale>.properties} files without touching code. Missing
 * keys fall back to the key itself so a half-finished translation never
 * prevents the application from starting.
 *
 * @author Foulest
 */
public final class Messages {

    private static final ResourceBundle BUNDLE = loadBundle();

    private Messages() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static @NotNull ResourceBundle loadBundle() {
        try {
            return ResourceBundle.getBundle("messages", Locale.getDefault());
        } catch (MissingResourceException ex) {
            DebugUtil.warn("Failed to load message bundle", ex);
            return ResourceBundle.getBundle("messages", Locale.ROOT);
        }
    }

    /**
     * Gets a localized message.
     *
     * @param key  message key
     * @param args optional {@link MessageFormat} arguments
     * @return the formatted message, or the key when it is missing
     */
    public static @NotNull String get(@NotNull String key, Object... args) {
        String pattern;

        try {
            pattern = BUNDLE.getString(key);
        } catch (MissingResourceException ex) {
            return key;
        }

        return args.length == 0 ? pattern : MessageFormat.format(pattern, args);
    }
}
