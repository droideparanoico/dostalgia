<script>
  import { getGame, deleteGame, updateGame, searchIGDB, scrapeIGDB, downloadGame, bundleUrl, setupBundleUrl, artworkUrl } from "../lib/api.js";
  import { push } from "../lib/router.js";

  let { id } = $props();

  let game = $state(null);
  let loading = $state(true);
  let editing = $state(false);
  let saving = $state(false);
  let error = $state("");

  let editTitle = $state("");
  let editYear = $state(0);
  let editGenre = $state("");
  let editDeveloper = $state("");
  let editPublisher = $state("");
  let editDescription = $state("");
  let editPlatform = $state("");
  let editExecutable = $state("");

  // IGDB scrape state
  let scraping = $state(false);
  let scrapeError = $state("");
  let igdbResults = $state(null);
  let igdbQuery = $state("");
  let applying = $state(null); // igdb_id being applied

  // Media selection — tracks which media item is shown in the media container
  let selectedMedia = $state(null);

  function selectMedia(type, idOrUrl) {
    if (type === 'video') {
      selectedMedia = { type: 'video', id: idOrUrl };
    } else if (type === 'screenshot') {
      selectedMedia = { type: 'screenshot', url: idOrUrl };
    }
  }

  function handleDownload() {
    downloadGame(id);
  }

  async function load() {
    loading = true;
    try {
      game = await getGame(id);
      igdbQuery = game.title || "";
      // Set initial selected media to first available item
      if (game.videos?.length) {
        selectedMedia = { type: 'video', id: game.videos[0] };
      } else if (game.screenshots?.length) {
        selectedMedia = { type: 'screenshot', url: game.screenshots[0] };
      }
    } catch (e) {
      error = "Game not found";
    }
    loading = false;
  }

  function startEdit() {
    editTitle = game.title;
    editYear = game.year || 0;
    editGenre = game.genre || "";
    editDeveloper = game.developer || "";
    editPublisher = game.publisher || "";
    editDescription = game.description || "";
    editPlatform = game.platform || "";
    editExecutable = game.executable || "";
    editing = true;
  }

  async function saveEdit() {
    saving = true;
    try {
      game = await updateGame(id, {
        title: editTitle,
        year: editYear || undefined,
        genre: editGenre || undefined,
        developer: editDeveloper || undefined,
        publisher: editPublisher || undefined,
        description: editDescription || undefined,
        platform: editPlatform || undefined,
        executable: editExecutable || undefined,
      });
      editing = false;
    } catch (e) {
      error = e.message;
    }
    saving = false;
  }

  async function handleDelete() {
    if (!confirm(`Delete "${game.title}"? This cannot be undone.`)) return;
    try {
      await deleteGame(id);
      push("/");
    } catch (e) {
      error = e.message;
    }
  }

  function handlePlay() {
    push(`/play/${id}`);
  }

  function handleSetup() {
    push(`/play/${id}?setup=1`);
  }

  // ─── IGDB Scrape ───────────────────────────────────────────

  async function handleSearchIGDB() {
    const query = igdbQuery.trim() || game.title;
    if (!query) return;
    scraping = true;
    scrapeError = "";
    igdbResults = null;
    try {
      const data = await searchIGDB(query);
      igdbResults = data.results || [];
      if (igdbResults.length === 0) {
        scrapeError = "No results found on IGDB. Try a different title.";
      }
    } catch (e) {
      scrapeError = "IGDB search failed: " + e.message;
    }
    scraping = false;
  }

  async function handleAutoScrape() {
    scraping = true;
    scrapeError = "";
    try {
      game = await scrapeIGDB(id);
      scrapeError = game.has_cover || game.year ? "Metadata updated from IGDB!" : "No matching game found on IGDB.";
    } catch (e) {
      scrapeError = "IGDB scrape failed: " + e.message;
    }
    scraping = false;
  }

  async function handleApplyScrape(igdbId) {
    applying = igdbId;
    scrapeError = "";
    try {
      game = await scrapeIGDB(id, igdbId);
      igdbResults = null; // clear results after applying
      scrapeError = "Game metadata updated!";
    } catch (e) {
      scrapeError = "Failed to apply IGDB data: " + e.message;
    }
    applying = null;
  }

  $effect(() => { load(); });
</script>

{#if loading}
  <div class="loading">Loading...</div>
{:else if error && !game}
  <div class="error">{error}</div>
  <a href="#/" class="back-link">← Back to Library</a>
{:else if game}
  <div class="detail">
    <a href="#/" class="back-link">← Back to Library</a>

    <div class="detail-layout">
      <!-- Cover + Meta + Developer -->
      <div class="cover-section">
        {#if game.has_cover}
          <img src={`/games/${game.id}/cover.jpg`} alt={game.title} class="cover-img" />
        {:else}
          <div class="cover-placeholder">💾</div>
        {/if}
        {#if !editing}
          {#if game.year || game.genre}
            <div class="meta">
              {#if game.year}
                <a href="#/?year={game.year}" class="meta-item meta-link">{game.year}</a>
              {/if}
              {#if game.genre}
                <a href="#/?genre={encodeURIComponent(game.genre)}" class="meta-item meta-link">{game.genre}</a>
              {/if}
            </div>
          {/if}
          {#if game.developer}
            <p class="dev">by {game.developer}{game.publisher ? ` · ${game.publisher}` : ""}</p>
          {/if}
        {/if}
      </div>

      <!-- Info -->
      <div class="info-section">
        {#if editing}
          <input type="text" bind:value={editTitle} placeholder="Title" class="edit-title" />
          <div class="edit-row">
            <input type="number" bind:value={editYear} placeholder="Year" style="width:100px" />
            <input type="text" bind:value={editGenre} placeholder="Genre" style="flex:1" />
          </div>
          <div class="edit-row">
            <input type="text" bind:value={editDeveloper} placeholder="Developer" style="flex:1" />
            <input type="text" bind:value={editPublisher} placeholder="Publisher" style="flex:1" />
          </div>
          <textarea bind:value={editDescription} placeholder="Description" rows="4"></textarea>
          <div class="edit-row">
            <select bind:value={editPlatform} style="flex:1">
              <option value="">Auto (unknown)</option>
              <option value="dos">DOS</option>
              <option value="windows">Windows</option>
            </select>
          </div>
          {#if game.executables && game.executables.length > 0}
            <div class="edit-exec-section">
              <span class="edit-exec-label">Main executable:</span>
              <div class="edit-exec-list">
                {#each game.executables as exe}
                  <label class="edit-exec-option" class:selected={editExecutable === exe}>
                    <input
                      type="radio"
                      name="executable"
                      value={exe}
                      checked={editExecutable === exe}
                      onchange={() => editExecutable = exe}
                    />
                    <span class="edit-exec-path">{exe}</span>
                  </label>
                {/each}
              </div>
            </div>
          {/if}
          <div class="edit-actions">
            <button class="btn btn-primary" onclick={saveEdit} disabled={saving}>
              {saving ? "Saving..." : "Save"}
            </button>
            <button class="btn" onclick={() => editing = false}>Cancel</button>
          </div>
        {:else}
          <h1>{game.title}</h1>
          {#if game.platform}
            <div class="platform-badge" class:platform-win={game.platform === 'windows'}>
              {#if game.platform === 'windows'}
                🪟 Requires Windows 3.1 — may not work in browser
              {:else}
                💾 DOS
                {#if game.bundle_size}
                  <span class="size-badge">· {game.bundle_size >= 1073741824
                    ? (game.bundle_size / 1073741824).toFixed(1) + ' GB'
                    : game.bundle_size >= 1048576
                      ? Math.round(game.bundle_size / 1048576) + ' MB'
                      : Math.round(game.bundle_size / 1024) + ' KB'}</span>
                {/if}
              {/if}
            </div>
          {/if}
          {#if game.description}
            <p class="description">{game.description}</p>
          {/if}

          <div class="actions">
            {#if game.ready && (game.platform !== 'windows')}
              <button class="btn btn-primary" onclick={handlePlay}>▶ Play</button>
            {:else if game.ready && game.platform === 'windows'}
              <button class="btn btn-primary btn-disabled" disabled title="Windows games need Windows 3.1 emulation (not yet supported)">
                ▶ Unplayable
              </button>
            {/if}
            {#if game.has_setup && game.platform !== 'windows'}
              <button class="btn btn-setup" onclick={handleSetup}>🛠 Setup</button>
            {/if}
            <button class="btn" onclick={startEdit}>✏ Edit</button>
            <button class="btn" onclick={handleDownload}>⬇ Download</button>
            <button class="btn btn-danger" onclick={handleDelete}>✕ Delete</button>
          </div>

          <!-- Media Section — container + clickable row for all videos & screenshots -->
          {#if selectedMedia}
            <div class="media-section">
              <span class="media-label">📺 Media</span>
              <div class="media-container">
                {#if selectedMedia.type === 'video'}
                  <iframe
                    src="https://www.youtube.com/embed/{selectedMedia.id}"
                    title="Gameplay video"
                    allowfullscreen
                    frameborder="0"
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                  ></iframe>
                {:else if selectedMedia.type === 'screenshot'}
                  <img src={selectedMedia.url} alt="Screenshot" class="media-main-img" />
                {/if}
              </div>
              <div class="media-row">
                {#each game.videos ?? [] as video, i}
                  <button
                    class="media-thumb"
                    class:active={selectedMedia.type === 'video' && selectedMedia.id === video}
                    onclick={() => selectMedia('video', video)}
                    title="Video {i + 1}"
                  >
                    <img src="https://img.youtube.com/vi/{video}/mqdefault.jpg" alt="Video {i + 1}" loading="lazy" />
                    <span class="media-thumb-icon">▶</span>
                  </button>
                {/each}
                {#each game.screenshots ?? [] as shot}
                  <button
                    class="media-thumb"
                    class:active={selectedMedia.type === 'screenshot' && selectedMedia.url === shot}
                    onclick={() => selectMedia('screenshot', shot)}
                    title="Screenshot"
                  >
                    <img src={shot} alt="Screenshot" loading="lazy" />
                  </button>
                {/each}
              </div>
            </div>
          {/if}

          <!-- IGDB Scrape Section -->
          <div class="scrape-section">
            <div class="scrape-header">
              <span class="scrape-label">📡 IGDB Metadata</span>
              <div class="scrape-buttons">
                <button class="btn btn-sm" onclick={handleAutoScrape} disabled={scraping}>
                  {scraping ? "Searching..." : "Auto Scrape"}
                </button>
                <button class="btn btn-sm" onclick={handleSearchIGDB} disabled={scraping}>
                  Search
                </button>
              </div>
            </div>

            {#if igdbResults !== null}
              <div class="igdb-query-row">
                <input
                  type="text"
                  bind:value={igdbQuery}
                  placeholder="Search IGDB..."
                  onkeydown={(e) => e.key === "Enter" && handleSearchIGDB()}
                />
              </div>
              {#if igdbResults.length > 0}
                <div class="igdb-results">
                  {#each igdbResults as result (result.igdb_id)}
                    <button
                      class="igdb-result"
                      onclick={() => handleApplyScrape(result.igdb_id)}
                      disabled={applying === result.igdb_id}
                    >
                      {#if result.cover_url}
                        <img
                          src={result.cover_url}
                          alt={result.name}
                          class="igdb-thumb"
                          loading="lazy"
                        />
                      {:else}
                        <div class="igdb-thumb-placeholder">🎮</div>
                      {/if}
                      <div class="igdb-info">
                        <strong>{result.name}</strong>
                        <span class="igdb-meta">
                          {result.year || "—"}
                          {#if result.is_dos}<span class="dos-badge">DOS</span>{/if}
                          {#if result.genres?.length}
                            · {result.genres.slice(0, 2).join(", ")}
                          {/if}
                        </span>
                        {#if result.developer}
                          <span class="igdb-dev">{result.developer}</span>
                        {/if}
                      </div>
                    </button>
                  {/each}
                </div>
              {/if}
            {/if}

            {#if scrapeError}
              <div class="scrape-status" class:scrape-ok={scrapeError.includes("updated")}>
                {scrapeError}
              </div>
            {/if}
          </div>
        {/if}
      </div>
    </div>
  </div>
{/if}

<style>
  .detail { padding: 32px 0; }
  .loading { text-align: center; padding: 60px; color: var(--text-dim); }
  .error { background: #331111; border: 1px solid #cc3333; color: #ff6666; padding: 12px; border-radius: var(--radius-sm); margin-bottom: 16px; }
  .back-link { display: inline-block; margin-bottom: 24px; color: var(--text-dim); }
  .back-link:hover { color: var(--phosphor); }
  .detail-layout { display: grid; grid-template-columns: 300px 1fr; gap: 32px; align-items: start; }
  .detail-layout > * { min-width: 0; } /* prevent grid overflow from wide content */
  @media (max-width: 640px) { .detail-layout { grid-template-columns: 1fr; } }
  .cover-section { border-radius: var(--radius); overflow: hidden; }
  .cover-section .meta { margin-top: 12px; }
  .cover-section .dev { margin-top: 8px; }
  .cover-img { width: 100%; border-radius: var(--radius); }
  .cover-placeholder { aspect-ratio: 3/4; background: var(--surface); display: flex; align-items: center; justify-content: center; font-size: 4rem; border-radius: var(--radius); }
  .info-section h1 { color: var(--phosphor); font-family: var(--font-mono); margin-bottom: 8px; word-break: break-word; }
  .platform-badge {
    display: inline-block;
    font-size: 0.8rem;
    padding: 3px 10px;
    border-radius: 10px;
    font-weight: 600;
    margin-bottom: 8px;
    background: var(--phosphor-dark);
    color: var(--phosphor);
  }
  .platform-badge.platform-win {
    background: #1a3a5c;
    color: #6ab0ff;
  }
  .size-badge {
    font-weight: 400;
    opacity: 0.8;
  }
  .meta { display: flex; gap: 8px; margin-bottom: 8px; }
  .meta-item { background: var(--surface-hover); color: var(--phosphor-dim); padding: 4px 12px; border-radius: 12px; font-size: 0.85rem; }
  .meta-link { cursor: pointer; text-decoration: none; transition: all 0.15s; display: inline-block; }
  .meta-link:hover { background: var(--phosphor-dark); color: var(--phosphor); }
  .dev { color: var(--text-dim); margin-bottom: 16px; }
  .description { color: var(--text); line-height: 1.7; margin-bottom: 24px; overflow-wrap: break-word; word-break: break-word; }
  .actions { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 24px; }
  .edit-title { font-size: 1.5rem; margin-bottom: 12px; width: 100%; }
  .edit-row { display: flex; gap: 8px; margin-bottom: 8px; }
  .edit-actions { display: flex; gap: 8px; margin-top: 12px; }
  textarea { width: 100%; min-height: 100px; }

  /* Media Section — container + clickable thumbnail row */
  .media-section {
    margin-top: 24px;
    padding-top: 20px;
    border-top: 1px solid var(--border);
  }
  .media-label {
    display: block;
    font-size: 0.85rem;
    color: var(--text-dim);
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin-bottom: 12px;
  }
  .media-container {
    position: relative;
    width: 100%;
    aspect-ratio: 16 / 9;
    border-radius: var(--radius);
    overflow: hidden;
    background: #000;
    margin-bottom: 12px;
  }
  .media-container iframe {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
  }
  .media-main-img {
    width: 100%;
    height: 100%;
    object-fit: contain;
    background: #000;
  }
  .media-row {
    display: flex;
    gap: 8px;
    overflow-x: auto;
    padding-bottom: 4px;
  }
  .media-thumb {
    flex-shrink: 0;
    position: relative;
    width: 200px;
    height: 113px;
    border-radius: var(--radius-sm);
    border: 2px solid var(--border);
    overflow: hidden;
    cursor: pointer;
    padding: 0;
    background: none;
    transition: border-color 0.15s, opacity 0.15s;
  }
  .media-thumb:hover {
    border-color: var(--phosphor-dim);
    opacity: 0.9;
  }
  .media-thumb.active {
    border-color: var(--phosphor);
  }
  .media-thumb img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  .media-thumb-icon {
    position: absolute;
    bottom: 4px;
    left: 4px;
    background: rgba(0,0,0,0.7);
    color: #fff;
    font-size: 0.7rem;
    padding: 1px 6px;
    border-radius: 4px;
  }

  /* IGDB Scrape */
  .scrape-section {
    margin-top: 24px;
    padding-top: 20px;
    border-top: 1px solid var(--border);
  }
  .scrape-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }
  .scrape-label {
    font-size: 0.85rem;
    color: var(--text-dim);
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
  }
  .scrape-buttons {
    display: flex;
    gap: 6px;
  }
  .btn-sm {
    padding: 4px 12px;
    font-size: 0.8rem;
  }
  .igdb-query-row {
    margin-bottom: 8px;
  }
  .igdb-query-row input {
    width: 100%;
    box-sizing: border-box;
  }
  .igdb-results {
    display: flex;
    flex-direction: column;
    gap: 8px;
    max-height: 320px;
    overflow-y: auto;
  }
  .igdb-result {
    display: flex;
    gap: 12px;
    align-items: center;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    padding: 8px;
    cursor: pointer;
    text-align: left;
    transition: border-color 0.15s;
    width: 100%;
  }
  .igdb-result:hover {
    border-color: var(--phosphor-dim);
  }
  .igdb-result:disabled {
    opacity: 0.6;
    cursor: wait;
  }
  .igdb-thumb {
    width: 48px;
    height: 64px;
    object-fit: cover;
    border-radius: 4px;
    flex-shrink: 0;
  }
  .igdb-thumb-placeholder {
    width: 48px;
    height: 64px;
    background: var(--surface-hover);
    border-radius: 4px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 1.2rem;
    flex-shrink: 0;
  }
  .igdb-info {
    flex: 1;
    min-width: 0;
  }
  .igdb-info strong {
    display: block;
    font-size: 0.9rem;
    color: var(--text-bright);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .igdb-meta {
    font-size: 0.8rem;
    color: var(--text-dim);
  }
  .dos-badge {
    background: var(--phosphor-dark);
    color: var(--phosphor);
    padding: 1px 6px;
    border-radius: 6px;
    font-size: 0.7rem;
    font-weight: 600;
  }
  .igdb-dev {
    display: block;
    font-size: 0.75rem;
    color: var(--text-dim);
    margin-top: 2px;
  }
  .scrape-status {
    margin-top: 8px;
    padding: 6px 10px;
    border-radius: var(--radius-sm);
    font-size: 0.85rem;
    background: #221100;
    color: #ff9933;
  }
  .scrape-status.scrape-ok {
    background: #112211;
    color: #66ff66;
  }

  /* ─── Executable Picker ────────────────────── */
  .edit-exec-section {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--border);
  }
  .edit-exec-label {
    display: block;
    font-size: 0.8rem;
    color: var(--text-dim);
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin-bottom: 8px;
  }
  .edit-exec-list {
    display: flex;
    flex-direction: column;
    gap: 4px;
    max-height: 180px;
    overflow-y: auto;
  }
  .edit-exec-option {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 8px;
    border-radius: var(--radius-sm);
    cursor: pointer;
    font-size: 0.85rem;
    font-family: var(--font-mono);
    background: var(--surface);
    border: 1px solid var(--border);
    transition: border-color 0.15s, background 0.15s;
  }
  .edit-exec-option:hover {
    border-color: var(--phosphor-dim);
    background: var(--surface-hover);
  }
  .edit-exec-option.selected {
    border-color: var(--phosphor);
    background: var(--phosphor-burn);
  }
  .edit-exec-option input[type="radio"] {
    accent-color: var(--phosphor);
    margin: 0;
    flex-shrink: 0;
  }
  .edit-exec-path {
    word-break: break-all;
    color: var(--text);
  }
  .edit-exec-option.selected .edit-exec-path {
    color: var(--phosphor-glow);
  }

  /* ─── Responsive ────────────────────────────────── */
  @media (max-width: 640px) {
    .detail { padding: 16px 0; }
    .detail-layout { gap: 16px; }
    .cover-section { max-width: 200px; }
    .cover-placeholder { font-size: 3rem; }
    .media-thumb { width: 160px; height: 90px; }
    .scrape-header { flex-direction: column; align-items: flex-start; gap: 8px; }
    .scrape-buttons { width: 100%; }
    .scrape-buttons .btn { flex: 1; justify-content: center; }
    .edit-row { flex-direction: column; }
    .edit-row input { width: 100% !important; }
  }
  .btn-setup {
    border-color: var(--phosphor-dark);
    color: var(--phosphor);
  }
  .btn-setup:hover {
    background: var(--phosphor-burn);
    box-shadow: 0 0 12px var(--phosphor-dark);
  }
</style>
