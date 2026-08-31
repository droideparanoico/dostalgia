import { describe, it, expect, vi, afterEach } from "vitest";
import {
  artworkUrl,
  bundleUrl,
  setupBundleUrl,
  pullSaves,
  pushSaves,
  deleteSaves,
} from "../api.js";

describe("artworkUrl", () => {
  it("returns null for null input", () => {
    expect(artworkUrl(null)).toBeNull();
  });

  it("returns null for undefined input", () => {
    expect(artworkUrl(undefined)).toBeNull();
  });

  it("passes through absolute HTTP URLs", () => {
    expect(artworkUrl("https://example.com/cover.jpg")).toBe(
      "https://example.com/cover.jpg"
    );
  });

  it("proxies relative paths through /artwork/", () => {
    expect(artworkUrl("images/game.png")).toBe("/artwork/images/game.png");
  });

  it("preserves nested relative paths", () => {
    expect(artworkUrl("some/deep/path/file.webp")).toBe(
      "/artwork/some/deep/path/file.webp"
    );
  });
});

describe("bundleUrl", () => {
  it("returns null for null game", () => {
    expect(bundleUrl(null)).toBeNull();
  });

  it("returns null for undefined game", () => {
    expect(bundleUrl(undefined)).toBeNull();
  });

  it("builds URL from bundle_file", () => {
    expect(bundleUrl({ bundle_file: "mygame.zip" })).toBe("/games/mygame.zip");
  });

  it("preserves subdirectory bundle paths", () => {
    expect(bundleUrl({ bundle_file: "subdir/game.zip" })).toBe(
      "/games/subdir/game.zip"
    );
  });
});

describe("setupBundleUrl", () => {
  it("returns null for null game", () => {
    expect(setupBundleUrl(null)).toBeNull();
  });

  it("returns null for undefined game", () => {
    expect(setupBundleUrl(undefined)).toBeNull();
  });

  it("builds API setup URL from game id", () => {
    expect(setupBundleUrl({ id: "game-1" })).toBe(
      "/api/games/game-1/setup-bundle"
    );
  });

  it("handles games with numeric id", () => {
    expect(setupBundleUrl({ id: 42 })).toBe("/api/games/42/setup-bundle");
  });
});

describe("pullSaves", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns null when there is no save (404)", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ status: 404, ok: false }));
    await expect(pullSaves("game-1")).resolves.toBeNull();
    expect(fetch).toHaveBeenCalledWith("/api/games/game-1/saves");
  });

  it("returns the raw bytes on success", async () => {
    const bytes = new Uint8Array([1, 2, 3]);
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        status: 200,
        ok: true,
        arrayBuffer: () => Promise.resolve(bytes.buffer),
      })
    );
    await expect(pullSaves("game-1")).resolves.toEqual(bytes);
  });

  it("throws on server error", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        status: 500,
        ok: false,
        json: () => Promise.resolve({ error: "boom" }),
      })
    );
    await expect(pullSaves("game-1")).rejects.toThrow("boom");
  });
});

describe("pushSaves", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("PUTs the raw bundle to the saves endpoint", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      status: 200,
      ok: true,
      json: () => Promise.resolve({ status: "saved" }),
    });
    vi.stubGlobal("fetch", fetchMock);
    const data = new Uint8Array([9, 8, 7]);

    await expect(pushSaves("game-1", data)).resolves.toEqual({
      status: "saved",
    });

    const [url, opts] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/games/game-1/saves");
    expect(opts.method).toBe("PUT");
    expect(opts.headers["Content-Type"]).toBe("application/octet-stream");
    expect(opts.body).toBe(data);
  });

  it("throws on server error", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        status: 400,
        ok: false,
        json: () => Promise.resolve({ error: "Empty save data" }),
      })
    );
    await expect(pushSaves("game-1", new Uint8Array())).rejects.toThrow(
      "Empty save data"
    );
  });
});

describe("deleteSaves", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("DELETEs the saves endpoint", async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      status: 200,
      ok: true,
      json: () => Promise.resolve({ status: "deleted" }),
    });
    vi.stubGlobal("fetch", fetchMock);

    await expect(deleteSaves("game-1")).resolves.toEqual({ status: "deleted" });
    expect(fetchMock.mock.calls[0]).toEqual([
      "/api/games/game-1/saves",
      { method: "DELETE" },
    ]);
  });

  it("throws on server error", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        status: 500,
        ok: false,
        json: () => Promise.resolve({ error: "boom" }),
      })
    );
    await expect(deleteSaves("game-1")).rejects.toThrow("boom");
  });
});
