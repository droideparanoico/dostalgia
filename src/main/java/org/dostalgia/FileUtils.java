package org.dostalgia;

import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Shared file-system utilities. */
public final class FileUtils {
    private FileUtils() {}

    /** Recursively delete a directory tree (walk in reverse order so dirs are empty when deleted). */
    public static void deleteDirectory(final Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public @Nonnull FileVisitResult visitFile(@Nonnull final Path f, @Nonnull final BasicFileAttributes a) throws IOException {
                Files.delete(f);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @Nonnull FileVisitResult postVisitDirectory(@Nonnull final Path d, final IOException e) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Walk a directory tree and collect relative paths of files whose extension matches
     * one of the given extensions. Extensions should include the dot, e.g. {@code ".exe"}.
     * Results are sorted alphabetically.
     */
    public static List<String> findFilesByExtensions(Path dir, Set<String> extensions) throws IOException {
        List<String> result = new ArrayList<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public @Nonnull FileVisitResult visitFile(@Nonnull Path f, @Nonnull BasicFileAttributes a) {
                String name = f.getFileName().toString().toLowerCase();
                for (String ext : extensions) {
                    if (name.endsWith(ext)) {
                        result.add(dir.relativize(f).toString().replace('\\', '/'));
                        break;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        result.sort(String::compareTo);
        return result;
    }
}
