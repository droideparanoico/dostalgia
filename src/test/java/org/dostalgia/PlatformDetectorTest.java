package org.dostalgia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlatformDetectorTest {

    private final PlatformDetector detector = new PlatformDetector();

    @Test
    void detect_comFile_returnsDos(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("test.com");
        Files.write(f, new byte[]{0x4D, 0x5A}); // MZ header
        assertEquals("dos", detector.detect(f));
    }

    @Test
    void detect_batFile_returnsDos(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("test.bat");
        Files.writeString(f, "@echo off");
        assertEquals("dos", detector.detect(f));
    }

    @Test
    void detect_plainMZ_returnsDos(@TempDir Path dir) throws Exception {
        byte[] buf = new byte[0x80];
        buf[0] = 0x4D; // M
        buf[1] = 0x5A; // Z
        // e_lfanew points to garbage — should be treated as plain DOS
        buf[0x3C] = 0x00;
        buf[0x3D] = 0x00;
        buf[0x3E] = 0x00;
        buf[0x3F] = 0x00;

        Path f = dir.resolve("test.exe");
        Files.write(f, buf);
        assertEquals("dos", detector.detect(f));
    }

    @Test
    void detect_peHeader_returnsWindows(@TempDir Path dir) throws Exception {
        byte[] buf = new byte[0x100];
        buf[0] = 0x4D; // M
        buf[1] = 0x5A; // Z
        // e_lfanew = 0x80
        buf[0x3C] = (byte) 0x80;
        buf[0x3D] = 0x00;
        buf[0x3E] = 0x00;
        buf[0x3F] = 0x00;
        // PE signature at offset 0x80
        buf[0x80] = 0x50; // P
        buf[0x81] = 0x45; // E

        Path f = dir.resolve("test.exe");
        Files.write(f, buf);
        assertEquals("windows", detector.detect(f));
    }

    @Test
    void detect_neHeader_returnsWindows(@TempDir Path dir) throws Exception {
        byte[] buf = new byte[0x100];
        buf[0] = 0x4D;
        buf[1] = 0x5A;
        buf[0x3C] = (byte) 0x80;
        buf[0x80] = 0x4E; // N
        buf[0x81] = 0x45; // E

        Path f = dir.resolve("test.exe");
        Files.write(f, buf);
        assertEquals("windows", detector.detect(f));
    }

    @Test
    void detect_emptyFile_returnsNull(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("empty.exe");
        Files.write(f, new byte[0]);
        assertNull(detector.detect(f));
    }

    @Test
    void detect_noMZ_returnsNull(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("bad.exe");
        Files.write(f, new byte[]{0x00, 0x00});
        assertNull(detector.detect(f));
    }
}
