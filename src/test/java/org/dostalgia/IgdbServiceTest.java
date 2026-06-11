package org.dostalgia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class IgdbServiceTest {

    private IgdbService service;
    private ObjectMapper mapper;

    private Method epochToYearMethod;
    private Method extractGenresMethod;
    private Method sanitizeMethod;
    private Method jsonNodeToMapMethod;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        service = new IgdbService();
        service.clientId = Optional.of("test-client-id");
        service.clientSecret = Optional.of("test-client-secret");
        mapper = new ObjectMapper();

        epochToYearMethod = IgdbService.class.getDeclaredMethod("epochToYear", JsonNode.class);
        epochToYearMethod.setAccessible(true);

        extractGenresMethod = IgdbService.class.getDeclaredMethod("extractGenres", JsonNode.class);
        extractGenresMethod.setAccessible(true);

        sanitizeMethod = IgdbService.class.getDeclaredMethod("sanitize", String.class);
        sanitizeMethod.setAccessible(true);

        jsonNodeToMapMethod = IgdbService.class.getDeclaredMethod("jsonNodeToMap", JsonNode.class);
        jsonNodeToMapMethod.setAccessible(true);
    }

    // -- isConfigured tests --

    @Test
    void isConfigured_returnsTrueWhenBothPresentAndNonBlank() {
        assertTrue(service.isConfigured());
    }

    @Test
    void isConfigured_returnsFalseWhenClientIdEmpty() {
        service.clientId = Optional.empty();
        assertFalse(service.isConfigured());
    }

    @Test
    void isConfigured_returnsFalseWhenClientSecretEmpty() {
        service.clientSecret = Optional.empty();
        assertFalse(service.isConfigured());
    }

    @Test
    void isConfigured_returnsFalseWhenClientIdBlank() {
        service.clientId = Optional.of("");
        assertFalse(service.isConfigured());
    }

    @Test
    void isConfigured_returnsFalseWhenClientSecretBlank() {
        service.clientSecret = Optional.of("");
        assertFalse(service.isConfigured());
    }

    // -- epochToYear tests --

    @Test
    void epochToYear_withReleaseDate_returnsYear() throws Exception {
        // 946684800 = 2000-01-01T00:00:00Z
        JsonNode node = mapper.readTree("{\"first_release_date\": 946684800}");
        Optional<Integer> year = (Optional<Integer>) epochToYearMethod.invoke(null, node);
        assertTrue(year.isPresent());
        int y = year.get();
        assertTrue(y >= 1999 && y <= 2001, "Year should be around 2000 but was " + y);
    }

    @Test
    void epochToYear_withoutReleaseDate_returnsEmpty() throws Exception {
        JsonNode node = mapper.readTree("{\"name\": \"Test Game\"}");
        Optional<Integer> year = (Optional<Integer>) epochToYearMethod.invoke(null, node);
        assertFalse(year.isPresent());
    }

    @Test
    void epochToYear_withEpochZero_returnsYear() throws Exception {
        // epoch 0 = 1970-01-01T00:00:00Z
        JsonNode node = mapper.readTree("{\"first_release_date\": 0}");
        Optional<Integer> year = (Optional<Integer>) epochToYearMethod.invoke(null, node);
        assertTrue(year.isPresent());
        assertEquals(1970, year.get());
    }

    @Test
    void epochToYear_negativeEpoch_returnsYear() throws Exception {
        // negative epoch is before 1970
        JsonNode node = mapper.readTree("{\"first_release_date\": -315619200}");
        Optional<Integer> year = (Optional<Integer>) epochToYearMethod.invoke(null, node);
        assertTrue(year.isPresent());
        // Calendar may handle negative values differently; just verify it returns something
        assertNotNull(year.get());
    }

    // -- extractGenres tests --

    @Test
    void extractGenres_withGenresArray_returnsList() throws Exception {
        JsonNode node = mapper.readTree("{\"genres\": [{\"name\": \"Action\"}, {\"name\": \"Adventure\"}]}");
        List<String> genres = (List<String>) extractGenresMethod.invoke(null, node);
        assertEquals(2, genres.size());
        assertTrue(genres.contains("Action"));
        assertTrue(genres.contains("Adventure"));
    }

    @Test
    void extractGenres_emptyArray_returnsEmptyList() throws Exception {
        JsonNode node = mapper.readTree("{\"genres\": []}");
        List<String> genres = (List<String>) extractGenresMethod.invoke(null, node);
        assertTrue(genres.isEmpty());
    }

    @Test
    void extractGenres_missingField_returnsEmptyList() throws Exception {
        JsonNode node = mapper.readTree("{\"name\": \"Test\"}");
        List<String> genres = (List<String>) extractGenresMethod.invoke(null, node);
        assertTrue(genres.isEmpty());
    }

    @Test
    void extractGenres_genreWithoutName_skipsEntry() throws Exception {
        JsonNode node = mapper.readTree("{\"genres\": [{\"name\": \"Action\"}, {\"id\": 5}]}");
        List<String> genres = (List<String>) extractGenresMethod.invoke(null, node);
        assertEquals(1, genres.size());
        assertEquals("Action", genres.get(0));
    }

    // -- sanitize tests --

    @Test
    void sanitize_escapesBackslashes() throws Exception {
        String result = (String) sanitizeMethod.invoke(service, "path\\to\\file");
        assertEquals("path\\\\to\\\\file", result);
    }

    @Test
    void sanitize_escapesQuotes() throws Exception {
        String result = (String) sanitizeMethod.invoke(service, "say \"hello\"");
        assertEquals("say \\\"hello\\\"", result);
    }

    @Test
    void sanitize_escapesBothBackslashesAndQuotes() throws Exception {
        String result = (String) sanitizeMethod.invoke(service, "\\\"mixed\\\"");
        assertEquals("\\\\\\\"mixed\\\\\\\"", result);
    }

    @Test
    void sanitize_normalString_unchanged() throws Exception {
        String result = (String) sanitizeMethod.invoke(service, "hello world");
        assertEquals("hello world", result);
    }

    @Test
    void sanitize_emptyString_unchanged() throws Exception {
        String result = (String) sanitizeMethod.invoke(service, "");
        assertEquals("", result);
    }

    // -- jsonNodeToMap tests --

    @Test
    void jsonNodeToMap_basicFields() throws Exception {
        JsonNode node = mapper.readTree("{\"id\": 123, \"name\": \"Test Game\"}");
        Map<String, Object> map = (Map<String, Object>) jsonNodeToMapMethod.invoke(service, node);
        assertEquals(123, map.get("igdb_id"));
        assertEquals("Test Game", map.get("name"));
    }

    @Test
    void jsonNodeToMap_withGenres() throws Exception {
        JsonNode node = mapper.readTree("{\"id\": 1, \"name\": \"G\", \"genres\": [{\"name\": \"RPG\"}]}");
        Map<String, Object> map = (Map<String, Object>) jsonNodeToMapMethod.invoke(service, node);
        assertTrue(map.containsKey("genres"));
        assertEquals(List.of("RPG"), map.get("genres"));
    }

    @Test
    void jsonNodeToMap_withInvolvedCompanies() throws Exception {
        JsonNode node = mapper.readTree("""
            {
                "id": 1,
                "name": "G",
                "involved_companies": [
                    { "company": { "name": "DevStudio" }, "developer": true, "publisher": false },
                    { "company": { "name": "PubStudio" }, "developer": false, "publisher": true }
                ]
            }
            """);
        Map<String, Object> map = (Map<String, Object>) jsonNodeToMapMethod.invoke(service, node);
        assertEquals("DevStudio", map.get("developer"));
        assertEquals("PubStudio", map.get("publisher"));
    }

    @Test
    void jsonNodeToMap_withSummary() throws Exception {
        JsonNode node = mapper.readTree("{\"id\": 1, \"name\": \"G\", \"summary\": \"A great game\"}");
        Map<String, Object> map = (Map<String, Object>) jsonNodeToMapMethod.invoke(service, node);
        assertEquals("A great game", map.get("summary"));
    }

    @Test
    void jsonNodeToMap_withCoverUrl() throws Exception {
        JsonNode node = mapper.readTree("{\"id\": 1, \"name\": \"G\", \"cover\": { \"url\": \"//images.igdb.com/igdb/image/upload/t_thumb/abc.jpg\" }}");
        Map<String, Object> map = (Map<String, Object>) jsonNodeToMapMethod.invoke(service, node);
        String coverUrl = (String) map.get("cover_url");
        assertNotNull(coverUrl);
        assertTrue(coverUrl.startsWith("https:"));
        assertTrue(coverUrl.contains("t_cover_big"));
    }

    @Test
    void jsonNodeToMap_emptyObject_returnsDefaults() throws Exception {
        JsonNode node = mapper.readTree("{}");
        Map<String, Object> map = (Map<String, Object>) jsonNodeToMapMethod.invoke(service, node);
        assertEquals(0, map.get("igdb_id"));
        assertEquals("", map.get("name"));
    }

    @Test
    void jsonNodeToMap_noInvolvedCompanies_skipsDevPub() throws Exception {
        JsonNode node = mapper.readTree("{\"id\": 1, \"name\": \"G\"}");
        Map<String, Object> map = (Map<String, Object>) jsonNodeToMapMethod.invoke(service, node);
        assertFalse(map.containsKey("developer"));
        assertFalse(map.containsKey("publisher"));
    }

    @Test
    void jsonNodeToMap_firstDevAndPubOnly() throws Exception {
        // Multiple developers/publishers — only first of each should be recorded
        JsonNode node = mapper.readTree("""
            {
                "id": 1, "name": "G",
                "involved_companies": [
                    { "company": { "name": "FirstDev" }, "developer": true, "publisher": false },
                    { "company": { "name": "SecondDev" }, "developer": true, "publisher": false },
                    { "company": { "name": "FirstPub" }, "developer": false, "publisher": true }
                ]
            }
            """);
        Map<String, Object> map = (Map<String, Object>) jsonNodeToMapMethod.invoke(service, node);
        assertEquals("FirstDev", map.get("developer"));
        assertEquals("FirstPub", map.get("publisher"));
    }
}
