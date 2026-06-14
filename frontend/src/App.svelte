<script>
  import "./app.css";
  import Header from "./lib/Header.svelte";
  import Library from "./pages/Library.svelte";
  import GameDetail from "./pages/GameDetail.svelte";
  import Play from "./pages/Play.svelte";

  let route = $state("/");
  let gameId = $state(null);
  let filterGenre = $state("");
  let filterYear = $state("");
  let uploadTriggered = $state(0);

  function parseHash() {
    const hash = window.location.hash.slice(1) || "/";
    const [pathPart, qs] = hash.split("?");
    const parts = pathPart.split("/").filter(Boolean);
    if (parts.length === 0 || (parts.length === 1 && parts[0] === "")) {
      route = "/";
      gameId = null;
      // Parse filter query params
      const params = new URLSearchParams(qs || "");
      filterGenre = params.get("genre") || "";
      filterYear = params.get("year") || "";
    } else if (parts[0] === "game" && parts[1]) {
      route = "/game";
      gameId = parts[1];
    } else if (parts[0] === "play" && parts[1]) {
      route = "/play";
      gameId = parts[1];
    } else {
      route = "/";
      gameId = null;
    }
  }

  // Listen for hash changes and page loads
  $effect(() => {
    parseHash();
    const handler = () => parseHash();
    window.addEventListener("hashchange", handler);
    return () => window.removeEventListener("hashchange", handler);
  });
</script>

<Header onUploadClick={() => uploadTriggered++} hideUpload={route !== "/"} />

<main class="container">
  {#if route === "/"}
    <Library genreFilter={filterGenre} yearFilter={filterYear} {uploadTriggered} />
  {:else if route === "/game" && gameId}
    <GameDetail id={gameId} />
  {:else if route === "/play" && gameId}
    <Play id={gameId} />
  {:else}
    <div class="empty">
      <h2>Page not found</h2>
      <a href="#/">Back to Library</a>
    </div>
  {/if}
</main>

<style>
  .empty {
    text-align: center;
    padding: 80px 20px;
  }
  .empty h2 {
    margin-bottom: 16px;
  }
</style>
