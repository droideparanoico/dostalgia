<script>
  let { onUploadClick = () => {}, hideUpload = false } = $props();
</script>

<header class="header">
  <div class="header-inner">
    <a href="#/" class="logo">
      <svg class="logo-icon" width="36" height="36" viewBox="0 0 36 36" fill="none">
        <rect x="4" y="6" width="28" height="22" rx="2" fill="#33FF33" opacity="0.15"/>
        <rect x="6" y="8" width="24" height="16" fill="#0A0A0A"/>
        <rect x="8" y="10" width="20" height="10" fill="#33FF33" opacity="0.3"/>
        <rect x="10" y="12" width="6" height="4" fill="#33FF33" opacity="0.6"/>
        <rect x="18" y="12" width="6" height="4" fill="#33FF33" opacity="0.4"/>
        <rect x="10" y="24" width="16" height="3" fill="#33FF33" opacity="0.2"/>
        <rect x="12" y="29" width="12" height="2" fill="#33FF33" opacity="0.1"/>
      </svg>
      <span class="logo-text">DOSTALGIA</span>
    </a>
    <nav>
      {#if !hideUpload}
        <button class="btn" onclick={onUploadClick}>+ Upload</button>
      {/if}
    </nav>
  </div>
</header>

<style>
  .header {
    height: var(--header-height);
    border-bottom: 1px solid var(--border);
    background: var(--bg-elevated);
    position: sticky;
    top: 0;
    z-index: 100;
  }
  .header-inner {
    max-width: var(--max-width);
    margin: 0 auto;
    padding: 0 24px;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  @media (max-width: 640px) {
    .header-inner { padding: 0 12px; }
  }
  .logo {
    display: flex;
    align-items: center;
    gap: 12px;
    cursor: pointer;
    text-decoration: none;
  }
  .logo-icon {
    flex-shrink: 0;
  }
  .logo-text {
    font-family: var(--font-mono);
    font-size: 1.4rem;
    font-weight: 700;
    color: var(--phosphor);
    letter-spacing: 0.15em;
    animation: phosphor-pulse 3s ease-in-out infinite;
  }
  @media (max-width: 640px) {
    .logo-text { font-size: 1rem; letter-spacing: 0.1em; }
    .logo-icon { width: 28px; height: 28px; }
  }
  nav {
    display: flex;
    gap: 8px;
  }
</style>