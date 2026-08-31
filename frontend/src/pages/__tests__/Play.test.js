import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/svelte";
import Play from "../Play.svelte";

// Mock the API module that Play imports
vi.mock("../../lib/api.js", () => ({
  getGame: vi.fn(),
  bundleUrl: vi.fn(),
  setupBundleUrl: vi.fn(),
  pullSaves: vi.fn(),
  pushSaves: vi.fn(),
  deleteSaves: vi.fn(),
}));

// Mock router
vi.mock("../../lib/router.js", () => ({
  push: vi.fn(),
}));

import * as api from "../../lib/api.js";

function makeGame(overrides = {}) {
  return {
    id: "game-1",
    title: "Commander Keen",
    platform: "dos",
    ready: true,
    ...overrides,
  };
}

describe("Play", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows loading state initially", () => {
    api.getGame.mockReturnValue(new Promise(() => {}));
    render(Play, { props: { id: "game-1" } });
    expect(screen.getByText("Loading game data...")).toBeTruthy();
  });

  it("shows not-ready state when game is not processed", async () => {
    api.getGame.mockResolvedValue(makeGame({ ready: false }));
    render(Play, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(screen.getByText("Not ready")).toBeTruthy();
    });
  });

  it("shows unsupported warning for Windows games", async () => {
    api.getGame.mockResolvedValue(
      makeGame({ platform: "windows", ready: true })
    );
    render(Play, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(screen.getByText("Windows game")).toBeTruthy();
    });
  });

  it("shows error box when game fetch fails", async () => {
    api.getGame.mockRejectedValue(new Error("Game not found"));
    render(Play, { props: { id: "missing" } });
    // The error should appear in the overlay as well
    await vi.waitFor(() => {
      expect(screen.getByText("⚠️")).toBeTruthy();
    });
  });

  it("shows emulator booting state for ready DOS games", async () => {
    api.getGame.mockResolvedValue(
      makeGame({ platform: "dos", ready: true })
    );
    api.bundleUrl.mockReturnValue("/games/game-1.jsdos");
    render(Play, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(screen.getByText("Starting emulator...")).toBeTruthy();
    });
  });

  it("starts js-dos with server-backed save hooks", async () => {
    api.getGame.mockResolvedValue(makeGame());
    api.bundleUrl.mockReturnValue("/games/game-1.jsdos");

    const dosMock = vi.fn().mockReturnValue({
      save: vi.fn().mockResolvedValue(true),
      stop: vi.fn().mockResolvedValue(undefined),
    });
    window.Dos = dosMock;

    try {
      render(Play, { props: { id: "game-1" } });
      await vi.waitFor(() => {
        expect(dosMock).toHaveBeenCalled();
      });

      const opts = dosMock.mock.calls[0][1];
      expect(opts.url).toBe("/games/game-1.jsdos");
      expect(opts.autoSave).toBe(true);

      // Both normal and setup bundles map to the same per-game save key
      await expect(opts.fsChanges.urlToKey("/games/game-1.jsdos")).resolves.toBe("game-1");

      // pull() forwards to the API and returns null when there is no save
      api.pullSaves.mockResolvedValue(null);
      await expect(opts.fsChanges.pull("game-1")).resolves.toBeNull();
      expect(api.pullSaves).toHaveBeenCalledWith("game-1");

      // push()/delete() forward to the API
      api.pushSaves.mockResolvedValue({ status: "saved" });
      await opts.fsChanges.push("game-1", new Uint8Array([1]));
      expect(api.pushSaves).toHaveBeenCalledWith("game-1", expect.any(Uint8Array));

      api.deleteSaves.mockResolvedValue({ status: "deleted" });
      await opts.fsChanges.delete("game-1");
      expect(api.deleteSaves).toHaveBeenCalledWith("game-1");

      // pull() survives API errors so the game still boots
      api.pullSaves.mockRejectedValue(new Error("offline"));
      await expect(opts.fsChanges.pull("game-1")).resolves.toBeNull();
    } finally {
      delete window.Dos;
    }
  });
});
