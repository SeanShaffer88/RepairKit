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

import org.jetbrains.annotations.NotNull;

import java.awt.Toolkit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plays the standard Windows notification sounds.
 *
 * @author Foulest
 */
public final class SoundUtil {

    private static final ExecutorService SOUND_PLAYER =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "repairkit-sound");
                thread.setDaemon(true);
                return thread;
            });

    private SoundUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Plays a sound by desktop property name.
     *
     * @param soundName the desktop property, for example {@code win.sound.hand}
     */
    public static void playSound(@NotNull String soundName) {
        try {
            Object property = Toolkit.getDefaultToolkit().getDesktopProperty(soundName);

            if (property instanceof Runnable runnable) {
                SOUND_PLAYER.execute(runnable);
            }
        } catch (RuntimeException ex) {
            DebugUtil.warn("Failed to play sound: " + soundName, ex);
        }
    }
}
