package org.dostalgia;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@jakarta.ws.rs.Path("/api/upload")
@Produces(MediaType.APPLICATION_JSON)
public class UploadResource {

    @Inject
    GameService svc;

    @Inject
    IgdbService igdb;

    @Inject
    ZipService zip;

    @Inject
    ExecutableDetector exeDetector;

    @Inject
    PlatformDetector platform;

    @Inject
    CdImageService cdImageService;

    @Inject
    ConfigPatcher configPatcher;

    private static final Logger LOG = Logger.getLogger(UploadResource.class.getName());

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response upload(
        @RestForm("file") FileUpload upload,
        @RestForm("title") String title,
        @RestForm("igdb_id") Integer igdbId
    ) throws Exception {
        if (upload == null) {
            return Response.status(400).entity(Map.of("error", "No file provided")).build();
        }

        String filename = upload.fileName();
        if (title == null || title.isBlank()) {
            title = filename.endsWith(".zip") ? filename.substring(0, filename.length() - 4) : filename;
        }

        String gameId = GameService.sanitizeId(title);

        // Check for duplicate by title (case-insensitive)
        for (var g : svc.list()) {
            if (g.getTitle() != null && g.getTitle().equalsIgnoreCase(title)) {
                return Response.status(409)
                    .entity(Map.of("error", "\"" + title + "\" is already in your library"))
                    .build();
            }
        }

        // Ensure unique ID
        String baseId = gameId;
        int counter = 2;
        while (Files.exists(svc.gameJsonPath(gameId))) {
            gameId = baseId + "-" + counter++;
        }

        // Extract ZIP
        Path tmpDir = Files.createTempDirectory("dostalgia-");
        try {
            Path zipPath = tmpDir.resolve(filename);
            Files.copy(upload.uploadedFile(), zipPath, StandardCopyOption.REPLACE_EXISTING);

            Path extractDir = tmpDir.resolve("extracted");
            zip.unzip(zipPath, extractDir);
            // If the ZIP has a single root directory, flatten it
            try {
                zip.flattenSingleDir(extractDir);
            } catch (Exception e) {
                LOG.warning("Flatten failed for '" + filename + "': " + e.getMessage()
                    + " — continuing (cd in autoexec handles subdirs)");
            }

            // Fix .cue files that reference non-existent data files (e.g. CloneCD)
            try {
                cdImageService.fixCueReferences(extractDir);
            } catch (Exception e) {
                LOG.warning("Failed to fix cue references: " + e.getMessage());
            }

            // Patch game config files with hardcoded absolute paths
            try {
                configPatcher.fixAbsolutePaths(extractDir);
            } catch (Exception e) {
                LOG.warning("Failed to patch config paths: " + e.getMessage());
            }

            // Find main executable
            String mainExe = exeDetector.findMain(extractDir);

            // Detect CD images
            List<String> cdImages = cdImageService.findInDirectory(extractDir);

            if (mainExe == null) {
                if (cdImages.isEmpty()) {
                    return Response.status(400)
                        .entity(Map.of("error", "No executable (.exe/.com/.bat) found in the archive"))
                        .build();
                }
                // CD-only game — proceed without executable
            }

            // Find setup executable
            String setupExe = exeDetector.findSetup(extractDir);

            // Create game directory and .jsdos bundle
            Path gameDir = svc.gameDir(gameId);
            Files.createDirectories(gameDir);

            String bundleFile = gameId + "/" + gameId + ".jsdos";
            Path bundlePath = gameDir.resolve(gameId + ".jsdos");
            zip.createBundle(extractDir, mainExe, cdImages, bundlePath);

            // Detect platform (DOS vs Windows)
            Path mainExePath = mainExe != null ? Path.of(mainExe) : null;
            String platformType = mainExe != null ? platform.detect(mainExePath) : "dos";

            // Save metadata
            Game game = new Game(gameId, title);
            game.setBundleFile(bundleFile);
            game.setPlatform(platformType);

            // Store the selected executable and the full list for the UI picker
            String relMain = mainExe != null
                ? extractDir.relativize(mainExePath).toString().replace('\\', '/')
                : "";
            game.setExecutable(relMain);
            game.setExecutables(exeDetector.findAll(extractDir));
            if (setupExe != null) {
                Path setupExePath = Path.of(setupExe);
                String setupPlatform = platform.detect(setupExePath);
                if (!"windows".equals(setupPlatform)) {
                    game.setSetupExe(extractDir.relativize(setupExePath).toString());
                }
            }
            game.setReady(true);
            svc.save(game);

            // Auto-populate from IGDB (use provided igdb_id or auto-search)
            if (igdb.isConfigured()) {
                if (igdbId != null) {
                    igdb.applyIgdbId(game, igdbId);
                } else {
                    igdb.autoScrape(game);
                }
                if (game.isHasCover() || game.getYear() != null || game.getGenre() != null
                    || (game.getVideos() != null && !game.getVideos().isEmpty())
                    || (game.getScreenshots() != null && !game.getScreenshots().isEmpty())) {
                    svc.save(game);
                }
            }

            return Response.status(201).entity(game).build();

        } catch (Exception e) {
            LOG.severe("Upload failed for '" + filename + "': " + e.getMessage());
            return Response.serverError()
                .entity(Map.of("error", "Upload failed: " + e.getMessage()))
                .build();
        } finally {
            deleteDir(tmpDir);
        }
    }

    @POST
    @jakarta.ws.rs.Path("/{id}/cover")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadCover(@PathParam("id") String id, @RestForm("file") FileUpload upload) throws Exception {
        if (upload == null) {
            return Response.status(400).entity(Map.of("error", "No file provided")).build();
        }
        if (!Files.exists(svc.gameJsonPath(id))) {
            return Response.status(404).entity(Map.of("error", "Game not found")).build();
        }

        String fname = upload.fileName().toLowerCase();
        String ext;
        if (fname.endsWith(".jpg") || fname.endsWith(".jpeg")) ext = ".jpg";
        else if (fname.endsWith(".png")) ext = ".png";
        else if (fname.endsWith(".webp")) ext = ".webp";
        else {
            return Response.status(400).entity(Map.of("error", "Only JPG, PNG, or WebP images accepted")).build();
        }

        Path gameDir = svc.gameDir(id);
        // Remove old covers
        for (String old : new String[]{".jpg", ".jpeg", ".png", ".webp"}) {
            Files.deleteIfExists(gameDir.resolve("cover" + old));
        }

        Files.copy(upload.uploadedFile(), gameDir.resolve("cover" + ext), StandardCopyOption.REPLACE_EXISTING);
        return Response.ok(Map.of("status", "ok")).build();
    }

    private void deleteDir(Path dir) throws IOException {
        FileUtils.deleteDirectory(dir);
    }
}
