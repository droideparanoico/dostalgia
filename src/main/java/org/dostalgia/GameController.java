package org.dostalgia;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.nio.file.NoSuchFileException;
import java.nio.file.Files;
import java.util.Map;

@Path("/api/games")
@Produces(MediaType.APPLICATION_JSON)
public class GameController {

    @Inject
    GameService svc;

    @GET
    public Response list(
        @QueryParam("search") String search,
        @QueryParam("genre") String genre,
        @QueryParam("year") Integer year,
        @QueryParam("limit") @DefaultValue("50") int limit,
        @QueryParam("offset") @DefaultValue("0") int offset
    ) {
        try {
            var all = svc.list();

            if (search != null && !search.isBlank()) {
                all = all.stream()
                    .filter(g -> g.getTitle().toLowerCase().contains(search.toLowerCase()))
                    .toList();
            }
            if (genre != null && !genre.isBlank()) {
                all = all.stream()
                    .filter(g -> genre.equalsIgnoreCase(g.getGenre()))
                    .toList();
            }
            if (year != null) {
                all = all.stream()
                    .filter(g -> year.equals(g.getYear()))
                    .toList();
            }

            int total = all.size();
            int from = Math.min(offset, total);
            int to = Math.min(offset + limit, total);
            var page = all.subList(from, to);

            return Response.ok(Map.of("games", page, "total", total)).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        try {
            var game = svc.load(id);
            return Response.ok(game).build();
        } catch (NoSuchFileException e) {
            return Response.status(404).entity(Map.of("error", "Game not found")).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PATCH
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("id") String id, Map<String, Object> updates) {
        try {
            var game = svc.load(id);

            if (updates.containsKey("title")) game.setTitle((String) updates.get("title"));
            if (updates.containsKey("year")) game.setYear(asInt(updates.get("year")));
            if (updates.containsKey("genre")) game.setGenre((String) updates.get("genre"));
            if (updates.containsKey("developer")) game.setDeveloper((String) updates.get("developer"));
            if (updates.containsKey("publisher")) game.setPublisher((String) updates.get("publisher"));
            if (updates.containsKey("description")) game.setDescription((String) updates.get("description"));
            if (updates.containsKey("rating")) game.setRating(asDouble(updates.get("rating")));
            if (updates.containsKey("platform")) game.setPlatform((String) updates.get("platform"));

            // Changing the executable requires patching the .jsdos bundle
            if (updates.containsKey("executable")) {
                String newExe = (String) updates.get("executable");
                if (newExe != null && !newExe.isBlank()) {
                    svc.setExecutable(id, newExe);
                }
                // Reload after bundle patch to get updated metadata
                game = svc.load(id);
            } else {
                svc.save(game);
            }
            return Response.ok(game).build();
        } catch (NoSuchFileException e) {
            return Response.status(404).entity(Map.of("error", "Game not found")).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    /**
     * Stream a setup .jsdos bundle on-the-fly. No disk duplication —
     * reads the main bundle and patches config files to point at the
     * setup executable (SETUP.EXE, INSTALL.EXE, etc.).
     */
    @GET
    @Path("/{id}/setup-bundle")
    @Produces("application/zip")
    public Response setupBundle(@PathParam("id") String id) {
        try {
            var game = svc.load(id);
            if (game.getSetupExe() == null || game.getSetupExe().isBlank()) {
                return Response.status(404)
                    .entity(Map.of("error", "No setup executable configured for this game"))
                    .build();
            }
            String filename = (game.getTitle() != null ? game.getTitle() : id) + ".setup.jsdos";
            StreamingOutput stream = output -> svc.streamSetupBundle(id, output);
            return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
        } catch (NoSuchFileException e) {
            return Response.status(404).entity(Map.of("error", "Game not found")).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @GET
    @Path("/{id}/download")
    @Produces("application/zip")
    public Response download(@PathParam("id") String id) {
        try {
            var game = svc.load(id);
            String filename = (game.getTitle() != null ? game.getTitle() : id) + ".zip";
            var bundlePath = svc.getGamesDir().resolve(game.getBundleFile());
            if (!Files.exists(bundlePath)) {
                return Response.status(404).entity(Map.of("error", "Bundle file not found")).build();
            }
            StreamingOutput stream = output -> Files.copy(bundlePath, output);
            return Response.ok(stream)
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .build();
        } catch (NoSuchFileException e) {
            return Response.status(404).entity(Map.of("error", "Game not found")).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") String id) {
        try {
            svc.delete(id);
            return Response.ok(Map.of("status", "deleted")).build();
        } catch (Exception e) {
            return Response.serverError().entity(Map.of("error", e.getMessage())).build();
        }
    }

    private Integer asInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private Double asDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return null;
    }
}
