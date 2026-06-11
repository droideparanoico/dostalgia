package org.dostalgia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilsTest {

    @Test
    void deleteDirectory_nonExistentDir_doesNotThrow(@TempDir Path dir) {
        Path nonExistent = dir.resolve("doesnotexist");
        assertDoesNotThrow(() -> FileUtils.deleteDirectory(nonExistent));
    }

    @Test
    void deleteDirectory_deletesFilesRecursively(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("file1.txt"), "hello");
        Files.writeString(dir.resolve("file2.txt"), "world");
        Path sub = Files.createDirectories(dir.resolve("sub"));
        Files.writeString(sub.resolve("nested.txt"), "deep");

        assertTrue(Files.exists(dir.resolve("file1.txt")));
        FileUtils.deleteDirectory(dir);
        assertFalse(Files.exists(dir));
    }

    @Test
    void deleteDirectory_deletesSubdirectories(@TempDir Path dir) throws Exception {
        Path a = Files.createDirectories(dir.resolve("a/b/c"));
        Files.writeString(a.resolve("deep.txt"), "deep");
        Path sub2 = Files.createDirectories(dir.resolve("sub2"));
        Files.writeString(sub2.resolve("file.txt"), "data");

        FileUtils.deleteDirectory(dir);
        assertFalse(Files.exists(dir));
    }

    @Test
    void findFilesByExtensions_findsExeComBat(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("game.exe"), "data");
        Files.writeString(dir.resolve("helper.com"), "data");
        Files.writeString(dir.resolve("run.bat"), "data");
        Files.writeString(dir.resolve("readme.txt"), "data");

        List<String> result = FileUtils.findFilesByExtensions(dir, Set.of(".exe", ".com", ".bat"));
        assertEquals(3, result.size());
        assertTrue(result.contains("game.exe"));
        assertTrue(result.contains("helper.com"));
        assertTrue(result.contains("run.bat"));
    }

    @Test
    void findFilesByExtensions_caseInsensitive(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("GAME.EXE"), "data");
        Files.writeString(dir.resolve("Helper.Com"), "data");
        Files.writeString(dir.resolve("RUN.BAT"), "data");

        List<String> result = FileUtils.findFilesByExtensions(dir, Set.of(".exe", ".com", ".bat"));
        assertEquals(3, result.size());
    }

    @Test
    void findFilesByExtensions_emptyDir_returnsEmpty(@TempDir Path dir) throws Exception {
        List<String> result = FileUtils.findFilesByExtensions(dir, Set.of(".exe"));
        assertTrue(result.isEmpty());
    }

    @Test
    void findFilesByExtensions_noMatches_returnsEmpty(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("readme.txt"), "data");
        Files.writeString(dir.resolve("notes.md"), "data");

        List<String> result = FileUtils.findFilesByExtensions(dir, Set.of(".exe", ".com", ".bat"));
        assertTrue(result.isEmpty());
    }

    @Test
    void findFilesByExtensions_includesSubdirectories(@TempDir Path dir) throws Exception {
        Path sub = Files.createDirectories(dir.resolve("subdir"));
        Files.writeString(sub.resolve("game.exe"), "data");
        Files.writeString(dir.resolve("root.exe"), "data");

        List<String> result = FileUtils.findFilesByExtensions(dir, Set.of(".exe"));
        assertEquals(2, result.size());
        assertTrue(result.contains("root.exe"));
        assertTrue(result.contains("subdir/game.exe"));
    }
}
