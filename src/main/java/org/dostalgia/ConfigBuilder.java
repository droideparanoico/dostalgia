package org.dostalgia;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single source of truth for all DOSBox configuration generation.
 */
@ApplicationScoped
public class ConfigBuilder {

    /**
     * Build a dosbox.conf for a game with a known executable.
     * @param relExe relative path to the executable inside the ZIP (e.g. "FALLOUT.EXE" or "FALLOUT/FALLOUT.EXE")
     * @param cdImages relative paths to CD image files inside the ZIP
     */
    public String buildDosboxConf(String relExe, List<String> cdImages) {
        var parts = splitExePath(relExe);
        String dir = parts.dir();
        String exe = parts.exe();

        return DOSBOX_CONF_TEMPLATE
            .replace("${CD_MOUNTS}", buildCdMounts(cdImages))
            .replace("${EXEC_PATH}", buildExecPath(dir, exe));
    }

    /** Build dosbox.conf as UTF-8 bytes (for ZIP stream operations). */
    public byte[] buildDosboxConfBytes(String relExe, List<String> cdImages) {
        return buildDosboxConf(relExe, cdImages).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Build jsdos.json for js-dos v8 with the given executable path.
     * js-dos v8 uses jsdos.json autoexec.script instead of the dosbox.conf [autoexec] section.
     */
    public byte[] buildJsdosJson(String relExe, List<String> cdImages) {
        var parts = splitExePath(relExe);
        String script = buildAutoexecScript(parts.dir(), parts.exe(), cdImages);
        return buildJsdosJsonRaw(script);
    }

    /** Build dosbox.conf for a CD-only game (no filesystem executables). */
    public String buildCdOnlyDosboxConf(List<String> cdImages) {
        return CD_ONLY_DOSBOX_CONF_TEMPLATE
            .replace("${CD_MOUNTS}", buildCdMounts(cdImages));
    }

    /** Build jsdos.json for a CD-only game. */
    public byte[] buildCdOnlyJsdosJson(List<String> cdImages) {
        String script = buildCdMountScript(cdImages) + "c:\n";
        return buildJsdosJsonRaw(script);
    }

    /** Split "dir/file.exe" into (dir, exe). Returns (null, file) if no directory separator. */
    public static ExeParts splitExePath(String relExe) {
        if (relExe == null || relExe.isBlank()) {
            return new ExeParts(null, "");
        }
        int idx = relExe.replace('\\', '/').lastIndexOf('/');
        if (idx >= 0) {
            return new ExeParts(relExe.substring(0, idx), relExe.substring(idx + 1));
        }
        return new ExeParts(null, relExe);
    }

    /** Escape a string for embedding inside a JSON string value. */
    public static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record ExeParts(String dir, String exe) {}

    private static final String DOSBOX_CONF_TEMPLATE = """
        [sdl]
        autolock=true
        usescancodes=true
        output=surface

        [cpu]
        core=auto
        cycles=auto

        [mixer]
        nosound=false
        rate=44100

        [sblaster]
        sbtype=sb16
        irq=7
        dma=1
        hdma=5

        [dos]
        xms=true
        ems=true
        umb=true

        [dosbox]
        memsize=64

        [autoexec]
        @echo off
        mount c .
        ${CD_MOUNTS}c:
        ${EXEC_PATH}
        """;

    private static final String CD_ONLY_DOSBOX_CONF_TEMPLATE = """
        [sdl]
        autolock=true
        usescancodes=true
        output=surface

        [cpu]
        core=auto
        cycles=auto

        [mixer]
        nosound=false
        rate=44100

        [sblaster]
        sbtype=sb16
        irq=7
        dma=1
        hdma=5

        [dos]
        xms=true
        ems=true
        umb=true

        [dosbox]
        memsize=64

        [autoexec]
        @echo off
        mount c .
        ${CD_MOUNTS}c:
        echo.
        echo This game runs from the CD-ROM.
        echo Type D: and press Enter, then look for the game executable (e.g. DOTT.EXE).
        """;

    /**
     * Build the CD mount lines for the autoexec section.
     * Mounts ALL CD images, deduplicating by parent directory.
     * Skips descriptor formats (.cue, .ccd) when a data format
     * (.img, .iso, .bin) exists in the same directory.
     */
    private static String buildCdMounts(List<String> cdImages) {
        if (cdImages == null || cdImages.isEmpty()) return "";
        // Deduplicate by parent directory — prefer data formats over descriptors
        Set<String> seenDirs = new HashSet<>();
        StringBuilder sb = new StringBuilder();
        char driveLetter = 'D';
        for (String img : cdImages) {
            int slashIdx = img.lastIndexOf('/');
            String parentDir = slashIdx >= 0 ? img.substring(0, slashIdx) : "";
            if (seenDirs.contains(parentDir)) continue;
            String imgLower = img.toLowerCase();
            // Skip descriptor formats if a data format exists in the same directory
            if (imgLower.endsWith(".cue") || imgLower.endsWith(".ccd")) {
                boolean hasData = cdImages.stream().anyMatch(i -> {
                    int is = i.lastIndexOf('/');
                    String ip = is >= 0 ? i.substring(0, is) : "";
                    String il = i.toLowerCase();
                    return ip.equals(parentDir)
                        && (il.endsWith(".img") || il.endsWith(".iso") || il.endsWith(".bin"));
                });
                if (hasData) continue;
            }
            seenDirs.add(parentDir);
            String flags = imgLower.endsWith(".bin") ? " -t cdrom -fs iso" : " -t cdrom";
            sb.append("imgmount ").append(driveLetter).append(" \"")
              .append(img).append("\"").append(flags).append("\n");
            driveLetter++;
        }
        return sb.toString();
    }

    private static String buildExecPath(String dir, String exe) {
        if (dir == null) return exe + "\n";
        return "path=c:\\\ncd " + dir + "\n" + exe + "\n";
    }

    private static String buildCdMountScript(List<String> cdImages) {
        StringBuilder script = new StringBuilder();
        script.append("mount c .\n");
        char driveLetter = 'D';
        for (String img : cdImages) {
            String imgLower = img.toLowerCase();
            String flags = imgLower.endsWith(".bin") && !imgLower.endsWith(".cue")
                ? " -t cdrom -fs iso" : " -t cdrom";
            script.append("imgmount ").append(driveLetter).append(" \"")
                  .append(img).append("\"").append(flags).append("\n");
            driveLetter++;
        }
        return script.toString();
    }

    private static String buildAutoexecScript(String dir, String exe, List<String> cdImages) {
        StringBuilder script = new StringBuilder(buildCdMountScript(cdImages));
        script.append("c:\n");
        if (dir != null) {
            script.append("path=c:\\\n");
            script.append("cd ").append(dir).append("\n");
        }
        script.append(exe);
        return script.toString();
    }

    private static byte[] buildJsdosJsonRaw(String script) {
        String json = "{\"autoexec\":{\"options\":{\"script\":{\"value\":\""
            + escapeJson(script) + "\"}}},"
            + "\"output\":{\"options\":{\"autolock\":{\"value\":true}}}}";
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
