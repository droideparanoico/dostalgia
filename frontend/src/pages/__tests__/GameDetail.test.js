import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/svelte";
import GameDetail from "../GameDetail.svelte";

// Mock api.js
vi.mock("../../lib/api.js", () => ({
  getGame: vi.fn(),
  deleteGame: vi.fn(),
  updateGame: vi.fn(),
  searchIGDB: vi.fn(),
  scrapeIGDB: vi.fn(),
  downloadGame: vi.fn(),
  bundleUrl: vi.fn(),
  setupBundleUrl: vi.fn(),
  artworkUrl: vi.fn(),
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
    year: 1991,
    genre: "Platformer",
    developer: "id Software",
    publisher: "Apogee",
    description: "A classic DOS platformer.",
    platform: "dos",
    ready: true,
    has_cover: false,
    has_setup: false,
    bundle_file: "commander-keen.zip",
    bundle_size: 512000,
    executables: ["keen.exe"],
    executable: null,
    videos: [],
    screenshots: [],
    ...overrides,
  };
}

describe("GameDetail", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows loading state initially", () => {
    api.getGame.mockReturnValue(new Promise(() => {}));
    render(GameDetail, { props: { id: "game-1" } });
    expect(screen.getByText("Loading...")).toBeTruthy();
  });

  it("shows error when game is not found", async () => {
    api.getGame.mockRejectedValue(new Error("Game not found"));
    render(GameDetail, { props: { id: "missing" } });
    await vi.waitFor(() => {
      expect(screen.getByText("Game not found")).toBeTruthy();
    });
  });

  it("renders game title", async () => {
    api.getGame.mockResolvedValue(makeGame());
    render(GameDetail, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(screen.getByText("Commander Keen")).toBeTruthy();
    });
  });

  it("renders developer and publisher", async () => {
    api.getGame.mockResolvedValue(
      makeGame({ developer: "id Software", publisher: "Apogee" })
    );
    render(GameDetail, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(screen.getByText(/id Software/)).toBeTruthy();
      expect(screen.getByText(/Apogee/)).toBeTruthy();
    });
  });

  it("renders year and genre as links", async () => {
    api.getGame.mockResolvedValue(
      makeGame({ year: 1991, genre: "Platformer" })
    );
    render(GameDetail, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(screen.getByText("1991").closest("a")).toBeTruthy();
      expect(screen.getByText("Platformer").closest("a")).toBeTruthy();
    });
  });

  it("shows DOS badge for dos platform", async () => {
    api.getGame.mockResolvedValue(makeGame({ platform: "dos" }));
    render(GameDetail, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(screen.getByText("💾 DOS")).toBeTruthy();
    });
  });

  it("shows Windows badge for windows platform", async () => {
    api.getGame.mockResolvedValue(makeGame({ platform: "windows" }));
    render(GameDetail, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(screen.getByText(/Windows 3\.1/)).toBeTruthy();
    });
  });

  it("shows Play button for ready DOS games", async () => {
    api.getGame.mockResolvedValue(
      makeGame({ ready: true, platform: "dos" })
    );
    render(GameDetail, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(screen.getByText("▶ Play")).toBeTruthy();
    });
  });

  it("shows disabled Play button for Windows games", async () => {
    api.getGame.mockResolvedValue(
      makeGame({ ready: true, platform: "windows" })
    );
    render(GameDetail, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(screen.getByText("▶ Unplayable")).toBeTruthy();
    });
  });

  it("shows Setup button when has_setup is true", async () => {
    api.getGame.mockResolvedValue(
      makeGame({ has_setup: true })
    );
    render(GameDetail, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(screen.getByText("🛠 Setup")).toBeTruthy();
    });
  });

  it("shows Edit, Download, Delete buttons", async () => {
    api.getGame.mockResolvedValue(makeGame());
    render(GameDetail, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(screen.getByText("✏ Edit")).toBeTruthy();
      expect(screen.getByText("⬇ Download")).toBeTruthy();
      expect(screen.getByText("✕ Delete")).toBeTruthy();
    });
  });

  it("enters edit mode when Edit button is clicked", async () => {
    api.getGame.mockResolvedValue(makeGame());
    render(GameDetail, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(screen.getByText("✏ Edit")).toBeTruthy();
    });
    screen.getByText("✏ Edit").click();
    await vi.waitFor(() => {
      expect(screen.getByText("Save")).toBeTruthy();
      expect(screen.getByText("Cancel")).toBeTruthy();
    });
  });

  it("shows cover placeholder when no cover", async () => {
    api.getGame.mockResolvedValue(makeGame({ has_cover: false }));
    render(GameDetail, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(screen.getByText("💾")).toBeTruthy();
    });
  });

  it("shows description when provided", async () => {
    api.getGame.mockResolvedValue(
      makeGame({ description: "A classic DOS platformer." })
    );
    render(GameDetail, { props: { id: "game-1" } });
    await vi.waitFor(() => {
      expect(
        screen.getByText("A classic DOS platformer.")
      ).toBeTruthy();
    });
  });
});
