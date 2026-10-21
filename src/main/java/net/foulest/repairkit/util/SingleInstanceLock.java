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
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Prevents two copies of RepairKit from sharing one log and tool directory.
 * <p>
 * The lock is held for the lifetime of the process and released by the
 * operating system even after a crash.
 *
 * @author Foulest
 */
public final class SingleInstanceLock implements AutoCloseable {

    private final @Nullable FileChannel channel;
    private final @Nullable FileLock lock;
    private boolean closed;

    private SingleInstanceLock(@Nullable FileChannel channel, @Nullable FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Tries to acquire the single-instance lock.
     *
     * @return the lock, or {@code null} when another instance holds it
     */
    public static @Nullable SingleInstanceLock acquire() {
        @NotNull Path file = WindowsUtil.dataDirectory().resolve("repairkit.lock");

        try {
            Files.createDirectories(file.getParent());
            @NotNull FileChannel channel = FileChannel.open(file,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();

            if (lock == null) {
                channel.close();
                return null;
            }

            return new SingleInstanceLock(channel, lock);
        } catch (OverlappingFileLockException ex) {
            return null;
        } catch (IOException ex) {
            DebugUtil.warn("Failed to create the single-instance lock", ex);
            // A lock failure must not prevent the application from starting, and
            // must not be mistaken for a second instance.
            return new SingleInstanceLock(null, null);
        }
    }

    /**
     * Releases the lock.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;

        if (lock != null) {
            try {
                lock.release();
            } catch (IOException ex) {
                DebugUtil.warn("Failed to release the single-instance lock", ex);
            }
        }

        if (channel != null) {
            try {
                channel.close();
            } catch (IOException ex) {
                DebugUtil.warn("Failed to close the single-instance lock channel", ex);
            }
        }
    }
}
