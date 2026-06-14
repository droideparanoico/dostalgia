<script>
  import { getGame, bundleUrl, setupBundleUrl } from "../lib/api.js";
  import { push } from "../lib/router.js";

  let { id } = $props();

  // Read query params from the hash — window.location is available after mount
  let isSetup = $state(false);

  let game = $state(null);
  let loading = $state(true);
  let error = $state("");
  let booting = $state(false);
  let running = $state(false);
  let unsupported = $state(false);
  let started = false;

  let dosContainer;
  let dosCI = null;
  let injectedLink = null;
  let injectedScript = null;

  async function load() {
    loading = true;
    // Detect ?setup=1 from the hash-based URL
    isSetup = window.location.hash.includes("setup=1");
    try {
      game = await getGame(id);
      // Windows games can't run in pure DOSBox — flag it
      if (game.platform === 'windows') {
        unsupported = true;
      }
    } catch (e) {
      error = "Game not found";
    }
    loading = false;
  }

  async function startEmulator() {
    if (started || !game?.ready || !dosContainer) return;
    started = true;
    booting = true;

    // Load js-dos CSS
    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href = "https://v8.js-dos.com/latest/js-dos.css";
    document.head.appendChild(link);
    injectedLink = link;

    // Load js-dos script from CDN
    if (!window.Dos) {
      try {
        await new Promise((resolve, reject) => {
          const s = document.createElement("script");
          s.src = "https://v8.js-dos.com/latest/js-dos.js";
          s.onload = resolve;
          s.onerror = reject;
          document.head.appendChild(s);
          injectedScript = s;
        });
      } catch {
        error = "Failed to load emulator. Check your internet connection.";
        booting = false;
        return;
      }
    }

    // Start the DOS emulator
    try {
      const url = isSetup ? setupBundleUrl(game) : bundleUrl(game);
      if (!url || !window.Dos) throw new Error("Failed to resolve game bundle");
      dosCI = await window.Dos(dosContainer, { url });
      running = true;
    } catch (e) {
      error = e.message;
    }
    booting = false;
  }

  function stopEmulator() {
    // Force full page reload which terminates all workers, AudioContexts
    // and WASM threads. Hash is preserved so the SPA routes correctly.
    window.location.hash = `#/game/${game.id}`;
    window.location.reload();
  }

  function handleTryAnyway() {
    unsupported = false;
    startEmulator();
  }

  function goBack() {
    push(`/game/${id}`);
  }

  $effect(() => { load(); });

  // Auto-start when both game data and the container exist
  $effect(() => {
    if (game?.ready && dosContainer && !started && !unsupported) {
      startEmulator();
    }
  });

  // Cleanup emulator on component unmount (browser back, nav away)
  $effect(() => {
    return () => {
      if (dosCI) {
        try { dosCI.exit(); } catch (_) {}
        dosCI = null;
      }
      if (dosContainer) {
        dosContainer.innerHTML = "";
      }
      // Remove injected js-dos CSS so it doesn't corrupt other pages
      if (injectedLink && injectedLink.parentNode) {
        injectedLink.parentNode.removeChild(injectedLink);
        injectedLink = null;
      }
      if (injectedScript && injectedScript.parentNode) {
        injectedScript.parentNode.removeChild(injectedScript);
        injectedScript = null;
      }
    };
  });
</script>

<div class="play-page">
  <!-- Top bar: shown when game is loaded -->
  <div class="play-header" class:active={game}>
    {#if game}
      <button class="link-button" onclick={stopEmulator}>
        ← {isSetup ? "Back" : game.title}
      </button>
      {#if running}
        <button class="btn btn-danger" onclick={stopEmulator}>⏹ Stop</button>
      {/if}
    {/if}
  </div>

  <!-- Error state -->
  {#if error && !booting}
    <div class="error-box">{error}</div>
  {/if}

  <!-- Unsupported platform warning -->
  {#if unsupported}
    <div class="overlay">
      <div class="overlay-icon">🪟</div>
      <h2>Windows game</h2>
      <p><strong>{game.title}</strong> is a Windows application and won't run in the browser DOS emulator without Windows 3.1 installed.</p>
      <div class="unsupported-actions">
        <button class="btn btn-secondary" onclick={goBack}>← Back</button>
        <button class="btn" onclick={handleTryAnyway}>Try anyway</button>
      </div>
    </div>
  {/if}

  <!-- Loading / booting overlay (covers the canvas while loading) -->
  {#if !running}
    <div class="overlay">
      {#if loading}
        <div class="overlay-icon">⏳</div>
        <p>Loading game data...</p>
      {:else if booting}
        <div class="overlay-icon">⚙️</div>
        <h2>{game?.title || ""}</h2>
        {#if isSetup}
          <p>Starting setup utility...</p>
          <p class="save-hint">⚙️ Configure controls, then exit SETUP to save</p>
        {:else}
          <p>Starting emulator...</p>
          <p class="save-hint">💾 Game saves are stored automatically</p>
        {/if}
      {:else if error}
        <div class="overlay-icon">⚠️</div>
        <h2>Failed to start</h2>
        <p>{error}</p>
      {:else if !game?.ready}
        <div class="overlay-icon">🕹️</div>
        <h2>Not ready</h2>
        <p>This game hasn't been processed yet.</p>
      {/if}
    </div>
  {/if}

  <!-- dos-container is always in the DOM so bind:this works on first render -->
  <div bind:this={dosContainer} class="dos-container"></div>
</div>

<style>
  .play-page { padding: 16px 0; position: relative; }
  @media (max-width: 640px) { .play-page { padding: 8px 0; } }
  .play-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    opacity: 0;
    transition: opacity 0.2s;
  }
  .play-header.active { opacity: 1; }
  .link-button {
    background: none;
    border: none;
    color: var(--text-dim);
    font-size: 0.9rem;
    cursor: pointer;
    padding: 0;
    font-family: var(--font-sans);
  }
  .link-button:hover { color: var(--phosphor); }

  /* Emulator canvas — always mounted */
  .dos-container {
    width: 100%;
    aspect-ratio: 4/3;
    max-height: 80vh;
    background: #000;
    border-radius: var(--radius);
    overflow: hidden;
    border: 1px solid var(--border);
  }
  @media (max-width: 640px) {
    .dos-container { aspect-ratio: 4/3; max-height: 50vh; border-radius: var(--radius-sm); }
  }
  .dos-container :global(canvas) {
    width: 100% !important;
    height: 100% !important;
  }

  /* Overlay shown while loading / booting, covers the empty container */
  .overlay {
    position: absolute;
    inset: 0;
    top: 50px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    text-align: center;
    background: var(--bg);
    z-index: 10;
    border-radius: var(--radius);
    border: 1px dashed var(--border);
    margin-top: 4px;
    padding: 20px;
  }
  @media (max-width: 640px) {
    .overlay { top: 44px; border-radius: var(--radius-sm); padding: 16px; }
    .overlay-icon { font-size: 2rem; }
    .overlay h2 { font-size: 1rem; }
    .overlay p { font-size: 0.85rem; }
  }
  .overlay-icon { font-size: 3rem; margin-bottom: 12px; }
  .overlay h2 {
    color: var(--phosphor);
    font-family: var(--font-mono);
    margin-bottom: 4px;
  }
  .overlay p { color: var(--text-dim); }
  .save-hint {
    display: inline-block;
    margin-top: 16px;
    padding: 6px 16px;
    border-radius: 12px;
    background: var(--phosphor-burn);
    color: var(--phosphor-dim);
    font-size: 0.85rem;
  }

  .error-box {
    position: absolute;
    inset: 0;
    top: 50px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-top: 4px;
    background: #331111;
    border: 1px solid #cc3333;
    color: #ff6666;
    border-radius: var(--radius);
    z-index: 10;
  }
  .unsupported-actions {
    display: flex;
    gap: 12px;
    margin-top: 20px;
  }
</style>
