package org.dostalgia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigPatcherTest {

    private final ConfigPatcher patcher = new ConfigPatcher();

    @Test
    void fixAbsolutePaths_rewritesStalePrefix(@TempDir Path dir) throws Exception {
        // Create a file that references C:\FALLOUT1\MASTER.DAT
        // but after flattening, MASTER.DAT is at the root
        String content = "master_dat=C:\\FALLOUT1\\MASTER.DAT\ncritter_dat=C:\\FALLOUT1\\CRITTER.DAT\n";
        Files.writeString(dir.resolve("FALLOUT.CFG"), content, StandardCharsets.ISO_8859_1);

        // The referenced files exist at root
        Files.writeString(dir.resolve("MASTER.DAT"), "data");
        Files.writeString(dir.resolve("CRITTER.DAT"), "data");

        patcher.fixAbsolutePaths(dir);

        String fixed = Files.readString(dir.resolve("FALLOUT.CFG"), StandardCharsets.ISO_8859_1);
        assertTrue(fixed.contains(".\\MASTER.DAT"), "Should rewrite to relative path");
        assertTrue(fixed.contains(".\\CRITTER.DAT"), "Both files should be rewritten");
        assertFalse(fixed.contains("FALLOUT1"), "Stale prefix should be removed");
    }

    @Test
    void fixAbsolutePaths_leavesNonCDrives(@TempDir Path dir) throws Exception {
        // D: drive references are CD-ROM — should stay
        String content = "cd_path=D:\\GAMEDATA\\SOUND.DAT\n";
        Files.writeString(dir.resolve("config.ini"), content, StandardCharsets.ISO_8859_1);

        patcher.fixAbsolutePaths(dir);

        String fixed = Files.readString(dir.resolve("config.ini"), StandardCharsets.ISO_8859_1);
        assertEquals(content.trim(), fixed.trim(), "D: references should be preserved");
    }

    @Test
    void fixAbsolutePaths_leavesValidPaths(@TempDir Path dir) throws Exception {
        // Path exists with full structure — should stay as-is
        Path sub = Files.createDirectory(dir.resolve("VALIDDIR"));
        Files.writeString(sub.resolve("file.dat"), "data");

        String content = "data=C:\\VALIDDIR\\file.dat\n";
        Files.writeString(dir.resolve("config.ini"), content, StandardCharsets.ISO_8859_1);

        patcher.fixAbsolutePaths(dir);

        String fixed = Files.readString(dir.resolve("config.ini"), StandardCharsets.ISO_8859_1);
        assertEquals(content.trim(), fixed.trim(), "Valid full paths should be preserved");
    }

    @Test
    void fixAbsolutePaths_skipsNonTextFiles(@TempDir Path dir) throws Exception {
        // Binary file with null bytes — should not be processed
        byte[] binary = new byte[200];
        binary[0] = 'C';
        binary[1] = ':';
        binary[2] = '\\';
        binary[3] = 'D';
        binary[10] = 0; // null byte makes it "not text"
        Files.write(dir.resolve("binary.dat"), binary);

        // Should not throw or modify binary files
        assertDoesNotThrow(() -> patcher.fixAbsolutePaths(dir));

        byte[] result = Files.readAllBytes(dir.resolve("binary.dat"));
        assertArrayEquals(binary, result, "Binary file should be unchanged");
    }

    @Test
    void fixAbsolutePaths_skipsLargeFiles(@TempDir Path dir) throws Exception {
        // File > 100KB should be skipped
        byte[] large = new byte[101 * 1024];
        large[0] = 'C';
        large[1] = ':';
        large[2] = '\\';
        Files.write(dir.resolve("large.cfg"), large);

        // Should not throw
        assertDoesNotThrow(() -> patcher.fixAbsolutePaths(dir));
    }

    @Test
    void fixAbsolutePaths_emptyDir_noError(@TempDir Path dir) {
        assertDoesNotThrow(() -> patcher.fixAbsolutePaths(dir));
    }

    @Test
    void fixAbsolutePaths_unixStylePath(@TempDir Path dir) throws Exception {
        // Some games use forward slashes: C:/GAME/DATA
        String content = "path=C:/MYGAME/DATA.DAT\n";
        Files.writeString(dir.resolve("config.cfg"), content, StandardCharsets.ISO_8859_1);
        Files.writeString(dir.resolve("DATA.DAT"), "data");

        patcher.fixAbsolutePaths(dir);

        String fixed = Files.readString(dir.resolve("config.cfg"), StandardCharsets.ISO_8859_1);
        assertTrue(fixed.contains("./DATA.DAT"), "Forward-slash paths should be fixed");
    }
}
