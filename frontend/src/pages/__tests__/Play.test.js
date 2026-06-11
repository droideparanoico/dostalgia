import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/svelte";
import Play from "../Play.svelte";

// Mock the API module that Play imports
vi.mock("../../lib/api.js", () => ({
  getGame: vi.fn(),
  bundleUrl: vi.fn(),
  setupBundleUrl: vi.fn(),
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
});
