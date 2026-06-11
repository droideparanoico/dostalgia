package org.dostalgia;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StaticResourceTest {

    @Test
    void health_returnsOkStatusAndVersion(@TempDir Path tempDir) {
        StaticResource sr = new StaticResource();
        sr.dataDir = tempDir.toString();

        Response resp = sr.health();
        assertEquals(200, resp.getStatus());
        Object entity = resp.getEntity();
        assertInstanceOf(Map.class, entity);
        Map<?, ?> map = (Map<?, ?>) entity;
        assertEquals("ok", map.get("status"));
        assertEquals("0.1.0", map.get("version"));
    }

    @Test
    void getGameFile_jsdosContentType(@TempDir Path tempDir) throws Exception {
        Path gamesDir = Files.createDirectories(tempDir.resolve("games"));
        Files.writeString(gamesDir.resolve("test.jsdos"), "zip content");

        StaticResource sr = new StaticResource();
        sr.dataDir = tempDir.toString();

        Response resp = sr.getGameFile("test.jsdos");
        assertEquals(200, resp.getStatus());
        assertNotNull(resp.getMediaType());
        assertEquals("application/zip", resp.getMediaType().toString());
    }

    @Test
    void getGameFile_zipContentType(@TempDir Path tempDir) throws Exception {
        Path gamesDir = Files.createDirectories(tempDir.resolve("games"));
        Files.writeString(gamesDir.resolve("archive.zip"), "zip content");

        StaticResource sr = new StaticResource();
        sr.dataDir = tempDir.toString();

        Response resp = sr.getGameFile("archive.zip");
        assertEquals(200, resp.getStatus());
        assertNotNull(resp.getMediaType());
        assertEquals("application/zip", resp.getMediaType().toString());
    }

    @Test
    void getGameFile_jpgContentType(@TempDir Path tempDir) throws Exception {
        Path gamesDir = Files.createDirectories(tempDir.resolve("games"));
        Files.writeString(gamesDir.resolve("cover.jpg"), "image data");

        StaticResource sr = new StaticResource();
        sr.dataDir = tempDir.toString();

        Response resp = sr.getGameFile("cover.jpg");
        assertEquals(200, resp.getStatus());
        assertNotNull(resp.getMediaType());
        assertEquals("image/jpeg", resp.getMediaType().toString());
    }

    @Test
    void getGameFile_jpegContentType(@TempDir Path tempDir) throws Exception {
        Path gamesDir = Files.createDirectories(tempDir.resolve("games"));
        Files.writeString(gamesDir.resolve("photo.jpeg"), "image data");

        StaticResource sr = new StaticResource();
        sr.dataDir = tempDir.toString();

        Response resp = sr.getGameFile("photo.jpeg");
        assertEquals(200, resp.getStatus());
        assertNotNull(resp.getMediaType());
        assertEquals("image/jpeg", resp.getMediaType().toString());
    }

    @Test
    void getGameFile_pngContentType(@TempDir Path tempDir) throws Exception {
        Path gamesDir = Files.createDirectories(tempDir.resolve("games"));
        Files.writeString(gamesDir.resolve("screenshot.png"), "png data");

        StaticResource sr = new StaticResource();
        sr.dataDir = tempDir.toString();

        Response resp = sr.getGameFile("screenshot.png");
        assertEquals(200, resp.getStatus());
        assertNotNull(resp.getMediaType());
        assertEquals("image/png", resp.getMediaType().toString());
    }

    @Test
    void getGameFile_webpContentType(@TempDir Path tempDir) throws Exception {
        Path gamesDir = Files.createDirectories(tempDir.resolve("games"));
        Files.writeString(gamesDir.resolve("image.webp"), "webp data");

        StaticResource sr = new StaticResource();
        sr.dataDir = tempDir.toString();

        Response resp = sr.getGameFile("image.webp");
        assertEquals(200, resp.getStatus());
        assertNotNull(resp.getMediaType());
        assertEquals("image/webp", resp.getMediaType().toString());
    }

    @Test
    void getGameFile_gifContentType(@TempDir Path tempDir) throws Exception {
        Path gamesDir = Files.createDirectories(tempDir.resolve("games"));
        Files.writeString(gamesDir.resolve("anim.gif"), "gif data");

        StaticResource sr = new StaticResource();
        sr.dataDir = tempDir.toString();

        Response resp = sr.getGameFile("anim.gif");
        assertEquals(200, resp.getStatus());
        assertNotNull(resp.getMediaType());
        assertEquals("image/gif", resp.getMediaType().toString());
    }

    @Test
    void getGameFile_jsonContentType(@TempDir Path tempDir) throws Exception {
        Path gamesDir = Files.createDirectories(tempDir.resolve("games"));
        Files.writeString(gamesDir.resolve("data.json"), "{}");

        StaticResource sr = new StaticResource();
        sr.dataDir = tempDir.toString();

        Response resp = sr.getGameFile("data.json");
        assertEquals(200, resp.getStatus());
        assertNotNull(resp.getMediaType());
        assertEquals("application/json", resp.getMediaType().toString());
    }

    @Test
    void getGameFile_exeContentType(@TempDir Path tempDir) throws Exception {
        Path gamesDir = Files.createDirectories(tempDir.resolve("games"));
        Files.writeString(gamesDir.resolve("game.exe"), "MZ");

        StaticResource sr = new StaticResource();
        sr.dataDir = tempDir.toString();

        Response resp = sr.getGameFile("game.exe");
        assertEquals(200, resp.getStatus());
        assertNotNull(resp.getMediaType());
        assertEquals("application/octet-stream", resp.getMediaType().toString());
    }

    @Test
    void getGameFile_pathTraversal_returns403(@TempDir Path tempDir) {
        StaticResource sr = new StaticResource();
        sr.dataDir = tempDir.toString();

        Response resp = sr.getGameFile("../etc/passwd");
        assertEquals(403, resp.getStatus());
    }

    @Test
    void getGameFile_nonExistentFile_returns404(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("games"));

        StaticResource sr = new StaticResource();
        sr.dataDir = tempDir.toString();

        Response resp = sr.getGameFile("nonexistent.exe");
        assertEquals(404, resp.getStatus());
    }

    @Test
    void getArtwork_contentTypeAndCacheHeader(@TempDir Path tempDir) throws Exception {
        Path artworkDir = Files.createDirectories(tempDir.resolve("artwork"));
        Files.writeString(artworkDir.resolve("cover.jpg"), "image data");

        StaticResource sr = new StaticResource();
        sr.dataDir = tempDir.toString();

        Response resp = sr.getArtwork("cover.jpg");
        assertEquals(200, resp.getStatus());
        assertNotNull(resp.getMediaType());
        assertEquals("image/jpeg", resp.getMediaType().toString());
        assertTrue(resp.getHeaders().containsKey("Cache-Control"));
    }

    @Test
    void getArtwork_pathTraversal_returns403(@TempDir Path tempDir) {
        StaticResource sr = new StaticResource();
        sr.dataDir = tempDir.toString();

        Response resp = sr.getArtwork("../../etc/passwd");
        assertEquals(403, resp.getStatus());
    }
}
