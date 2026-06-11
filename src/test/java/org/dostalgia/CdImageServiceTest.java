package org.dostalgia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CdImageServiceTest {

    private final CdImageService service = new CdImageService();

    @Test
    void findInDirectory_findsAllCdFormats(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("game.cue"), "FILE \"game.bin\" BINARY");
        Files.writeString(dir.resolve("game.bin"), "data");
        Files.writeString(dir.resolve("game.iso"), "iso");
        Files.writeString(dir.resolve("readme.txt"), "text");

        List<String> result = service.findInDirectory(dir);
        assertEquals(3, result.size());
        assertTrue(result.contains("game.bin"));
        assertTrue(result.contains("game.cue"));
        assertTrue(result.contains("game.iso"));
        assertFalse(result.contains("readme.txt"));
    }

    @Test
    void findInDirectory_emptyDir(@TempDir Path dir) throws Exception {
        assertTrue(service.findInDirectory(dir).isEmpty());
    }

    @Test
    void findInDirectory_caseInsensitive(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("GAME.CUE"), "data");
        Files.writeString(dir.resolve("GAME.ISO"), "data");
        List<String> result = service.findInDirectory(dir);
        assertEquals(2, result.size());
    }

    @Test
    void fixCueReferences_rewritesNonexistentReference(@TempDir Path dir) throws Exception {
        // CUE references game.bin, but only game.img exists
        Files.writeString(dir.resolve("game.cue"), "FILE \"game.bin\" BINARY\n  TRACK 01 MODE1/2352\n    INDEX 01 00:00:00");
        Files.writeString(dir.resolve("game.img"), "data");

        service.fixCueReferences(dir);

        String fixed = Files.readString(dir.resolve("game.cue"));
        assertTrue(fixed.contains("game.img"), "Should reference existing .img file");
        assertFalse(fixed.contains("game.bin"), "Should no longer reference missing .bin");
    }

    @Test
    void fixCueReferences_skipsWhenExists(@TempDir Path dir) throws Exception {
        // CUE references game.bin — and it exists, so no change
        String original = "FILE \"game.bin\" BINARY\n  TRACK 01 MODE1/2352\n    INDEX 01 00:00:00";
        Files.writeString(dir.resolve("game.cue"), original);
        Files.writeString(dir.resolve("game.bin"), "data");

        service.fixCueReferences(dir);

        assertEquals(original, Files.readString(dir.resolve("game.cue")).trim());
    }

    @Test
    void fixCueReferences_noFileLine_noChange(@TempDir Path dir) throws Exception {
        String original = "TITLE \"Test Game\"\nTRACK 01 MODE1/2352";
        Files.writeString(dir.resolve("game.cue"), original);

        service.fixCueReferences(dir);

        assertEquals(original, Files.readString(dir.resolve("game.cue")).trim());
    }

    @Test
    void findInBundle_findsCdImages(@TempDir Path dir) throws Exception {
        // Create a test bundle with CD images and regular files
        Path bundle = dir.resolve("test.jsdos");
        try (var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(bundle))) {
            zos.putNextEntry(new java.util.zip.ZipEntry("game.cue"));
            zos.write("cue data".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("game.iso"));
            zos.write("iso data".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("readme.txt"));
            zos.write("text".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new java.util.zip.ZipEntry("subdir/game.img"));
            zos.write("img".getBytes());
            zos.closeEntry();
        }

        List<String> result = service.findInBundle(bundle);
        assertEquals(3, result.size());
        assertTrue(result.contains("game.cue"));
        assertTrue(result.contains("game.iso"));
        assertTrue(result.contains("subdir/game.img"));
    }
}
