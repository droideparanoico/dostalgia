<script>
  import { listGames, uploadGame, searchIGDB } from "../lib/api.js";
  import GameCard from "../lib/GameCard.svelte";

  let { genreFilter = "", yearFilter = "", uploadTriggered = 0 } = $props();

  let games = $state([]);
  let loading = $state(true);
  let search = $state("");
  let uploading = $state(false);
  let uploadError = $state("");

  // Post-selection upload dialog
  let pendingFile = $state(null);
  let pendingFileName = $state("");
  let pendingTitle = $state("");
  let searching = $state(false);
  let searchError = $state("");
  let igdbResults = $state(null);
  let manualSearched = $state(false);
  let duplicateError = $state("");

  // Filter state
  let selectedGenres = $state(new Set());
  let selectedYears = $state(new Set());
  let showAllGenres = $state(false);
  let showAllYears = $state(false);

  let fileInput;

  // When header upload button is clicked, trigger the hidden file input.
  // Uses a previous-value guard to prevent re-firing on component remount.
  let prevUploadTrigger = $state(uploadTriggered);
  $effect(() => {
    if (uploadTriggered > 0 && uploadTriggered !== prevUploadTrigger) {
      prevUploadTrigger = uploadTriggered;
      fileInput?.click();
    }
  });

  // Derive filter options from the loaded games
  let filterOptions = $derived.by(() => {
    const years = {};
    const genres = {};
    for (const g of games) {
      if (g.year) {
        const y = String(g.year);
        years[y] = (years[y] || 0) + 1;
      }
      if (g.genre) {
        genres[g.genre] = (genres[g.genre] || 0) + 1;
      }
    }
    return {
      years: Object.entries(years)
        .map(([label, count]) => ({ label, count }))
        .sort((a, b) => Number(b.label) - Number(a.label)),
      genres: Object.entries(genres)
        .map(([label, count]) => ({ label, count }))
        .sort((a, b) => b.count - a.count),
    };
  });

  // Apply initial filter values from URL params
  $effect(() => {
    if (genreFilter) selectedGenres = new Set([genreFilter]);
    if (yearFilter) selectedYears = new Set([yearFilter]);
  });

  // Update URL hash when filters change (but not on initial load)
  let initialFilter = true;
  $effect(() => {
    if (initialFilter) { initialFilter = false; return; }
    const params = new URLSearchParams();
    if (selectedGenres.size === 1) params.set("genre", [...selectedGenres][0]);
    if (selectedYears.size === 1) params.set("year", [...selectedYears][0]);
    const qs = params.toString();
    const base = window.location.hash.split("?")[0] || "#/";
    const newHash = qs ? base + "?" + qs : base;
    if (window.location.hash !== newHash) {
      window.location.hash = newHash;
    }
  });

  // Filtered games
  let filteredGames = $derived.by(() => {
    let list = games;
    if (selectedGenres.size > 0) {
      list = list.filter(g => g.genre && selectedGenres.has(g.genre));
    }
    if (selectedYears.size > 0) {
      list = list.filter(g => g.year && selectedYears.has(String(g.year)));
    }
    return list;
  });

  function toggleFilter(set, value) {
    const next = new Set(set);
    if (next.has(value)) next.delete(value);
    else next.add(value);
    return next;
  }

  function resetFilters() {
    selectedGenres = new Set();
    selectedYears = new Set();
    showAllGenres = false;
    showAllYears = false;
    window.location.hash = "#/";
  }

  function hasActiveFilters() {
    return selectedGenres.size > 0 || selectedYears.size > 0;
  }

  async function loadGames() {
    loading = true;
    try {
      const data = await listGames(search ? { search } : {});
      games = data.games || [];
    } catch (e) {
      console.error("Failed to load games:", e);
    }
    loading = false;
  }

  function handleFileSelected(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    const name = file.name.replace(/\.zip$/i, "");
    pendingFile = file;
    pendingFileName = name;
    pendingTitle = name;
    igdbResults = null;
    searching = true;
    searchError = "";
    manualSearched = false;
    duplicateError = "";
    fileInput.value = "";
    searchIGDB(name).then(data => {
      igdbResults = data.results || [];
    }).catch(err => {
      searchError = "IGDB search failed: " + err.message;
      igdbResults = [];
    }).finally(() => {
      searching = false;
    });
  }

  async function handleManualSearch() {
    const q = pendingTitle.trim();
    if (!q) return;
    manualSearched = true;
    searching = true;
    searchError = "";
    igdbResults = null;
    try {
      const data = await searchIGDB(q);
      igdbResults = data.results || [];
      if (igdbResults.length === 0) {
        searchError = 'No matches found for "' + q + '" on IGDB.';
      }
    } catch (err) {
      searchError = "IGDB search failed: " + err.message;
      igdbResults = [];
    }
    searching = false;
  }

  function cancelUpload() {
    pendingFile = null;
    pendingFileName = "";
    pendingTitle = "";
    igdbResults = null;
    searchError = "";
    manualSearched = false;
    duplicateError = "";
  }

  async function doUpload(title, igdbId) {
    if (!pendingFile) return;

    const finalTitle = title || pendingFileName;
    if (finalTitle) {
      const dup = games.find(g => g.title?.toLowerCase() === finalTitle.toLowerCase());
      if (dup) {
        duplicateError = `"${dup.title}" is already in your library`;
        return;
      }
    }

    uploading = true;
    uploadError = "";
    duplicateError = "";
    try {
      await uploadGame(pendingFile, title || undefined, igdbId);
      cancelUpload();
      await loadGames();
    } catch (err) {
      uploadError = err.message;
    }
    uploading = false;
  }

  $effect(() => {
    loadGames();
  });
</script>

<div class="library-layout">
  <!-- ─── Filter Sidebar ─────────────────────────── -->
  <aside class="sidebar" class:sidebar-active={hasActiveFilters()}>

    <!-- Search box -->
    <div class="sidebar-search">
      <input
        type="text"
        placeholder="Search games..."
        bind:value={search}
        oninput={() => loadGames()}
      />
    </div>

    <!-- Year filter group -->
    <div class="filter-group">
      <h3 class="filter-label">Year</h3>
      {#if filterOptions.years.length === 0}
        <p class="filter-empty">—</p>
      {:else}
        {#each (showAllYears ? filterOptions.years : filterOptions.years.slice(0, 5)) as opt}
          <label class="filter-option">
            <input
              type="checkbox"
              checked={selectedYears.has(opt.label)}
              onchange={() => selectedYears = toggleFilter(selectedYears, opt.label)}
            />
            <span class="filter-text">{opt.label}</span>
            <span class="filter-count">{opt.count}</span>
          </label>
        {/each}
        {#if filterOptions.years.length > 5}
          <button class="btn btn-link" onclick={() => showAllYears = !showAllYears}>
            {showAllYears ? "△ Show less" : `▾ Show all (${filterOptions.years.length})`}
          </button>
        {/if}
      {/if}
    </div>

    <!-- Genre filter group -->
    <div class="filter-group">
      <h3 class="filter-label">Genre</h3>
      {#if filterOptions.genres.length === 0}
        <p class="filter-empty">—</p>
      {:else}
        {#each (showAllGenres ? filterOptions.genres : filterOptions.genres.slice(0, 5)) as opt}
          <label class="filter-option">
            <input
              type="checkbox"
              checked={selectedGenres.has(opt.label)}
              onchange={() => selectedGenres = toggleFilter(selectedGenres, opt.label)}
            />
            <span class="filter-text">{opt.label}</span>
            <span class="filter-count">{opt.count}</span>
          </label>
        {/each}
        {#if filterOptions.genres.length > 5}
          <button class="btn btn-link" onclick={() => showAllGenres = !showAllGenres}>
            {showAllGenres ? "△ Show less" : `▾ Show all (${filterOptions.genres.length})`}
          </button>
        {/if}
      {/if}
    </div>

    {#if hasActiveFilters()}
      <div class="sidebar-reset">
        <button class="btn btn-sm btn-reset" onclick={resetFilters}>✕ Reset</button>
      </div>
    {/if}
  </aside>

  <!-- ─── Main Content ──────────────────────────── -->
  <div class="library-main">

    <!-- Active filter chips -->
    {#if hasActiveFilters()}
      <div class="active-filters">
        {#each [...selectedYears] as y}
          <button class="filter-chip" onclick={() => selectedYears = toggleFilter(selectedYears, y)}>
            {y} ✕
          </button>
        {/each}
        {#each [...selectedGenres] as g}
          <button class="filter-chip" onclick={() => selectedGenres = toggleFilter(selectedGenres, g)}>
            {g} ✕
          </button>
        {/each}
      </div>
    {/if}

    {#if pendingFile}
      <div class="upload-overlay" onclick={cancelUpload} role="presentation">
        <div class="upload-dialog" onclick={(e) => e.stopPropagation()} role="dialog">
          <h3>Upload Game</h3>
          <p class="upload-file-name">{pendingFileName}.zip</p>

          <div class="upload-dialog-body">
            {#if uploading}
              <div class="upload-progress">
                <div class="upload-progress-spinner"></div>
                <p class="upload-progress-text">Uploading and processing</p>
                <p class="upload-progress-sub">{pendingFileName}.zip</p>
                <p class="upload-progress-note">This may take a while for large games</p>
              </div>
            {:else if duplicateError}
              <div class="status-msg error-msg">
                ⚠️ {duplicateError}
              </div>
            {:else if searching}
              <div class="status-msg">Searching IGDB for "{pendingFileName}"...</div>
            {:else if igdbResults !== null && igdbResults.length > 0}
              <p class="match-label">Select a match or enter a custom title:</p>
              <div class="upload-igdb-results">
                {#each igdbResults as result}
                  <button
                    class="igdb-result"
                    data-name={result.name}
                    data-igdb-id={result.igdb_id}
                    onclick={(e) => doUpload(e.currentTarget.dataset.name, Number(e.currentTarget.dataset.igdbId))}
                    disabled={uploading}
                  >
                    {#if result.cover_url}
                      <img src={result.cover_url} alt={result.name} class="igdb-thumb" loading="lazy" />
                    {:else}
                      <div class="igdb-thumb-placeholder">🎮</div>
                    {/if}
                    <div class="igdb-info">
                      <strong>{result.name}</strong>
                      <span class="igdb-meta">
                        {result.year || "—"}
                        {#if result.genres?.length} · {result.genres.slice(0, 2).join(", ")}{/if}
                      </span>
                      {#if result.developer}
                        <span class="igdb-dev">{result.developer}</span>
                      {/if}
                    </div>
                  </button>
                {/each}
              </div>
              <div class="upload-custom-row">
                <input
                  type="text"
                  placeholder="Or type a different title..."
                  bind:value={pendingTitle}
                  disabled={uploading}
                  onkeydown={(e) => e.key === "Enter" && handleManualSearch()}
                />
                <button class="btn" onclick={handleManualSearch} disabled={uploading || !pendingTitle.trim()}>
                  Search
                </button>
              </div>
            {:else}
              {#if searchError}
                <div class="status-msg error-msg">
                  {searchError}
                  {#if manualSearched}
                    <button class="btn btn-sm btn-upload-anyway" onclick={() => doUpload(pendingTitle.trim())} disabled={uploading}>
                      {uploading ? "Uploading..." : 'Upload anyway as "' + pendingTitle.trim() + '"'}
                    </button>
                  {/if}
                </div>
              {:else}
                <p class="match-label">No matches found. Edit the title below and search IGDB:</p>
              {/if}
              <div class="upload-custom-row">
                <input
                  type="text"
                  placeholder="Edit title..."
                  bind:value={pendingTitle}
                  disabled={uploading || searching}
                  onkeydown={(e) => e.key === "Enter" && handleManualSearch()}
                />
                <button class="btn" onclick={handleManualSearch} disabled={uploading || searching || !pendingTitle.trim()}>
                  {uploading ? "Uploading..." : "Search"}
                </button>
              </div>
              <button class="btn btn-secondary" onclick={() => doUpload(pendingFileName)} disabled={uploading}>
                Use filename: {pendingFileName}
              </button>
            {/if}
          </div>

          <button class="btn btn-cancel" onclick={cancelUpload} disabled={uploading}>Cancel</button>
        </div>
      </div>
    {/if}

    {#if uploadError}
      <div class="error">{uploadError}</div>
    {/if}

    {#if loading}
      <div class="loading">Scanning library...</div>
    {:else if filteredGames.length === 0}
      <div class="empty-state">
        <div class="empty-icon">💾</div>
        <h2>{hasActiveFilters() ? "No matching games" : "No games yet"}</h2>
        <p>{hasActiveFilters() ? "Try changing your filters" : "Upload a DOS game ZIP to get started"}</p>
        {#if hasActiveFilters()}
          <button class="btn btn-primary" style="margin-top: 16px;" onclick={resetFilters}>✕ Clear filters</button>
        {:else}
          <label class="btn btn-primary" style="margin-top: 16px;">
            Upload your first game
            <input type="file" accept=".zip" class="hidden-input" onchange={handleFileSelected} />
          </label>
        {/if}
      </div>
    {:else}
      <div class="grid">
        {#each filteredGames as game (game.id)}
          <GameCard {game} />
        {/each}
      </div>
      <div class="count">{filteredGames.length} game{filteredGames.length !== 1 ? "s" : ""}</div>
    {/if}

    <!-- Hidden file input triggered by header upload button -->
    <input
      bind:this={fileInput}
      type="file"
      accept=".zip"
      class="hidden-input"
      onchange={handleFileSelected}
      disabled={uploading}
    />
  </div>
</div>

<style>
  /* ─── Layout ────────────────────────────────── */
  .library-layout {
    display: flex;
    gap: 28px;
    padding: 32px 0;
    align-items: flex-start;
  }
  @media (max-width: 800px) {
    .library-layout { flex-direction: column; padding: 16px 0; gap: 16px; }
  }

  /* ─── Sidebar ────────────────────────────────── */
  .sidebar {
    width: 220px;
    flex-shrink: 0;
    position: sticky;
    top: 24px;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: 16px;
    max-height: calc(100vh - 120px);
    overflow-y: auto;
  }
  @media (max-width: 800px) {
    .sidebar {
      width: 100%;
      position: static;
      max-height: none;
    }
  }
  .sidebar-search {
    margin-bottom: 16px;
  }
  .sidebar-search input {
    width: 100%;
  }
  .sidebar-reset {
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px solid var(--border);
  }
  .sidebar-reset .btn-reset {
    width: 100%;
    justify-content: center;
  }
  .filter-group {
    margin-bottom: 20px;
  }
  .filter-label {
    font-size: 0.75rem;
    color: var(--text-dim);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin-bottom: 8px;
    font-weight: 600;
  }
  .filter-empty {
    color: var(--text-dim);
    font-size: 0.8rem;
    font-style: italic;
  }
  .filter-option {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 3px 0;
    cursor: pointer;
    font-size: 0.85rem;
    color: var(--text);
    transition: color 0.1s;
  }
  .filter-option:hover {
    color: var(--phosphor);
  }
  .filter-option input[type="checkbox"] {
    accent-color: var(--phosphor);
    margin: 0;
    flex-shrink: 0;
  }
  .filter-text {
    flex: 1;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .filter-count {
    font-size: 0.75rem;
    color: var(--text-dim);
    background: var(--surface-hover);
    padding: 0 6px;
    border-radius: 8px;
    min-width: 20px;
    text-align: center;
  }
  .btn-link {
    background: none;
    border: none;
    color: var(--phosphor-dim);
    font-size: 0.8rem;
    cursor: pointer;
    padding: 4px 0;
    font-family: var(--font-sans);
  }
  .btn-link:hover {
    color: var(--phosphor);
  }
  .btn-reset {
    border-color: #883333;
    color: #ff6666;
    font-size: 0.75rem;
    padding: 2px 10px;
  }
  .btn-reset:hover {
    background: #331111;
    border-color: #ff4444;
  }

  /* ─── Active filter chips ────────────────────── */
  .active-filters {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    margin-bottom: 16px;
  }
  .filter-chip {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    background: var(--phosphor-dark);
    color: var(--phosphor);
    border: 1px solid transparent;
    border-radius: 14px;
    padding: 3px 12px;
    font-size: 0.8rem;
    cursor: pointer;
    font-family: var(--font-sans);
    transition: all 0.15s;
  }
  .filter-chip:hover {
    background: #1a4422;
    border-color: var(--phosphor-dim);
  }

  /* ─── Main content ───────────────────────────── */
  .library-main {
    flex: 1;
    min-width: 0;
  }

  .btn-link {
    background: none;
    border: none;
    color: var(--phosphor-dim);
    font-size: 0.8rem;
    cursor: pointer;
    padding: 4px 0;
    font-family: var(--font-sans);
  }
  .btn-link:hover {
    color: var(--phosphor);
  }

  .hidden-input {
    display: none;
  }

  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 20px;
  }
  @media (max-width: 480px) {
    .grid { grid-template-columns: repeat(2, 1fr); gap: 12px; }
  }

  .error {
    background: #331111;
    border: 1px solid #cc3333;
    color: #ff6666;
    padding: 12px 16px;
    border-radius: var(--radius-sm);
    margin-bottom: 16px;
  }

  .loading {
    text-align: center;
    padding: 60px 20px;
    color: var(--text-dim);
    font-style: italic;
  }

  .empty-state {
    text-align: center;
    padding: 80px 20px;
    border: 1px dashed var(--border);
    border-radius: var(--radius);
  }
  .empty-icon {
    font-size: 3rem;
    margin-bottom: 16px;
  }
  .empty-state h2 {
    margin-bottom: 8px;
  }
  .empty-state p {
    color: var(--text-dim);
  }

  .count {
    margin-top: 24px;
    text-align: center;
    color: var(--text-dim);
    font-size: 0.85rem;
  }

  /* ─── Upload Dialog ─────────────────────────────── */
  .upload-overlay {
    position: fixed;
    inset: 0;
    background: rgba(0,0,0,0.7);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 200;
    padding: 16px;
  }
  .upload-dialog {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: 24px;
    max-width: 480px;
    width: 100%;
    max-height: 90vh;
    display: flex;
    flex-direction: column;
  }
  .upload-dialog h3 {
    margin-bottom: 4px;
    color: var(--phosphor);
    font-family: var(--font-mono);
  }
  .upload-file-name {
    color: var(--text-dim);
    font-size: 0.85rem;
    margin-bottom: 4px;
    word-break: break-all;
  }
  .upload-dialog-body {
    flex: 1;
    overflow-y: auto;
    margin: 16px 0;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .match-label {
    font-size: 0.85rem;
    color: var(--text-dim);
  }
  .status-msg {
    padding: 12px;
    text-align: center;
    color: var(--text-dim);
    font-style: italic;
  }
  .status-msg.error-msg {
    color: #ff6666;
    font-style: normal;
  }

  /* IGDB results list inside dialog */
  .upload-igdb-results {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-height: 240px;
    overflow-y: auto;
  }
  .igdb-result {
    display: flex;
    gap: 10px;
    align-items: center;
    background: var(--surface-hover);
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
    width: 42px;
    height: 56px;
    object-fit: cover;
    border-radius: 4px;
    flex-shrink: 0;
  }
  .igdb-thumb-placeholder {
    width: 42px;
    height: 56px;
    background: var(--surface);
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
    font-size: 0.85rem;
    color: var(--text-bright);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .igdb-meta {
    font-size: 0.75rem;
    color: var(--text-dim);
  }
  .igdb-dev {
    display: block;
    font-size: 0.7rem;
    color: var(--text-dim);
    margin-top: 1px;
  }

  .upload-custom-row {
    display: flex;
    gap: 8px;
  }
  .upload-custom-row input {
    flex: 1;
  }

  .btn-cancel {
    border-color: var(--border);
    color: var(--text-dim);
  }
  .btn-cancel:hover {
    border-color: var(--text-dim);
    color: var(--text);
  }
  .btn-secondary {
    border-color: var(--border);
    color: var(--text);
  }
  .btn-secondary:hover {
    border-color: var(--text-dim);
  }
  .btn-upload-anyway {
    margin-top: 8px;
    width: 100%;
    justify-content: center;
    border-color: #cc8833;
    color: #ffaa44;
  }
  .btn-upload-anyway:hover {
    background: #332211;
    border-color: #ffaa44;
  }

  /* Upload Progress spinner */
  .upload-progress {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 40px 20px;
    text-align: center;
  }
  .upload-progress-spinner {
    width: 40px;
    height: 40px;
    border: 3px solid var(--border);
    border-top: 3px solid var(--phosphor);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
    margin-bottom: 16px;
  }
  @keyframes spin {
    to { transform: rotate(360deg); }
  }
  .upload-progress-text {
    color: var(--text);
    font-size: 1rem;
    margin-bottom: 4px;
  }
  .upload-progress-sub {
    color: var(--text-dim);
    font-size: 0.85rem;
  }
  .upload-progress-note {
    color: var(--text-dim);
    font-size: 0.75rem;
    margin-top: 8px;
    opacity: 0.7;
  }
</style>
