package org.dostalgia;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameTest {

    @Test
    void noArgConstructor_setsNothing() {
        Game g = new Game();
        assertNull(g.getId());
        assertNull(g.getTitle());
        assertNull(g.getYear());
        assertNull(g.getGenre());
        assertNull(g.getDeveloper());
        assertNull(g.getPublisher());
        assertNull(g.getDescription());
        assertNull(g.getRating());
        assertNull(g.getBundleFile());
        assertNull(g.getSetupExe());
        assertNull(g.getBundleSize());
        assertNull(g.getExecutable());
        assertNull(g.getExecutables());
        assertNull(g.getPlatform());
        assertNull(g.getIgdbId());
        assertNull(g.getIgdbCoverId());
        assertFalse(g.isHasCover());
        assertFalse(g.isHasScreenshots());
        assertNull(g.getScreenshots());
        assertNull(g.getVideos());
        assertFalse(g.isReady());
        assertNull(g.getCreatedAt());
        assertNull(g.getUpdatedAt());
    }

    @Test
    void twoArgConstructor_setsIdTitleAndTimestamps() {
        Game g = new Game("test-id", "Test Game");
        assertEquals("test-id", g.getId());
        assertEquals("Test Game", g.getTitle());
        assertFalse(g.isReady());
        assertNotNull(g.getCreatedAt());
        assertNotNull(g.getUpdatedAt());
    }

    @Test
    void settersAndGetters_roundTrip() {
        Game g = new Game();
        g.setId("id1");
        g.setTitle("Title");
        g.setYear(1995);
        g.setGenre("Action");
        g.setDeveloper("DevCo");
        g.setPublisher("PubCo");
        g.setDescription("A great game");
        g.setRating(4.5);
        g.setBundleFile("game.zip");
        g.setSetupExe("setup.exe");
        g.setBundleSize(12345L);
        g.setExecutable("game.exe");
        g.setExecutables(List.of("game.exe", "setup.exe"));
        g.setPlatform("dos");
        g.setIgdbId(42);
        g.setIgdbCoverId("abc123");
        g.setHasCover(true);
        g.setHasScreenshots(true);
        g.setScreenshots(List.of("shot1.png"));
        g.setVideos(List.of("vid1"));
        g.setReady(true);
        Instant now = Instant.now();
        g.setCreatedAt(now);
        g.setUpdatedAt(now);

        assertEquals("id1", g.getId());
        assertEquals("Title", g.getTitle());
        assertEquals(1995, g.getYear());
        assertEquals("Action", g.getGenre());
        assertEquals("DevCo", g.getDeveloper());
        assertEquals("PubCo", g.getPublisher());
        assertEquals("A great game", g.getDescription());
        assertEquals(4.5, g.getRating());
        assertEquals("game.zip", g.getBundleFile());
        assertEquals("setup.exe", g.getSetupExe());
        assertEquals(12345L, g.getBundleSize());
        assertEquals("game.exe", g.getExecutable());
        assertEquals(List.of("game.exe", "setup.exe"), g.getExecutables());
        assertEquals("dos", g.getPlatform());
        assertEquals(42, g.getIgdbId());
        assertEquals("abc123", g.getIgdbCoverId());
        assertTrue(g.isHasCover());
        assertTrue(g.isHasScreenshots());
        assertEquals(List.of("shot1.png"), g.getScreenshots());
        assertEquals(List.of("vid1"), g.getVideos());
        assertTrue(g.isReady());
        assertEquals(now, g.getCreatedAt());
        assertEquals(now, g.getUpdatedAt());
    }

    @Test
    void isHasSetup_nullSetupExe_returnsFalse() {
        Game g = new Game();
        g.setSetupExe(null);
        assertFalse(g.isHasSetup());
    }

    @Test
    void isHasSetup_blankSetupExe_returnsFalse() {
        Game g = new Game();
        g.setSetupExe("   ");
        assertFalse(g.isHasSetup());
    }

    @Test
    void isHasSetup_nonBlankSetupExe_returnsTrue() {
        Game g = new Game();
        g.setSetupExe("setup.exe");
        assertTrue(g.isHasSetup());
    }

    @Test
    void isPlayable_notReady_returnsFalse() {
        Game g = new Game();
        g.setReady(false);
        assertFalse(g.isPlayable());
    }

    @Test
    void isPlayable_readyNullPlatform_returnsTrue() {
        Game g = new Game();
        g.setReady(true);
        g.setPlatform(null);
        assertTrue(g.isPlayable());
    }

    @Test
    void isPlayable_readyDosPlatform_returnsTrue() {
        Game g = new Game();
        g.setReady(true);
        g.setPlatform("dos");
        assertTrue(g.isPlayable());
    }

    @Test
    void isPlayable_readyWindowsPlatform_returnsFalse() {
        Game g = new Game();
        g.setReady(true);
        g.setPlatform("windows");
        assertFalse(g.isPlayable());
    }
}
