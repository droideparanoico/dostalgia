package org.dostalgia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** Represents a single DOS game, stored as data/games/{id}/game.json */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Game {
    private String id;
    private String title;
    private Integer year;
    private String genre;
    private String developer;
    private String publisher;
    private String description;
    private Double rating;
    private String bundleFile;
    private String setupExe;
    /** Game bundle file size in bytes. Populated dynamically at load time (not persisted). */
    private Long bundleSize;
    /** Currently selected main executable (relative path inside the bundle). */
    private String executable;
    /** All discoverable executables in the bundle (for the UI picker). */
    private List<String> executables;
    /** Platform type: "dos" for pure DOS, "windows" for Windows 3.x/9x+ */
    private String platform;
    private Integer igdbId;
    private String igdbCoverId;
    private boolean hasCover;
    private boolean hasScreenshots;
    private List<String> screenshots;
    private List<String> videos;
    private boolean ready;
    private Instant createdAt;
    private Instant updatedAt;

    public Game() {}

    public Game(String id, String title) {
        this.id = id;
        this.title = title;
        this.ready = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }

    public String getDeveloper() { return developer; }
    public void setDeveloper(String developer) { this.developer = developer; }

    public String getPublisher() { return publisher; }
    public void setPublisher(String publisher) { this.publisher = publisher; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public String getBundleFile() { return bundleFile; }
    public void setBundleFile(String bundleFile) { this.bundleFile = bundleFile; }

    public String getSetupExe() { return setupExe; }
    public void setSetupExe(String setupExe) { this.setupExe = setupExe; }

    public Long getBundleSize() { return bundleSize; }
    public void setBundleSize(Long bundleSize) { this.bundleSize = bundleSize; }

    public String getExecutable() { return executable; }
    public void setExecutable(String executable) { this.executable = executable; }

    public List<String> getExecutables() { return executables; }
    public void setExecutables(List<String> executables) { this.executables = executables; }

    /** Returns true if a SETUP.EXE (or variant) was detected in the game archive. */
    public boolean isHasSetup() { return setupExe != null && !setupExe.isBlank(); }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    /** Returns true if the game is a pure DOS executable that can run natively in DOSBox. */
    public boolean isPlayable() {
        return ready && (platform == null || "dos".equals(platform));
    }

    public Integer getIgdbId() { return igdbId; }
    public void setIgdbId(Integer igdbId) { this.igdbId = igdbId; }

    public String getIgdbCoverId() { return igdbCoverId; }
    public void setIgdbCoverId(String igdbCoverId) { this.igdbCoverId = igdbCoverId; }

    public boolean isHasCover() { return hasCover; }
    public void setHasCover(boolean hasCover) { this.hasCover = hasCover; }

    public boolean isHasScreenshots() { return hasScreenshots; }
    public void setHasScreenshots(boolean hasScreenshots) { this.hasScreenshots = hasScreenshots; }

    public List<String> getScreenshots() { return screenshots; }
    public void setScreenshots(List<String> screenshots) { this.screenshots = screenshots; }

    public List<String> getVideos() { return videos; }
    public void setVideos(List<String> videos) { this.videos = videos; }

    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
