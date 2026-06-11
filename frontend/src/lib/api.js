/** API client for DOStalgia backend. */

const BASE = import.meta.env.PROD ? "" : "";  // Proxy handles it in dev

async function request(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    headers: { "Content-Type": "application/json", ...options.headers },
    ...options,
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ detail: res.statusText }));
    throw new Error(err.detail || `HTTP ${res.status}`);
  }
  return res.json();
}

/** Fetch game list */
export async function listGames(params = {}) {
  const qs = new URLSearchParams();
  if (params.search) qs.set("search", params.search);
  if (params.genre) qs.set("genre", params.genre);
  if (params.limit) qs.set("limit", params.limit);
  if (params.offset) qs.set("offset", params.offset);
  return request(`/api/games?${qs}`);
}

/** Fetch single game */
export async function getGame(id) {
  return request(`/api/games/${id}`);
}

/** Update game metadata */
export async function updateGame(id, data) {
  return request(`/api/games/${id}`, {
    method: "PATCH",
    body: JSON.stringify(data),
  });
}

/** Delete a game */
export async function deleteGame(id) {
  return request(`/api/games/${id}`, { method: "DELETE" });
}

/** Upload a game ZIP */
export async function uploadGame(file, title, igdbId) {
  const form = new FormData();
  form.append("file", file);
  if (title) form.append("title", title);
  if (igdbId) form.append("igdb_id", String(igdbId));

  const res = await fetch(`${BASE}/api/upload`, {
    method: "POST",
    body: form,
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ detail: res.statusText }));
    throw new Error(err.detail || `HTTP ${res.status}`);
  }
  return res.json();
}

/** Search IGDB for a game title (requires TWITCH_CLIENT_ID/SECRET set) */
export async function searchIGDB(query) {
  const res = await fetch(`${BASE}/api/igdb/search?q=${encodeURIComponent(query)}`);
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(err.error || `HTTP ${res.status}`);
  }
  return res.json();
}

/** Apply IGDB metadata + cover to a game (auto or by igdb_id) */
export async function scrapeIGDB(gameId, igdbId) {
  const body = igdbId ? { igdb_id: igdbId } : {};
  const res = await fetch(`${BASE}/api/igdb/scrape/${gameId}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(err.error || `HTTP ${res.status}`);
  }
  return res.json();
}

/** Download a game's ZIP bundle */
export function downloadGame(id) {
  window.open(`${BASE}/api/games/${id}/download`, '_blank');
}

/** Check IGDB status */
export async function igdbStatus() {
  const res = await fetch(`${BASE}/api/igdb/status`);
  return res.json();
}

/** Get artwork URL for a path — returns proxy URL or placeholder */
export function artworkUrl(path) {
  if (!path) return null;
  if (path.startsWith("http")) return path;
  return `/artwork/${path}`;
}

/** Get game bundle URL for normal play */
export function bundleUrl(game) {
  if (!game) return null;
  // Use the bundle_file path from the backend (handles both old flat layout
  // and new in-directory layout transparently)
  return `/games/${game.bundle_file}`;
}

/** Get game bundle URL for setup mode (runs SETUP.EXE instead) */
export function setupBundleUrl(game) {
  if (!game) return null;
  return `/api/games/${game.id}/setup-bundle`;
}
