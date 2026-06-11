package org.dostalgia;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(SerializationFeature.INDENT_OUTPUT);

    // -- sanitizeId tests --

    @Test
    void sanitizeId_normalString_kept() {
        assertEquals("hello", GameService.sanitizeId("hello"));
    }

    @Test
    void sanitizeId_uppercaseToLowercase() {
        assertEquals("hello", GameService.sanitizeId("HELLO"));
    }

    @Test
    void sanitizeId_spacesToHyphens() {
        assertEquals("hello-world", GameService.sanitizeId("hello world"));
    }

    @Test
    void sanitizeId_specialCharsStripped() {
        assertEquals("helloworld", GameService.sanitizeId("hello!@#$world"));
    }

    @Test
    void sanitizeId_tripleHyphenStripped() {
        assertEquals("test", GameService.sanitizeId("---test---"));
    }

    @Test
    void sanitizeId_emptyReturnsUnknown() {
        assertEquals("unknown", GameService.sanitizeId(""));
    }

    @Test
    void sanitizeId_onlySpecialCharsReturnsUnknown() {
        assertEquals("unknown", GameService.sanitizeId("!@#$%^&*()"));
    }

    @Test
    void sanitizeId_mixedCaseAndSpaces() {
        assertEquals("doom-ii-hell-on-earth", GameService.sanitizeId("DOOM II: Hell on Earth"));
    }

    @Test
    void sanitizeId_underscoresToHyphens() {
        assertEquals("my-game", GameService.sanitizeId("my_game"));
    }

    @Test
    void sanitizeId_numbersPreserved() {
        assertEquals("game2", GameService.sanitizeId("Game2"));
    }

    // -- gameDir / gameJsonPath tests --

    @Test
    void gameDir_returnsCorrectPath(@TempDir Path tempDir) throws Exception {
        GameService svc = new GameService();
        svc.dataDir = tempDir.toString();
        svc.init();

        Path dir = svc.gameDir("test-game");
        assertEquals(tempDir.resolve("games/test-game"), dir);
    }

    @Test
    void gameJsonPath_returnsCorrectPath(@TempDir Path tempDir) throws Exception {
        GameService svc = new GameService();
        svc.dataDir = tempDir.toString();
        svc.init();

        Path path = svc.gameJsonPath("test-game");
        assertEquals(tempDir.resolve("games/test-game/game.json"), path);
    }

    // -- init tests --

    @Test
    void init_createsDirectories(@TempDir Path tempDir) throws Exception {
        GameService svc = new GameService();
        svc.dataDir = tempDir.toString();
        svc.init();

        assertTrue(Files.isDirectory(tempDir.resolve("games")));
        assertTrue(Files.isDirectory(tempDir.resolve("artwork")));
        assertTrue(Files.isDirectory(tempDir.resolve("saves")));
    }

    // -- save / load tests --

    @Test
    void saveAndLoad_roundTrip(@TempDir Path tempDir) throws Exception {
        GameService svc = new GameService();
        svc.dataDir = tempDir.toString();
        svc.init();

        Game game = new Game("test-game", "Test Game");
        game.setYear(1995);
        game.setGenre("FPS");
        svc.save(game);

        Game loaded = svc.load("test-game");
        assertEquals("test-game", loaded.getId());
        assertEquals("Test Game", loaded.getTitle());
        assertEquals(1995, loaded.getYear());
        assertEquals("FPS", loaded.getGenre());
        assertNotNull(loaded.getCreatedAt());
        assertNotNull(loaded.getUpdatedAt());
    }

    @Test
    void save_updatesUpdatedAt(@TempDir Path tempDir) throws Exception {
        GameService svc = new GameService();
        svc.dataDir = tempDir.toString();
        svc.init();

        Game game = new Game("game1", "Game One");
        svc.save(game);
        Thread.sleep(10); // ensure timestamp advances

        game.setGenre("Adventure");
        svc.save(game);

        Game loaded = svc.load("game1");
        assertNotNull(loaded.getUpdatedAt());
        assertTrue(loaded.getUpdatedAt().isAfter(loaded.getCreatedAt())
            || loaded.getUpdatedAt().equals(loaded.getCreatedAt()),
            "updatedAt should be >= createdAt");
    }

    @Test
    void load_nonExistent_throwsNoSuchFileException(@TempDir Path tempDir) throws Exception {
        GameService svc = new GameService();
        svc.dataDir = tempDir.toString();
        svc.init();

        assertThrows(NoSuchFileException.class, () -> svc.load("nonexistent"));
    }

    // -- list tests --

    @Test
    void list_emptyDir_returnsEmpty(@TempDir Path tempDir) throws Exception {
        GameService svc = new GameService();
        svc.dataDir = tempDir.toString();
        svc.init();

        List<Game> games = svc.list();
        assertTrue(games.isEmpty());
    }

    @Test
    void saveAndList_returnsGame(@TempDir Path tempDir) throws Exception {
        GameService svc = new GameService();
        svc.dataDir = tempDir.toString();
        svc.init();

        svc.save(new Game("game1", "Game One"));
        svc.save(new Game("game2", "Game Two"));

        List<Game> games = svc.list();
        assertEquals(2, games.size());
    }

    @Test
    void list_sortsByTitle(@TempDir Path tempDir) throws Exception {
        GameService svc = new GameService();
        svc.dataDir = tempDir.toString();
        svc.init();

        svc.save(new Game("zzz", "Z Game"));
        svc.save(new Game("aaa", "A Game"));

        List<Game> games = svc.list();
        assertEquals(2, games.size());
        assertEquals("A Game", games.get(0).getTitle());
        assertEquals("Z Game", games.get(1).getTitle());
    }

    // -- delete tests --

    @Test
    void delete_removesGameDir(@TempDir Path tempDir) throws Exception {
        GameService svc = new GameService();
        svc.dataDir = tempDir.toString();
        svc.init();

        svc.save(new Game("game1", "Game One"));
        assertTrue(Files.exists(svc.gameDir("game1")));

        svc.delete("game1");
        assertFalse(Files.exists(svc.gameDir("game1")));
    }

    @Test
    void delete_nonExistent_doesNotThrow(@TempDir Path tempDir) throws Exception {
        GameService svc = new GameService();
        svc.dataDir = tempDir.toString();
        svc.init();

        assertDoesNotThrow(() -> svc.delete("nonexistent"));
    }

    @Test
    void delete_removesOldJsdosFiles(@TempDir Path tempDir) throws Exception {
        GameService svc = new GameService();
        svc.dataDir = tempDir.toString();
        svc.init();

        // Create backward-compat files that delete should clean up
        Files.writeString(tempDir.resolve("games/game1.jsdos"), "old");
        Files.writeString(tempDir.resolve("games/game1.setup.jsdos"), "old-setup");

        svc.delete("game1");

        assertFalse(Files.exists(tempDir.resolve("games/game1.jsdos")));
        assertFalse(Files.exists(tempDir.resolve("games/game1.setup.jsdos")));
    }

    // -- JSON serialization tests --

    @Test
    void gameSerialization_snakeCase(@TempDir Path tempDir) throws Exception {
        Game g = new Game("my-game", "My Game");
        g.setIgdbCoverId("abc123");
        g.setHasCover(true);
        g.setHasScreenshots(false);
        g.setIgdbId(42);
        g.setBundleSize(1024L);
        g.setSetupExe("setup.exe");

        String json = MAPPER.writeValueAsString(g);
        assertTrue(json.contains("\"igdb_cover_id\""));
        assertTrue(json.contains("\"has_cover\""));
        assertTrue(json.contains("\"has_screenshots\""));
        assertTrue(json.contains("\"bundle_size\""));
        assertTrue(json.contains("\"setup_exe\""));
        assertFalse(json.contains("igdbCoverId"));
        assertFalse(json.contains("hasCover"));
        assertFalse(json.contains("hasScreenshots"));
        assertFalse(json.contains("bundleSize"));
        assertFalse(json.contains("setupExe"));

        Game deserialized = MAPPER.readValue(json, Game.class);
        assertEquals("my-game", deserialized.getId());
        assertEquals("My Game", deserialized.getTitle());
        assertEquals("abc123", deserialized.getIgdbCoverId());
        assertTrue(deserialized.isHasCover());
        assertFalse(deserialized.isHasScreenshots());
        assertEquals(Integer.valueOf(42), deserialized.getIgdbId());
        assertEquals(Long.valueOf(1024L), deserialized.getBundleSize());
        assertEquals("setup.exe", deserialized.getSetupExe());
    }

    @Test
    void gameSerialization_timestamps(@TempDir Path tempDir) throws Exception {
        Game g = new Game("ts-game", "Timestamp Test");
        String json = MAPPER.writeValueAsString(g);

        assertTrue(json.contains("\"created_at\""));
        assertTrue(json.contains("\"updated_at\""));

        Game deserialized = MAPPER.readValue(json, Game.class);
        assertNotNull(deserialized.getCreatedAt());
        assertNotNull(deserialized.getUpdatedAt());
    }
}
