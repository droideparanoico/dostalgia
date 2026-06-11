package org.dostalgia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutableDetectorTest {

    private final PlatformDetector platform = new PlatformDetector();
    private final ExecutableDetector detector = new ExecutableDetector();

    // Manually wire dependencies since we're not in a CDI container
    {
        detector.platform = platform;
    }

    @Test
    void findAll_findsExeComBat(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("GAME.EXE"), "MZ");
        Files.writeString(dir.resolve("helper.com"), "MZ");
        Files.writeString(dir.resolve("run.bat"), "@echo off");
        Files.writeString(dir.resolve("readme.txt"), "text"); // should NOT be found

        List<String> result = detector.findAll(dir);
        assertEquals(3, result.size());
        assertTrue(result.contains("GAME.EXE"));
        assertTrue(result.contains("helper.com"));
        assertTrue(result.contains("run.bat"));
        assertFalse(result.contains("readme.txt"));
    }

    @Test
    void findMain_prefersLargestNonInstaller(@TempDir Path dir) throws Exception {
        // Small installer
        Files.write(dir.resolve("INSTALL.EXE"), createMZ(100));
        // Large real game
        Files.write(dir.resolve("GAME.EXE"), createMZ(5000));

        String main = detector.findMain(dir);
        assertEquals(dir.resolve("GAME.EXE").toString(), main);
    }

    @Test
    void findMain_skipsExtenders(@TempDir Path dir) throws Exception {
        Files.write(dir.resolve("DOS4GW.EXE"), createMZ(200));
        Files.write(dir.resolve("GAME.EXE"), createMZ(1000));

        String main = detector.findMain(dir);
        assertEquals(dir.resolve("GAME.EXE").toString(), main);
    }

    @Test
    void findMain_skipsSelfExtractors(@TempDir Path dir) throws Exception {
        byte[] sfx = createMZ(500);
        // Embed PKSFX string
        String content = new String(sfx);
        int pos = content.indexOf("MZ") + 2;
        byte[] withSfx = new byte[sfx.length + 20];
        System.arraycopy(sfx, 0, withSfx, 0, sfx.length);
        byte[] pksfx = "PKSFX".getBytes();
        System.arraycopy(pksfx, 0, withSfx, pos, pksfx.length);
        Files.write(dir.resolve("GAME.EXE"), withSfx);
        Files.write(dir.resolve("REAL.EXE"), createMZ(2000));

        String main = detector.findMain(dir);
        assertEquals(dir.resolve("REAL.EXE").toString(), main);
    }

    @Test
    void findMain_emptyDir_returnsNull(@TempDir Path dir) throws Exception {
        assertNull(detector.findMain(dir));
    }

    @Test
    void findMain_subdirectoryDeprioritized(@TempDir Path dir) throws Exception {
        // Root-level smaller exe
        Files.write(dir.resolve("GAME.EXE"), createMZ(1000));
        // Subdirectory larger exe — wins on size before depth is considered
        Path sub = Files.createDirectory(dir.resolve("SUBDIR"));
        Files.write(sub.resolve("OTHER.EXE"), createMZ(2000));

        String main = detector.findMain(dir);
        // Larger file wins even though it's deeper (size sorts before depth)
        assertEquals(sub.resolve("OTHER.EXE").toString(), main);
    }

    @Test
    void findSetup_findsSetupExe(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("SETUP.EXE"), "MZ");
        Files.writeString(dir.resolve("GAME.EXE"), "MZ");

        String setup = detector.findSetup(dir);
        assertEquals(dir.resolve("SETUP.EXE").toString(), setup);
    }

    @Test
    void findSetup_findsInstallBat(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("INSTALL.BAT"), "@echo off");

        String setup = detector.findSetup(dir);
        assertEquals(dir.resolve("INSTALL.BAT").toString(), setup);
    }

    @Test
    void findSetup_noSetup_returnsNull(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("GAME.EXE"), "MZ");
        assertNull(detector.findSetup(dir));
    }

    @Test
    void isLikelySelfExtractor_detectsPKSFX(@TempDir Path dir) throws Exception {
        byte[] buf = new byte[1000];
        buf[0] = 0x4D; // M
        buf[1] = 0x5A; // Z
        byte[] pksfx = "PKSFX".getBytes();
        System.arraycopy(pksfx, 0, buf, 10, pksfx.length);

        Path f = dir.resolve("sfx.exe");
        Files.write(f, buf);
        assertTrue(ExecutableDetector.isLikelySelfExtractor(f));
    }

    @Test
    void isLikelySelfExtractor_plainExe_returnsFalse(@TempDir Path dir) throws Exception {
        byte[] buf = createMZ(500);
        Path f = dir.resolve("game.exe");
        Files.write(f, buf);
        assertFalse(ExecutableDetector.isLikelySelfExtractor(f));
    }

    @Test
    void findMain_prefersDosOverWindows(@TempDir Path dir) throws Exception {
        // Windows PE executable: MZ header + PE signature at offset 0x80
        byte[] windowsExe = new byte[0x100];
        windowsExe[0] = 0x4D; windowsExe[1] = 0x5A; // MZ
        windowsExe[0x3C] = (byte) 0x80; // PE offset at 0x80
        windowsExe[0x80] = 0x50; windowsExe[0x81] = 0x45; // "PE" signature
        Files.write(dir.resolve("WIN.EXE"), windowsExe);

        // DOS executable: just MZ header, no PE
        byte[] dosExe = new byte[0x40];
        dosExe[0] = 0x4D; dosExe[1] = 0x5A; // MZ
        Files.write(dir.resolve("DOS.EXE"), dosExe);

        String main = detector.findMain(dir);
        assertNotNull(main);
        assertTrue(main.endsWith("DOS.EXE"),
            "DOS executable should be preferred over Windows, but got: " + main);
    }

    @Test
    void findMain_returnsNullForNoExecutables(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("readme.txt"), "text");
        Files.writeString(dir.resolve("notes.md"), "markdown");
        assertNull(detector.findMain(dir));
    }

    @Test
    void findMain_skipsSelfExtractorsWithPkSignature(@TempDir Path dir) throws Exception {
        // Self-extractor with PK\x03\x04 signature bytes (ZIP local header)
        byte[] sfx = new byte[0x80];
        sfx[0] = 0x4D; sfx[1] = 0x5A; // MZ header
        sfx[0x60] = 0x50; sfx[0x61] = 0x4B; sfx[0x62] = 0x03; sfx[0x63] = 0x04; // PK\x03\x04
        Files.write(dir.resolve("SFX.EXE"), sfx);

        // Real game executable
        Files.write(dir.resolve("GAME.EXE"), createMZ(2000));

        String main = detector.findMain(dir);
        assertNotNull(main);
        assertTrue(main.endsWith("GAME.EXE"),
            "Self-extractor with PK signature bytes should be skipped, but got: " + main);
    }

    @Test
    void findMain_handlesMixedContent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("readme.txt"), "text");
        Files.write(dir.resolve("DOS4GW.EXE"), createMZ(200));  // extender, skipped
        Files.write(dir.resolve("INSTALL.EXE"), createMZ(150)); // installer, deprioritized
        Files.write(dir.resolve("GAME.EXE"), createMZ(5000));   // real game, should win
        Files.write(dir.resolve("helper.com"), createMZ(100));  // small .com

        String main = detector.findMain(dir);
        assertNotNull(main);
        assertTrue(main.endsWith("GAME.EXE"),
            "Main game executable should be selected, but got: " + main);
    }

    @Test
    void findAll_returnsEmptyForEmptyDir(@TempDir Path dir) throws Exception {
        assertTrue(detector.findAll(dir).isEmpty());
    }

    private static byte[] createMZ(int size) {
        byte[] buf = new byte[size];
        buf[0] = 0x4D; // M
        buf[1] = 0x5A; // Z
        return buf;
    }
}
