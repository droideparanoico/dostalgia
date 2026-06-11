package org.dostalgia;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZIP extraction, directory flattening, and .jsdos bundle creation.
 */
@ApplicationScoped
public class ZipService {

    private static final Logger LOG = Logger.getLogger(ZipService.class.getName());

    @Inject
    ConfigBuilder config;

    private static final Set<String> SKIP_EXT = Set.of(
        ".nrg", ".mdf", ".mds", ".sub", ".dmg"
    );

    /** Unzip an archive to a destination directory (path traversal safe). */
    public void unzip(Path zipPath, Path dest) throws IOException {
        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipPath))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = dest.resolve(entry.getName()).normalize();
                if (!target.startsWith(dest)) continue;
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * If the extraction directory contains a single subdirectory, move its contents up.
     */
    public void flattenSingleDir(Path dir) throws IOException {
        Path singleDir = null;
        try (var files = Files.list(dir)) {
            for (Path entry : files.toList()) {
                if (Files.isDirectory(entry)) {
                    if (singleDir != null) return;
                    singleDir = entry;
                }
            }
        }
        if (singleDir == null) return;

        LOG.info("Flattening root directory: " + singleDir.getFileName());
        try (var walk = Files.walk(singleDir)) {
            var list = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path f : list) {
                if (f.equals(singleDir)) continue;
                Path target = dir.resolve(singleDir.relativize(f));
                if (Files.isDirectory(f)) {
                    Files.createDirectories(target);
                    Files.delete(f);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.move(f, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Files.delete(singleDir);
        LOG.info("Flattened " + singleDir.getFileName());
    }

    /**
     * Create a .jsdos bundle ZIP from the extracted game directory.
     */
    public void createBundle(Path extractDir, String exePath, List<String> cdImages, Path bundlePath) throws IOException {
        Path jsdos = extractDir.resolve(".jsdos");
        Files.createDirectories(jsdos);
        if (exePath != null) {
            String relExe = extractDir.relativize(Path.of(exePath)).toString();
            Files.writeString(jsdos.resolve("dosbox.conf"), config.buildDosboxConf(relExe, cdImages));
            Files.write(jsdos.resolve("jsdos.json"), config.buildJsdosJson(relExe, cdImages));
        } else {
            Files.writeString(jsdos.resolve("dosbox.conf"), config.buildCdOnlyDosboxConf(cdImages));
            Files.write(jsdos.resolve("jsdos.json"), config.buildCdOnlyJsdosJson(cdImages));
        }

        try (var zos = new ZipOutputStream(Files.newOutputStream(bundlePath))) {

            // Phase 1: Collect all directory paths
            var allDirs = new TreeSet<String>();
            try (var walk = Files.walk(extractDir)) {
                walk.filter(Files::isRegularFile).forEach(f -> {
                    String entryName = extractDir.relativize(f).toString().replace('\\', '/');
                    int idx = entryName.lastIndexOf('/');
                    while (idx >= 0) {
                        allDirs.add(entryName.substring(0, idx + 1));
                        idx = entryName.lastIndexOf('/', idx - 1);
                    }
                });
            }

            // Phase 2: Write directory entries first
            for (String dir : allDirs) {
                zos.putNextEntry(new ZipEntry(dir));
                zos.closeEntry();
            }

            // Phase 3: .jsdos config files (before game files)
            try (var walk = Files.walk(jsdos)) {
                walk.filter(Files::isRegularFile).sorted()
                    .forEach(f -> zipEntry(zos, extractDir, f));
            }

            // Phase 4: Game files (excluding skipped extensions)
            try (var walk = Files.walk(extractDir)) {
                walk.filter(Files::isRegularFile)
                    .filter(f -> !f.startsWith(jsdos))
                    .filter(f -> {
                        String n = f.getFileName().toString().toLowerCase();
                        int dot = n.lastIndexOf('.');
                        return dot < 0 || !SKIP_EXT.contains(n.substring(dot));
                    })
                    .sorted()
                    .forEach(f -> zipEntry(zos, extractDir, f));
            }
        }

        // Clean up .jsdos config files from the extract dir
        try (var cleanup = Files.walk(jsdos)) {
            cleanup.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }

    /** Write a single file into a ZIP output stream, computing entry name relative to extractDir. */
    private static void zipEntry(ZipOutputStream zos, Path extractDir, Path file) {
        try {
            String entryName = extractDir.relativize(file).toString().replace('\\', '/');
            zos.putNextEntry(new ZipEntry(entryName));
            Files.copy(file, zos);
            zos.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
