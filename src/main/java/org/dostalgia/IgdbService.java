package org.dostalgia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** Client for the IGDB (Internet Game Database) API via Twitch OAuth2. */
@ApplicationScoped
public class IgdbService {

    private static final Logger LOG = Logger.getLogger(IgdbService.class.getName());
    private static final String TWITCH_AUTH = "https://id.twitch.tv/oauth2/token";
    private static final String IGDB_API = "https://api.igdb.com/v4";
    private static final String IMG_BASE = "https://images.igdb.com/igdb/image/upload";
    private static final String COVER_SIZE = "t_cover_big";

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @ConfigProperty(name = "TWITCH_CLIENT_ID")
    Optional<String> clientId;

    @ConfigProperty(name = "TWITCH_CLIENT_SECRET")
    Optional<String> clientSecret;

    @Inject
    GameStore gameStore;

    private String accessToken;
    private Instant tokenExpiry;

    public boolean isConfigured() {
        return clientId.isPresent() && clientSecret.isPresent()
            && !clientId.get().isBlank() && !clientSecret.get().isBlank();
    }

    private synchronized String getToken() throws Exception {
        if (accessToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
            return accessToken;
        }
        String cid = clientId.orElseThrow(() -> new RuntimeException("TWITCH_CLIENT_ID not configured"));
        String secret = clientSecret.orElseThrow(() -> new RuntimeException("TWITCH_CLIENT_SECRET not configured"));
        String body = "client_id=" + cid
            + "&client_secret=" + secret
            + "&grant_type=client_credentials";

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(TWITCH_AUTH))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new RuntimeException("Twitch OAuth2 failed: HTTP " + res.statusCode() + " " + res.body());
        }

        JsonNode json = mapper.readTree(res.body());
        accessToken = json.get("access_token").asText();
        int expiresIn = json.get("expires_in").asInt();
        tokenExpiry = Instant.now().plusSeconds(expiresIn - 120);
        LOG.info("IGDB: acquired new Twitch token (expires in " + expiresIn + "s)");
        return accessToken;
    }

    private HttpRequest.Builder igdbRequest(String path) throws Exception {
        String cid = clientId.orElseThrow(() -> new RuntimeException("TWITCH_CLIENT_ID not configured"));
        return HttpRequest.newBuilder()
            .uri(URI.create(IGDB_API + path))
            .header("Client-ID", cid)
            .header("Authorization", "Bearer " + getToken())
            .header("Content-Type", "text/plain");
    }

    /** POST an APQL query to an IGDB endpoint and return the JSON array, or null on failure. */
    private JsonNode postAndGet(String endpoint, String apql) throws Exception {
        HttpRequest req = igdbRequest(endpoint)
            .POST(HttpRequest.BodyPublishers.ofString(apql))
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;
        JsonNode results = mapper.readTree(res.body());
        return results.isArray() ? results : null;
    }

    public List<Map<String, Object>> search(String query) throws Exception {
        if (!isConfigured()) return List.of();

        String apql = "search \"" + sanitize(query) + "\";"
            + " fields name,first_release_date,genres.name,"
            + " involved_companies.company.name,involved_companies.developer,involved_companies.publisher,"
            + " summary,cover.url,platforms;"
            + " where platforms = [13];"
            + " limit 20;";

        JsonNode results = postAndGet("/games", apql);
        if (results == null || results.isEmpty()) {
            LOG.info("IGDB search: no results for '" + query + "'");
            return List.of();
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode game : results) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("igdb_id", game.get("id").asInt());
            entry.put("name", game.has("name") ? game.get("name").asText() : query);

            epochToYear(game).ifPresent(y -> entry.put("year", y));

            List<String> genres = extractGenres(game);
            if (!genres.isEmpty()) {
                entry.put("genres", genres);
            }

            if (game.has("involved_companies") && game.get("involved_companies").isArray()) {
                String developer = null;
                String publisher = null;
                for (JsonNode ic : game.get("involved_companies")) {
                    if (ic.has("company") && ic.get("company").has("name")) {
                        String companyName = ic.get("company").get("name").asText();
                        boolean isDev = ic.has("developer") && ic.get("developer").asBoolean();
                        boolean isPub = ic.has("publisher") && ic.get("publisher").asBoolean();
                        if (isDev) developer = companyName;
                        if (isPub) publisher = companyName;
                    }
                }
                if (developer != null) entry.put("developer", developer);
                if (publisher != null) entry.put("publisher", publisher);
            }

            if (game.has("summary")) {
                entry.put("summary", game.get("summary").asText());
            }

            if (game.has("platforms") && game.get("platforms").isArray()) {
                List<Integer> platformIds = new ArrayList<>();
                for (JsonNode p : game.get("platforms")) {
                    platformIds.add(p.asInt());
                }
                entry.put("platform_ids", platformIds);
                entry.put("is_dos", platformIds.contains(13));
            }

            if (game.has("cover") && game.get("cover").has("url")) {
                String thumbUrl = game.get("cover").get("url").asText();
                String coverUrl = "https:" + thumbUrl.replace("t_thumb", COVER_SIZE);
                entry.put("cover_url", coverUrl);
            } else if (game.has("cover") && game.get("cover").isInt()) {
                int coverId = game.get("cover").asInt();
                String coverUrl = fetchCoverUrl(coverId);
                if (coverUrl != null) entry.put("cover_url", coverUrl);
            }

            out.add(entry);
        }
        return out;
    }

    private String fetchCoverUrl(int coverId) throws Exception {
        JsonNode results = postAndGet("/covers", "fields url; where id = " + coverId + ";");
        if (results == null || results.isEmpty()) return null;
        String thumbUrl = results.get(0).get("url").asText();
        return IMG_BASE + "/" + COVER_SIZE + "/" + thumbUrl.substring(thumbUrl.lastIndexOf('/') + 1);
    }

    private List<String> fetchVideos(int igdbId) throws Exception {
        JsonNode results = postAndGet("/game_videos", "fields video_id; where game = " + igdbId + ";");
        if (results == null) return List.of();
        List<String> videos = new ArrayList<>();
        for (JsonNode v : results) {
            if (v.has("video_id")) videos.add(v.get("video_id").asText());
        }
        return videos;
    }

    private List<String> fetchScreenshots(int igdbId) throws Exception {
        JsonNode results = postAndGet("/screenshots", "fields url; where game = " + igdbId + ";");
        if (results == null) return List.of();
        List<String> screenshots = new ArrayList<>();
        for (JsonNode s : results) {
            if (s.has("url")) {
                String fullUrl = "https:" + s.get("url").asText().replace("t_thumb", "t_1080p");
                screenshots.add(fullUrl);
            }
        }
        return screenshots;
    }

    public void autoScrape(Game game) {
        if (!isConfigured()) return;
        try {
            List<Map<String, Object>> results = search(game.getTitle());
            if (results.isEmpty()) {
                LOG.info("IGDB auto-scrape: no results for '" + game.getTitle() + "'");
                return;
            }
            Map<String, Object> best = null;
            for (Map<String, Object> r : results) {
                if (Boolean.TRUE.equals(r.get("is_dos"))) { best = r; break; }
            }
            if (best == null) best = results.getFirst();
            applyMatch(game, best);
            LOG.info("IGDB auto-scrape: applied '" + best.get("name") + "' to '" + game.getTitle() + "'");
            Object igdbIdObj = best.get("igdb_id");
            if (igdbIdObj instanceof Number igdbIdNum) {
                int igdbId = igdbIdNum.intValue();
                try { List<String> v = fetchVideos(igdbId); if (!v.isEmpty()) game.setVideos(v); }
                catch (Exception e) { LOG.warning("IGDB: failed to fetch videos: " + e.getMessage()); }
                try { List<String> s = fetchScreenshots(igdbId); if (!s.isEmpty()) { game.setScreenshots(s); game.setHasScreenshots(true); } }
                catch (Exception e) { LOG.warning("IGDB: failed to fetch screenshots: " + e.getMessage()); }
            }
        } catch (Exception e) {
            LOG.warning("IGDB auto-scrape failed for '" + game.getTitle() + "': " + e.getMessage());
        }
    }

    public void applyIgdbId(Game game, int igdbId) throws Exception {
        String apql = "fields name,first_release_date,genres.name,"
            + " involved_companies.company.name,involved_companies.developer,involved_companies.publisher,"
            + " summary,cover.url;"
            + " where id = " + igdbId + ";";
        JsonNode results = postAndGet("/games", apql);
        if (results == null || results.isEmpty()) {
            throw new RuntimeException("IGDB: no game found with ID " + igdbId);
        }
        applyMatch(game, results.get(0));
        try { List<String> v = fetchVideos(igdbId); if (!v.isEmpty()) game.setVideos(v); }
        catch (Exception e) { LOG.warning("IGDB: failed to fetch videos for game " + igdbId + ": " + e.getMessage()); }
        try { List<String> s = fetchScreenshots(igdbId); if (!s.isEmpty()) { game.setScreenshots(s); game.setHasScreenshots(true); } }
        catch (Exception e) { LOG.warning("IGDB: failed to fetch screenshots for game " + igdbId + ": " + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    private void applyMatch(Game game, Object matchData) throws Exception {
        Map<String, Object> data;
        if (matchData instanceof Map) {
            data = (Map<String, Object>) matchData;
        } else if (matchData instanceof JsonNode node) {
            data = jsonNodeToMap(node);
        } else {
            return;
        }
        if (data.containsKey("name") && (game.getTitle() == null || game.getTitle().isBlank()))
            game.setTitle((String) data.get("name"));
        if (data.containsKey("year")) game.setYear((Integer) data.get("year"));
        if (data.containsKey("developer")) game.setDeveloper((String) data.get("developer"));
        if (data.containsKey("publisher")) game.setPublisher((String) data.get("publisher"));
        if (data.containsKey("summary")) game.setDescription((String) data.get("summary"));
        if (data.containsKey("genres")) {
            List<String> genres = (List<String>) data.get("genres");
            if (!genres.isEmpty()) game.setGenre(genres.getFirst());
        }
        if (data.containsKey("igdb_id")) game.setIgdbId((Integer) data.get("igdb_id"));
        String coverUrl = (String) data.get("cover_url");
        if (coverUrl != null && !coverUrl.isBlank()) downloadCover(game, coverUrl);
    }

    private void downloadCover(Game game, String coverUrl) throws Exception {
        String url = coverUrl.startsWith("http") ? coverUrl : "https:" + coverUrl;
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<Path> res = http.send(req, HttpResponse.BodyHandlers.ofFile(
            gameStore.gameDir(game.getId()).resolve("cover.jpg")));
        if (res.statusCode() == 200) {
            game.setHasCover(true);
            LOG.info("IGDB: cover downloaded for '" + game.getTitle() + "'");
        } else {
            LOG.warning("IGDB: cover download failed with HTTP " + res.statusCode());
        }
    }

    /** Convert IGDB epoch (seconds) to year, from 'first_release_date' field. */
    private static Optional<Integer> epochToYear(JsonNode node) {
        if (node.has("first_release_date")) {
            long epoch = node.get("first_release_date").asLong();
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(epoch * 1000);
            return Optional.of(cal.get(Calendar.YEAR));
        }
        return Optional.empty();
    }

    /** Extract genre names from a JsonNode that may have a 'genres' array. */
    private static List<String> extractGenres(JsonNode node) {
        List<String> genres = new ArrayList<>();
        if (node.has("genres") && node.get("genres").isArray()) {
            for (JsonNode g : node.get("genres")) {
                if (g.has("name")) genres.add(g.get("name").asText());
            }
        }
        return genres;
    }

    private String sanitize(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Map<String, Object> jsonNodeToMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("igdb_id", node.has("id") ? node.get("id").asInt() : 0);
        map.put("name", node.has("name") ? node.get("name").asText() : "");
        epochToYear(node).ifPresent(y -> map.put("year", y));

        List<String> genres = extractGenres(node);
        if (!genres.isEmpty()) {
            map.put("genres", genres);
        }
        if (node.has("involved_companies") && node.get("involved_companies").isArray()) {
            for (JsonNode ic : node.get("involved_companies")) {
                if (ic.has("company") && ic.get("company").has("name")) {
                    String name = ic.get("company").get("name").asText();
                    boolean isDev = ic.has("developer") && ic.get("developer").asBoolean();
                    boolean isPub = ic.has("publisher") && ic.get("publisher").asBoolean();
                    if (isDev && !map.containsKey("developer")) map.put("developer", name);
                    if (isPub && !map.containsKey("publisher")) map.put("publisher", name);
                }
            }
        }
        if (node.has("summary")) map.put("summary", node.get("summary").asText());
        if (node.has("cover")) {
            JsonNode cover = node.get("cover");
            try {
                if (cover.has("url")) {
                    String coverUrl = "https:" + cover.get("url").asText().replace("t_thumb", COVER_SIZE);
                    map.put("cover_url", coverUrl);
                } else if (cover.isInt() || cover.isLong()) {
                    String coverUrl = fetchCoverUrl(cover.asInt());
                    if (coverUrl != null) map.put("cover_url", coverUrl);
                } else if (cover.has("id")) {
                    String coverUrl = fetchCoverUrl(cover.get("id").asInt());
                    if (coverUrl != null) map.put("cover_url", coverUrl);
                }
            } catch (Exception ignored) {}
        }
        return map;
    }
}
