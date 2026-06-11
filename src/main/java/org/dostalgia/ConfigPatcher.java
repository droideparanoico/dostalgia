package org.dostalgia;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Detects and fixes hardcoded absolute DOS paths in game config files.
 * Handles the common case where game archives contain paths like
 * C:\\FALLOUT1\\MASTER.DAT that break after directory flattening.
 */
@ApplicationScoped
public class ConfigPatcher {

    private static final Logger LOG = Logger.getLogger(ConfigPatcher.class.getName());
    private static final Pattern DRIVE_PATH = Pattern.compile("[A-Za-z]:([\\\\/])[^\\s\"\\r\\n]+");

    /** Scan and fix absolute paths in all text files in the given directory. */
    public void fixAbsolutePaths(Path extractDir) throws IOException {
        try (var walk = Files.walk(extractDir)) {
            walk.filter(Files::isRegularFile)
                .filter(f -> {
                    try { return isSmallTextFile(f); }
                    catch (IOException e) { return false; }
                })
                .forEach(f -> {
                    try {
                        String text = Files.readString(f, StandardCharsets.ISO_8859_1);
                        String patched = patchText(text, extractDir);
                        if (!patched.equals(text)) {
                            Files.writeString(f, patched, StandardCharsets.ISO_8859_1);
                            LOG.info("Patched absolute paths in " + f.getFileName());
                        }
                    } catch (IOException e) {
                        LOG.warning("Failed to patch " + f.getFileName() + ": " + e.getMessage());
                    }
                });
        }
    }

    private static boolean isSmallTextFile(Path f) throws IOException {
        long size = Files.size(f);
        if (size > 100 * 1024 || size == 0) return false;
        try (var is = Files.newInputStream(f)) {
            byte[] head = new byte[(int) Math.min(size, 4096)];
            int read = is.readNBytes(head, 0, head.length);
            for (int i = 0; i < read; i++) {
                if (head[i] == 0) return false;
            }
        }
        return true;
    }

    private static String patchText(String text, Path extractDir) {
        var m = DRIVE_PATH.matcher(text);
        var sb = new StringBuilder();
        int lastEnd = 0;
        Set<String> seenPrefixes = new HashSet<>();

        while (m.find()) {
            int start = m.start();
            sb.append(text, lastEnd, start);

            String fullPath = m.group();
            String drive = fullPath.substring(0, 2);
            if (!"C:".equalsIgnoreCase(drive)) {
                sb.append(fullPath);
                lastEnd = m.end();
                continue;
            }

            String replacement = findReplacement(fullPath, extractDir, seenPrefixes);
            sb.append(replacement);
            lastEnd = m.end();
        }
        sb.append(text.substring(lastEnd));
        return sb.toString();
    }

    private static String findReplacement(String fullPath, Path extractDir, Set<String> seenPrefixes) {
        String drivePrefix = fullPath.substring(0, 2);
        char sep = fullPath.charAt(2);
        String splitRegex = sep == '\\' ? "\\\\" : "/";
        String relPath = fullPath.substring(3);
        String[] parts = relPath.split(splitRegex, -1);

        for (int skip = 0; skip < parts.length; skip++) {
            StringBuilder suffix = new StringBuilder();
            for (int j = skip; j < parts.length; j++) {
                if (j > skip) suffix.append(sep);
                suffix.append(parts[j]);
            }
            String suffixStr = suffix.toString();
            if (suffixStr.isEmpty()) continue;

            if (existsIgnoreCase(extractDir, suffixStr)) {
                if (skip == 0) return fullPath;

                StringBuilder stale = new StringBuilder(drivePrefix + sep);
                for (int k = 0; k < skip; k++) {
                    if (k > 0) stale.append(sep);
                    stale.append(parts[k]);
                }
                stale.append(sep);

                String staleStr = stale.toString();
                if (seenPrefixes.contains(staleStr)) {
                    return "." + sep + suffixStr;
                }
                seenPrefixes.add(staleStr);
                return "." + sep + suffixStr;
            }
        }
        return fullPath;
    }

    private static boolean existsIgnoreCase(Path base, String relativePath) {
        char sep = relativePath.contains("\\") ? '\\' : '/';
        String[] parts = relativePath.split(sep == '\\' ? "\\\\" : "/", -1);
        Path current = base;
        try {
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.isEmpty()) {
                    if (i == parts.length - 1) return Files.isDirectory(current);
                    continue;
                }
                Path found = null;
                try (var dirStream = Files.list(current)) {
                    for (Path entry : dirStream.toList()) {
                        if (entry.getFileName().toString().equalsIgnoreCase(part)) {
                            found = entry;
                            break;
                        }
                    }
                }
                if (found == null) return false;
                current = found;
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }
}
