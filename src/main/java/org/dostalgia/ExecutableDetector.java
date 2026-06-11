package org.dostalgia;

import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Detects game executables within extracted archives.
 */
@ApplicationScoped
public class ExecutableDetector {

    @Inject
    PlatformDetector platform;

    private static final Set<String> SKIP_EXE_NAMES = Set.of(
        "dos4gw", "dos32a", "dos4gw2", "pmode", "pmodew", "cwsdpmi",
        "emm386", "himem", "himemx", "debug",
        "uninst", "uninstall", "uninstaller",
        "pksfx", "pkzip", "pkunzip", "sfx", "makesfx",
        "unzip", "zip", "zip2exe", "arj", "arj32",
        "lha", "lha32", "lzh", "rar", "unrar",
        "ace", "unace", "zoo", "arc", "ha", "cab"
    );

    public List<String> findAll(Path dir) throws IOException {
        return FileUtils.findFilesByExtensions(dir, Set.of(".exe", ".com", ".bat"));
    }

    public String findMain(Path dir) throws IOException {
        record Candidate(int depth, boolean isInstaller, long size, String path, String platform) {}
        List<Candidate> candidates = new ArrayList<>();

        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public @Nonnull FileVisitResult visitFile(@Nonnull Path f, @Nonnull BasicFileAttributes a) {
                String name = f.getFileName().toString().toLowerCase();
                if (!name.endsWith(".exe") && !name.endsWith(".com") && !name.endsWith(".bat"))
                    return FileVisitResult.CONTINUE;

                int dot = name.lastIndexOf('.');
                String stem = dot >= 0 ? name.substring(0, dot) : name;
                if (SKIP_EXE_NAMES.contains(stem))
                    return FileVisitResult.CONTINUE;

                if (isLikelySelfExtractor(f))
                    return FileVisitResult.CONTINUE;

                boolean installer = name.contains("install") || name.contains("setup") || name.contains("config");
                int depth = f.getNameCount() - dir.getNameCount();
                String detectedPlatform = platform.detect(f);
                candidates.add(new Candidate(depth, installer, a.size(), f.toString(), detectedPlatform));
                return FileVisitResult.CONTINUE;
            }
        });

        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator
            .comparingInt((Candidate c) -> c.isInstaller() ? 1 : 0)
            .thenComparingInt(c -> {
                if ("dos".equals(c.platform())) return 0;
                if (c.platform() == null) return 1;
                return 2;
            })
            .thenComparingLong(c -> -c.size())
            .thenComparingInt(Candidate::depth));

        return candidates.getFirst().path();
    }

    public String findSetup(Path dir) throws IOException {
        record Candidate(int depth, boolean isWindows, long size, String path) {}
        List<Candidate> candidates = new ArrayList<>();
        List<String> patterns = List.of("setup", "setmain", "install", "config", "custom");

        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public @Nonnull FileVisitResult visitFile(@Nonnull Path f, @Nonnull BasicFileAttributes a) {
                String name = f.getFileName().toString().toLowerCase();
                String stem = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
                if (!name.endsWith(".exe") && !name.endsWith(".com") && !name.endsWith(".bat"))
                    return FileVisitResult.CONTINUE;
                for (String pat : patterns) {
                    if (stem.equals(pat) || stem.startsWith(pat + ".") || stem.startsWith(pat + "-")) {
                        boolean isWin = "windows".equals(platform.detect(f));
                        candidates.add(new Candidate(
                            f.getNameCount() - dir.getNameCount(), isWin, a.size(), f.toString()));
                        break;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        if (candidates.isEmpty()) return null;
        candidates.sort(Comparator
            .comparingInt((Candidate c) -> c.isWindows() ? 1 : 0)  // prefer DOS over Windows
            .thenComparingInt(Candidate::depth)
            .thenComparingLong(Candidate::size));
        return candidates.getFirst().path();
    }

    public static boolean isLikelySelfExtractor(Path exePath) {
        try (var fis = Files.newInputStream(exePath)) {
            byte[] buf = new byte[32768];
            int read = fis.readNBytes(buf, 0, buf.length);
            if (read < 0x40) return false;
            if (buf[0] != 0x4D || buf[1] != 0x5A) return false;

            String content = new String(buf, 0, read, StandardCharsets.ISO_8859_1);
            if (content.contains("PKZIP") || content.contains("PKSFX")) return true;

            for (int i = 0x40; i < read - 4; i++) {
                if (buf[i] == 0x50 && (buf[i+1] & 0xFF) == 0x4B) {
                    int sig = (buf[i+2] & 0xFF) | ((buf[i+3] & 0xFF) << 8);
                    if (sig == 0x0403 || sig == 0x0201 || sig == 0x0605) return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
