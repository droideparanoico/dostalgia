package org.dostalgia;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;

/** IGDB metadata & artwork integration. Enabled when TWITCH_CLIENT_ID and TWITCH_CLIENT_SECRET are set. */
@Path("/api/igdb")
@Produces(MediaType.APPLICATION_JSON)
public class IgdbController {

    @Inject
    IgdbService svc;

    @Inject
    GameService games;

    @GET
    @Path("/search")
    public Response search(@QueryParam("q") @DefaultValue("") String query) {
        if (query.isBlank()) {
            return Response.status(400).entity(Map.of("error", "Query parameter 'q' is required")).build();
        }
        try {
            var results = svc.search(query);
            return Response.ok(Map.of("results", results)).build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(Map.of("error", "IGDB search failed: " + e.getMessage()))
                .build();
        }
    }

    /**
     * Apply IGDB metadata + cover to a game.
     * Body: {"igdb_id": 12345} — applies data from that specific IGDB game entry.
     * Body: {} — auto-search using the game's title.
     */
    @POST
    @Path("/scrape/{gameId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response scrape(@PathParam("gameId") String gameId, Map<String, Object> body) {
        try {
            var game = games.load(gameId);

            if (body != null && body.containsKey("igdb_id")) {
                int igdbId = ((Number) body.get("igdb_id")).intValue();
                svc.applyIgdbId(game, igdbId);
            } else {
                svc.autoScrape(game);
            }

            games.save(game);
            return Response.ok(game).build();

        } catch (java.nio.file.NoSuchFileException e) {
            return Response.status(404).entity(Map.of("error", "Game not found")).build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(Map.of("error", "IGDB scrape failed: " + e.getMessage()))
                .build();
        }
    }

    /** Check if IGDB credentials are configured. */
    @GET
    @Path("/status")
    public Response status() {
        boolean configured = svc.isConfigured();
        return Response.ok(Map.of(
            "configured", configured,
            "message", configured ? "IGDB ready" : "Set TWITCH_CLIENT_ID and TWITCH_CLIENT_SECRET env vars"
        )).build();
    }
}
