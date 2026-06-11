package org.dostalgia;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

/**
 * Detects platform type (DOS vs Windows) by reading executable headers.
 */
@ApplicationScoped
public class PlatformDetector {

    private static final Logger LOG = Logger.getLogger(PlatformDetector.class.getName());

    public String detect(Path exePath) {
        String name = exePath.getFileName().toString().toLowerCase();
        if (name.endsWith(".com") || name.endsWith(".bat")) return "dos";

        try (var fis = Files.newInputStream(exePath)) {
            return detectFromBytes(readHeaderBytes(fis));
        } catch (IOException e) {
            LOG.warning("Could not read EXE header for " + exePath + ": " + e.getMessage());
            return null;
        }
    }

    /** Read enough PE header bytes from any InputStream (shared by Path and ZIP callers). */
    static byte[] readHeaderBytes(InputStream is) throws IOException {
        byte[] buf = new byte[0x1000];
        int read = is.readNBytes(buf, 0, buf.length);
        return read < buf.length ? java.util.Arrays.copyOf(buf, read) : buf;
    }

    /** Detect platform from pre-read MZ/PE header bytes. */
    static String detectFromBytes(byte[] buf) {
        return detectFromBytes(buf, buf.length);
    }

    /** Detect platform from pre-read MZ/PE header bytes with explicit read length. */
    static String detectFromBytes(byte[] buf, int read) {
        if (read < 2) return null;
        if (buf[0] != 0x4D || buf[1] != 0x5A) return null;
        if (read < 0x40) return "dos";

        int peOffset = (buf[0x3C] & 0xFF)
                     | ((buf[0x3D] & 0xFF) << 8)
                     | ((buf[0x3E] & 0xFF) << 16)
                     | ((buf[0x3F] & 0xFF) << 24);

        if (peOffset < 0 || peOffset + 2 > read) return "dos";

        byte sig1 = buf[peOffset];
        byte sig2 = buf[peOffset + 1];

        if ((sig1 == 0x50 && sig2 == 0x45)   // PE
         || (sig1 == 0x4E && sig2 == 0x45))  // NE
            return "windows";

        return "dos";
    }
}
