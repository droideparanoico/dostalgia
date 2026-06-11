import { describe, it, expect } from "vitest";
import { artworkUrl, bundleUrl, setupBundleUrl } from "../api.js";

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
