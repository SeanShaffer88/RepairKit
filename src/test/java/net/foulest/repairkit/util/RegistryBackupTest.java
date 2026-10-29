package net.foulest.repairkit.util;

import com.sun.jna.platform.win32.WinReg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RegistryBackupTest {
    @TempDir Path directory;

    @Test
    void failedValueFlushPreventsTheFollowingMutation() {
        Writer failing = new StringWriter() {
            @Override public void flush() { throw new IllegalStateException("disk unavailable"); }
        };
        RegistryBackup backup = backup(new BufferedWriter(failing), true);
        AtomicBoolean mutated = new AtomicBoolean();
        assertThrows(IllegalStateException.class, () -> {
            backup.recordValueSnapshot("HKEY_CURRENT_USER\\Software\\RepairKitTest", "Value", Map.of("Value", 7));
            mutated.set(true);
        });
        assertFalse(mutated.get());
        assertTrue(backup.isEmpty());
    }

    @Test
    void failedBackupWriteIsPropagated() {
        Writer failing = new Writer() {
            @Override public void write(char[] data, int offset, int count) throws IOException {
                throw new IOException("disk full");
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        RegistryBackup backup = backup(new BufferedWriter(failing), true);
        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                backup.recordValueSnapshot("HKEY_CURRENT_USER\\Software\\RepairKitTest", "Value", Map.of("Value", 7)));
        assertInstanceOf(IOException.class, error.getCause());
        assertTrue(backup.isEmpty());
    }

    @Test
    void failedKeyExportPreventsTheFollowingDeletion() {
        RegistryBackup backup = backup(new BufferedWriter(new StringWriter()), false);
        AtomicBoolean deleted = new AtomicBoolean();
        assertThrows(IllegalStateException.class, () -> {
            backup.recordKeyDeletion(WinReg.HKEY_CURRENT_USER, "Software\\RepairKitTest");
            deleted.set(true);
        });
        assertFalse(deleted.get());
        assertTrue(backup.isEmpty());
    }

    @Test
    void missingBackupCannotAuthorizeMutation() {
        assertThrows(IllegalStateException.class, () -> RegistryBackup.requireAvailable(null));
    }

    @Test
    void unsupportedOldValueRefusesMutationInsteadOfWritingAnIncompleteBackup() {
        RegistryBackup backup = backup(new BufferedWriter(new StringWriter()), true);
        assertThrows(IllegalStateException.class, () -> backup.recordValueSnapshot(
                "HKEY_CURRENT_USER\\Software\\RepairKitTest", "Value", Map.of("Value", new byte[]{1, 2})));
        assertTrue(backup.isEmpty());
    }

    @Test
    void successfulBackupContainsRestorableValueAndDeletionMarker() {
        StringWriter contents = new StringWriter();
        RegistryBackup backup = backup(new BufferedWriter(contents), true);
        backup.recordValueSnapshot("HKEY_CURRENT_USER\\Software\\RepairKitTest", "Value", Map.of("Value", 7));
        backup.recordValueSnapshot("HKEY_CURRENT_USER\\Software\\RepairKitTest", "Absent", Map.of());
        assertFalse(backup.isEmpty());
        assertTrue(contents.toString().contains("\"Value\"=dword:00000007"));
        assertTrue(contents.toString().contains("\"Absent\"=-"));
    }

    private RegistryBackup backup(BufferedWriter writer, boolean exportSucceeds) {
        return new RegistryBackup(directory, directory.resolve("backup.reg"), directory.resolve("manifest.txt"),
                writer, (qualified, target) -> exportSucceeds);
    }
}
