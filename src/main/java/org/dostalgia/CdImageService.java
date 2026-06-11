package org.dostalgia;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * CD image utilities: CUE sheet repair and CD image discovery in directories and ZIPs.
 */
@ApplicationScoped
public class CdImageService {

    private static final Logger LOG = Logger.getLogger(CdImageService.class.getName());
    private static final java.util.Set<String> CD_EXT = java.util.Set.of(
        ".iso", ".cue", ".img", ".ccd", ".bin"
    );

    /**
     * Fix .cue files that reference non-existent data files.
     * CloneCD rips often generate a .cue referencing a .bin, but the actual file is .img.
     */
    public void fixCueReferences(Path extractDir) throws IOException {
        try (var walk = Files.walk(extractDir)) {
            walk.filter(Files::isRegularFile)
                .filter(f -> f.getFileName().toString().toLowerCase().endsWith(".cue"))
                .forEach(f -> {
                    try { fixOneCue(f); }
                    catch (IOException e) {
                        LOG.warning("Failed to fix cue file " + f + ": " + e.getMessage());
                    }
                });
        }
    }

    private void fixOneCue(Path cueFile) throws IOException {
        String content = Files.readString(cueFile);
        Path dir = cueFile.getParent();

        var m = java.util.regex.Pattern
            .compile("FILE\\s+\"([^\"]+)\"", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(content);
        if (!m.find()) return;

        String referenced = m.group(1);
        Path refPath = dir.resolve(referenced);
        if (Files.exists(refPath)) return;

        Path replacement = findDataFile(dir);
        if (replacement == null) return;

        String actualName = replacement.getFileName().toString();
        content = content.substring(0, m.start(1)) + actualName + content.substring(m.end(1));
        Files.writeString(cueFile, content);
        LOG.info("Fixed cue " + cueFile.getFileName() + ": '" + referenced + "' → '" + actualName + "'");
    }

    private Path findDataFile(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                .filter(f -> {
                    String n = f.getFileName().toString().toLowerCase();
                    return n.endsWith(".img") || n.endsWith(".bin");
                })
                .findFirst()
                .orElse(null);
        }
    }

    /** Find mountable CD image files in a directory. Returns sorted relative paths. */
    public java.util.List<String> findInDirectory(Path dir) throws IOException {
        java.util.List<String> cds = new java.util.ArrayList<>();
        try (var walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(f -> {
                String name = f.getFileName().toString().toLowerCase();
                int dot = name.lastIndexOf('.');
                if (dot >= 0 && CD_EXT.contains(name.substring(dot))) {
                    cds.add(dir.relativize(f).toString().replace('\\', '/'));
                }
            });
        }
        cds.sort(String::compareTo);
        return cds;
    }

    /** Find CD image files inside a .jsdos ZIP bundle. */
    public java.util.List<String> findInBundle(Path bundlePath) throws IOException {
        java.util.List<String> cds = new java.util.ArrayList<>();
        try (var zis = new java.util.zip.ZipInputStream(Files.newInputStream(bundlePath))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().toLowerCase();
                int dot = name.lastIndexOf('.');
                if (dot >= 0 && CD_EXT.contains(name.substring(dot))) {
                    cds.add(entry.getName());
                }
                zis.closeEntry();
            }
        }
        cds.sort(String::compareTo);
        return cds;
    }
}
