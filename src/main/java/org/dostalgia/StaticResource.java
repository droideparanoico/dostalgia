package org.dostalgia;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Serves game bundles (.jsdos) and artwork from the external data directory. */
@jakarta.ws.rs.Path("/")
public class StaticResource {

    @ConfigProperty(name = "dostalgia.data.dir", defaultValue = "data")
    String dataDir;

    /** Serve a file from a subdirectory of the data dir with path traversal protection. */
    private Response serveFile(Path base, String path, String cacheHeader) {
        Path file = base.resolve(path).normalize();
        if (!file.startsWith(base)) {
            return Response.status(403).build();
        }
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return Response.status(404).build();
        }
        String contentType = guessContentType(file.getFileName().toString());
        var builder = Response.ok(new FileStream(file)).type(contentType);
        if (cacheHeader != null) {
            builder.header(cacheHeader.split(":")[0], cacheHeader.split(":", 2)[1].trim());
        }
        return builder.build();
    }

    @GET
    @jakarta.ws.rs.Path("/games/{path: .*}")
    public Response getGameFile(@PathParam("path") String path) {
        return serveFile(Path.of(dataDir, "games"), path, "Accept-Ranges: bytes");
    }

    @GET
    @jakarta.ws.rs.Path("/artwork/{path: .*}")
    public Response getArtwork(@PathParam("path") String path) {
        return serveFile(Path.of(dataDir, "artwork"), path, "Cache-Control: public, max-age=86400");
    }

    /** Health check */
    @GET
    @jakarta.ws.rs.Path("/api/health")
    public Response health() {
        return Response.ok(Map.of("status", "ok", "version", "0.1.0")).build();
    }

    private String guessContentType(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".jsdos")) return "application/zip";
        if (n.endsWith(".zip")) return "application/zip";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".json")) return "application/json";
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    /** Streaming file output that avoids loading the whole file into memory. */
    private record FileStream(Path file) implements StreamingOutput {
        @Override
        public void write(OutputStream output) throws IOException {
            Files.copy(file, output);
        }
    }
}
