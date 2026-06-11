package org.dostalgia;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Manages game metadata stored as JSON files on disk. */
@ApplicationScoped
public class GameService implements GameStore {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(SerializationFeature.INDENT_OUTPUT);

    @Inject
    ConfigBuilder config;

    @Inject
    CdImageService cdImageService;

    @ConfigProperty(name = "dostalgia.data.dir", defaultValue = "data")
    String dataDir;

    Path gamesDir;

    @PostConstruct
    void init() {
        gamesDir = Path.of(dataDir, "games");
        try {
            Files.createDirectories(gamesDir);
            Files.createDirectories(Path.of(dataDir, "artwork"));
            Files.createDirectories(Path.of(dataDir, "saves"));
        } catch (IOException e) {
            throw new RuntimeException("Cannot create data directories", e);
        }
    }

    public Path gameDir(String id) {
        return gamesDir.resolve(id);
    }

    public Path gameJsonPath(String id) {
        return gameDir(id).resolve("game.json");
    }

    /** Load a game by ID. Throws if not found. */
    public Game load(String id) throws IOException {
        Path path = gameJsonPath(id);
        if (!Files.exists(path)) {
            throw new NoSuchFileException("game.json not found for: " + id);
        }
        Game game = mapper.readValue(path.toFile(), Game.class);
        // Detect cover file
        game.setHasCover(
            Files.exists(gameDir(id).resolve("cover.jpg")) ||
            Files.exists(gameDir(id).resolve("cover.png")) ||
            Files.exists(gameDir(id).resolve("cover.webp"))
        );
        // Detect bundle size
        try {
            String bf = game.getBundleFile();
            if (bf != null) {
                Path bundlePath = gamesDir.resolve(bf);
                if (Files.exists(bundlePath)) {
                    game.setBundleSize(Files.size(bundlePath));
                }
            }
        } catch (IOException ignored) {}
        return game;
    }

    /** Save game metadata to disk. */
    public void save(Game game) throws IOException {
        game.setUpdatedAt(Instant.now());
        if (game.getCreatedAt() == null) {
            game.setCreatedAt(Instant.now());
        }
        Files.createDirectories(gameDir(game.getId()));
        mapper.writeValue(gameJsonPath(game.getId()).toFile(), game);
    }

    /** List all games, sorted by title. */
    public List<Game> list() throws IOException {
        List<Game> games = new ArrayList<>();
        if (!Files.isDirectory(gamesDir)) return games;

        try (var stream = Files.list(gamesDir)) {
            for (Path dir : (Iterable<Path>) stream::iterator) {
                if (!Files.isDirectory(dir)) continue;
                try {
                    Game g = load(dir.getFileName().toString());
                    games.add(g);
                } catch (Exception ignored) {
                    // skip broken entries
                }
            }
        }

        games.sort(Comparator.comparing(Game::getTitle));
        return games;
    }

    /** Delete a game and all its files. */
    public void delete(String id) throws IOException {
        FileUtils.deleteDirectory(gameDir(id));
        // Clean up old flat .jsdos locations (pre-game-dir layout — backward compat)
        Files.deleteIfExists(gamesDir.resolve(id + ".jsdos"));
        Files.deleteIfExists(gamesDir.resolve(id + ".setup.jsdos"));
    }

    /** Sanitize a string for use as a game directory ID. */
    public static String sanitizeId(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= 'a' && c <= 'z') sb.append(c);
            else if (c >= 'A' && c <= 'Z') sb.append((char)(c + 32));
            else if (c >= '0' && c <= '9') sb.append(c);
            else if (c == ' ' || c == '-' || c == '_') sb.append('-');
        }
        String result = sb.toString().replaceAll("^-+|-+$", "");
        return result.isEmpty() ? "unknown" : result;
    }

    /**
     * Patch the .jsdos bundle to use a different main executable.
     * Transactional: patches into a temp file, then atomically replaces on success.
     */
    public void setExecutable(String id, String executable) throws IOException {
        Game game = load(id);
        String bundleFile = game.getBundleFile();
        if (bundleFile == null) {
            throw new IOException("Game has no bundle file");
        }

        Path bundlePath = gamesDir.resolve(bundleFile);
        if (!Files.exists(bundlePath)) {
            throw new NoSuchFileException("Bundle not found: " + bundleFile);
        }

        // Build new config entries — preserve any CD images in the bundle
        List<String> cdImages = cdImageService.findInBundle(bundlePath);
        byte[] newDosboxConf = config.buildDosboxConfBytes(executable, cdImages);
        byte[] newJsdosJson = config.buildJsdosJson(executable, cdImages);

        // Transactional patch: write to temp, detect platform, then move
        Path tmpPath = bundlePath.resolveSibling(bundlePath.getFileName() + ".tmp");
        try {
            try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(bundlePath));
                 var zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(tmpPath))) {

                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (".jsdos/dosbox.conf".equals(name)) {
                        zos.putNextEntry(new java.util.zip.ZipEntry(name));
                        zos.write(newDosboxConf);
                        zos.closeEntry();
                    } else if (".jsdos/jsdos.json".equals(name)) {
                        zos.putNextEntry(new java.util.zip.ZipEntry(name));
                        zos.write(newJsdosJson);
                        zos.closeEntry();
                    } else {
                        zos.putNextEntry(new java.util.zip.ZipEntry(name));
                        zis.transferTo(zos);
                        zos.closeEntry();
                    }
                    zis.closeEntry();
                }
            }

            // Detect platform from the new bundle before committing
            String detectedPlatform = detectPlatformInBundle(executable, tmpPath);

            // Atomic move on success
            Files.move(tmpPath, bundlePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            // Update metadata
            game.setExecutable(executable);
            game.setPlatform(detectedPlatform);
            save(game);

        } catch (Exception e) {
            // Clean up temp file on failure — leave original untouched
            try { Files.deleteIfExists(tmpPath); } catch (IOException ignored) {}
            throw e instanceof IOException ioe ? ioe : new IOException(e);
        }
    }

    /** Detect platform for an executable inside a .jsdos ZIP bundle. */
    private String detectPlatformInBundle(String relExe, Path bundlePath) throws IOException {
        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(bundlePath))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(relExe.replace('\\', '/'))) {
                    return PlatformDetector.detectFromBytes(PlatformDetector.readHeaderBytes(zis));
                }
                zis.closeEntry();
            }
        }
        return null;
    }

    /**
     * Stream a setup .jsdos bundle on-the-fly by reading the main bundle
     * and replacing config files to point at the setup executable.
     * No data is written to disk — the modified ZIP is streamed directly.
     */
    public void streamSetupBundle(String id, OutputStream out) throws IOException {
        Game game = load(id);
        String setupExe = game.getSetupExe();
        if (setupExe == null || setupExe.isBlank()) {
            throw new IOException("No setup executable configured for this game");
        }

        Path bundlePath = gamesDir.resolve(game.getBundleFile());
        if (!Files.exists(bundlePath)) {
            throw new NoSuchFileException("Bundle not found: " + game.getBundleFile());
        }

        // Build new config entries — preserve any CD images in the bundle
        List<String> cdImages = cdImageService.findInBundle(bundlePath);
        byte[] newDosboxConf = config.buildDosboxConfBytes(setupExe, cdImages);
        byte[] newJsdosJson = config.buildJsdosJson(setupExe, cdImages);

        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(bundlePath));
             var zos = new java.util.zip.ZipOutputStream(out)) {

            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                zos.putNextEntry(new java.util.zip.ZipEntry(name));
                if (".jsdos/dosbox.conf".equals(name)) {
                    zos.write(newDosboxConf);
                } else if (".jsdos/jsdos.json".equals(name)) {
                    zos.write(newJsdosJson);
                } else {
                    zis.transferTo(zos);
                }
                zos.closeEntry();
                zis.closeEntry();
            }
        }
    }

    public Path getGamesDir() { return gamesDir; }
}
