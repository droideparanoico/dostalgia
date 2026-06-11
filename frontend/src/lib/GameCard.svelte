<script>
  import { push } from "../lib/router.js";

  let { game } = $props();

  function navigate() {
    push(`/game/${game.id}`);
  }
</script>

<button class="card crt-hover" onclick={navigate}>
  <div class="cover">
    {#if game.has_cover}
      <img src={`/games/${game.id}/cover.jpg`} alt={game.title} loading="lazy" />
    {:else}
      <div class="cover-placeholder">
        <span class="cover-icon">💾</span>
        
      </div>
    {/if}
  </div>
  <div class="info">
    <h3>{game.title}</h3>
    {#if game.year}
      <span class="year">{game.year}</span>
    {/if}
    {#if game.genre}
      <span class="genre">{game.genre}</span>
    {/if}
  </div>
  {#if game.platform === 'windows'}
    <div class="badge badge-windows">🪟 Win</div>
  {/if}
</button>

<style>
  .card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    overflow: hidden;
    transition: all 0.2s ease;
    cursor: pointer;
    text-align: left;
    display: flex;
    flex-direction: column;
    position: relative;
    width: 100%;
  }
  .card:hover {
    border-color: var(--phosphor-dim);
    transform: translateY(-2px);
    box-shadow: 0 4px 20px rgba(51, 255, 51, 0.1);
  }
  .cover {
    aspect-ratio: 3/4;
    background: var(--bg-elevated);
    display: flex;
    align-items: center;
    justify-content: center;
    overflow: hidden;
  }
  .cover img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  .cover-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    color: var(--text-dim);
  }
  .cover-icon {
    font-size: 2.5rem;
  }
  .info {
    padding: 12px;
    flex: 1;
  }
  .info h3 {
    font-size: 0.95rem;
    margin-bottom: 4px;
    color: var(--text-bright);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  @media (max-width: 480px) {
    .info { padding: 8px; }
    .info h3 { font-size: 0.8rem; }
    .year, .genre { font-size: 0.7rem; }
    .badge { font-size: 0.6rem; padding: 1px 6px; top: 4px; right: 4px; }
  }
  .year, .genre {
    font-size: 0.8rem;
    color: var(--text-dim);
    display: inline-block;
    margin-right: 8px;
  }
  .badge {
    position: absolute;
    top: 8px;
    right: 8px;
    background: var(--phosphor-dark);
    color: var(--phosphor);
    font-size: 0.7rem;
    padding: 2px 8px;
    border-radius: 10px;
    font-weight: 600;
    letter-spacing: 0.05em;
  }
  .badge-windows {
    background: #1a3a5c;
    color: #6ab0ff;
  }
</style>