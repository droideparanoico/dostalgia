package org.dostalgia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ZipServiceTest {

    private final ZipService zip = new ZipService();

    // Wire the config dependency manually (no CDI)
    {
        zip.config = new ConfigBuilder();
    }

    @Test
    void unzip_extractsFiles(@TempDir Path dir) throws Exception {
        Path zipFile = dir.resolve("test.zip");
        Path dest = dir.resolve("output");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("game.exe"));
            zos.write("MZ".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("readme.txt"));
            zos.write("Hello".getBytes());
            zos.closeEntry();
        }

        zip.unzip(zipFile, dest);
        assertTrue(Files.exists(dest.resolve("game.exe")));
        assertTrue(Files.exists(dest.resolve("readme.txt")));
        assertEquals("MZ", Files.readString(dest.resolve("game.exe")));
        assertEquals("Hello", Files.readString(dest.resolve("readme.txt")));
    }

    @Test
    void unzip_handlesSubdirectories(@TempDir Path dir) throws Exception {
        Path zipFile = dir.resolve("test.zip");
        Path dest = dir.resolve("output");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("subdir/game.exe"));
            zos.write("MZ".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("subdir/nested/deep.txt"));
            zos.write("deep".getBytes());
            zos.closeEntry();
        }

        zip.unzip(zipFile, dest);
        assertTrue(Files.exists(dest.resolve("subdir/game.exe")));
        assertTrue(Files.exists(dest.resolve("subdir/nested/deep.txt")));
    }

    @Test
    void unzip_pathTraversal_skipsMaliciousEntries(@TempDir Path dir) throws Exception {
        Path zipFile = dir.resolve("test.zip");
        Path dest = dir.resolve("output");
        Files.createDirectories(dest);

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("../evil.txt"));
            zos.write("malicious".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("good.txt"));
            zos.write("good".getBytes());
            zos.closeEntry();
        }

        zip.unzip(zipFile, dest);
        // Malicious entry should be skipped (path traversal)
        assertFalse(Files.exists(dir.resolve("evil.txt")));
        // Good entry should be extracted
        assertTrue(Files.exists(dest.resolve("good.txt")));
    }

    @Test
    void unzip_directoryEntries_createDirs(@TempDir Path dir) throws Exception {
        Path zipFile = dir.resolve("test.zip");
        Path dest = dir.resolve("output");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry("adir/"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("adir/file.txt"));
            zos.write("data".getBytes());
            zos.closeEntry();
        }

        zip.unzip(zipFile, dest);
        assertTrue(Files.isDirectory(dest.resolve("adir")));
        assertTrue(Files.exists(dest.resolve("adir/file.txt")));
    }

    @Test
    void flattenSingleDir_noOpWhenMultipleDirs(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("sub1"));
        Files.createDirectories(dir.resolve("sub2"));
        Files.writeString(dir.resolve("sub1/file.txt"), "data");

        zip.flattenSingleDir(dir);
        assertTrue(Files.exists(dir.resolve("sub1/file.txt")));
        assertTrue(Files.isDirectory(dir.resolve("sub1")));
        assertTrue(Files.isDirectory(dir.resolve("sub2")));
    }

    @Test
    void flattenSingleDir_noOpWhenNoSingleDir(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("file.txt"), "data");
        Files.writeString(dir.resolve("other.txt"), "data");

        zip.flattenSingleDir(dir);
        assertTrue(Files.exists(dir.resolve("file.txt")));
        assertTrue(Files.exists(dir.resolve("other.txt")));
    }

    @Test
    void flattenSingleDir_flattensOneDir(@TempDir Path dir) throws Exception {
        Path single = Files.createDirectories(dir.resolve("single"));
        Files.createDirectories(single.resolve("nested"));
        Files.writeString(single.resolve("file.txt"), "data");
        Files.writeString(single.resolve("nested/deep.txt"), "deep");

        zip.flattenSingleDir(dir);
        // Files should now be directly under dir
        assertTrue(Files.exists(dir.resolve("file.txt")));
        assertTrue(Files.exists(dir.resolve("nested/deep.txt")));
        // The intermediate single dir should be gone
        assertFalse(Files.exists(single));
    }

    @Test
    void flattenSingleDir_emptyDir_noOp(@TempDir Path dir) throws Exception {
        zip.flattenSingleDir(dir);
        assertTrue(Files.exists(dir));
    }

    @Test
    void createBundle_createsValidJsdosZip(@TempDir Path dir) throws Exception {
        Path extractDir = dir.resolve("extract");
        Files.createDirectories(extractDir.resolve("sub"));
        Files.writeString(extractDir.resolve("game.exe"), "MZ");
        Files.writeString(extractDir.resolve("sub/data.bin"), "binary");
        Files.writeString(extractDir.resolve("readme.txt"), "info");

        Path bundlePath = dir.resolve("output.jsdos");

        zip.createBundle(extractDir, extractDir.resolve("game.exe").toString(), List.of(), bundlePath);

        assertTrue(Files.exists(bundlePath));
        assertTrue(Files.size(bundlePath) > 0);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(bundlePath))) {
            boolean hasGameExe = false;
            boolean hasJsdosDir = false;
            boolean hasJsdosConf = false;
            boolean hasJsdosJson = false;
            boolean hasDataBin = false;
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.equals("game.exe")) hasGameExe = true;
                if (name.startsWith(".jsdos/")) hasJsdosDir = true;
                if (name.equals(".jsdos/dosbox.conf")) hasJsdosConf = true;
                if (name.equals(".jsdos/jsdos.json")) hasJsdosJson = true;
                if (name.equals("sub/data.bin")) hasDataBin = true;
                zis.closeEntry();
            }
            assertTrue(hasGameExe, "Should contain game.exe");
            assertTrue(hasJsdosDir, "Should contain .jsdos/ entries");
            assertTrue(hasJsdosConf, "Should contain .jsdos/dosbox.conf");
            assertTrue(hasJsdosJson, "Should contain .jsdos/jsdos.json");
            assertTrue(hasDataBin, "Should contain sub/data.bin");
        }
    }

    @Test
    void createBundle_respectsSkipExt(@TempDir Path dir) throws Exception {
        Path extractDir = dir.resolve("extract");
        Files.createDirectories(extractDir);
        Files.writeString(extractDir.resolve("game.exe"), "MZ");
        Files.writeString(extractDir.resolve("cdimage.nrg"), "image");
        Files.writeString(extractDir.resolve("cdimage.mdf"), "image");

        Path bundlePath = dir.resolve("output.jsdos");

        zip.createBundle(extractDir, extractDir.resolve("game.exe").toString(), List.of(), bundlePath);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(bundlePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                assertNotEquals("cdimage.nrg", name, "Should not contain .nrg");
                assertNotEquals("cdimage.mdf", name, "Should not contain .mdf");
                zis.closeEntry();
            }
        }
    }

    @Test
    void createBundle_noExe_createsCdOnlyConfig(@TempDir Path dir) throws Exception {
        Path extractDir = dir.resolve("extract");
        Files.createDirectories(extractDir);
        Files.writeString(extractDir.resolve("somefile.bin"), "data");

        Path bundlePath = dir.resolve("output.jsdos");

        // null exePath triggers CD-only config generation
        zip.createBundle(extractDir, null, List.of("cd.iso"), bundlePath);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(bundlePath))) {
            boolean hasJsdosConf = false;
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(".jsdos/dosbox.conf")) hasJsdosConf = true;
                zis.closeEntry();
            }
            assertTrue(hasJsdosConf, "CD-only bundle should still have dosbox.conf");
        }
    }
}
