import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/svelte";
import Library from "../Library.svelte";

// Mock the API module that Library imports
vi.mock("../../lib/api.js", () => ({
  listGames: vi.fn(),
  uploadGame: vi.fn(),
  searchIGDB: vi.fn(),
}));

import * as api from "../../lib/api.js";

function makeGame(overrides = {}) {
  return {
    id: "g1",
    title: "Test Game",
    year: 1995,
    genre: "Adventure",
    platform: "dos",
    has_cover: false,
    ...overrides,
  };
}

describe("Library", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.location.hash = "#/";
  });

  it("shows loading state initially", () => {
    api.listGames.mockReturnValue(new Promise(() => {})); // never resolves
    render(Library);
    expect(screen.getByText("Scanning library...")).toBeTruthy();
  });

  it("shows empty state when no games", async () => {
    api.listGames.mockResolvedValue({ games: [] });
    render(Library);
    await vi.waitFor(() => {
      expect(screen.getByText("No games yet")).toBeTruthy();
    });
  });

  it("renders game cards when games load", async () => {
    api.listGames.mockResolvedValue({
      games: [
        makeGame({ id: "g1", title: "Game One" }),
        makeGame({ id: "g2", title: "Game Two" }),
      ],
    });
    render(Library);
    await vi.waitFor(() => {
      expect(screen.getByText("Game One")).toBeTruthy();
      expect(screen.getByText("Game Two")).toBeTruthy();
    });
  });

  it("shows game count", async () => {
    api.listGames.mockResolvedValue({
      games: [
        makeGame({ id: "g1", title: "One" }),
        makeGame({ id: "g2", title: "Two" }),
      ],
    });
    render(Library);
    await vi.waitFor(() => {
      expect(screen.getByText("2 games")).toBeTruthy();
    });
  });

  it("shows singular game count for one game", async () => {
    api.listGames.mockResolvedValue({
      games: [makeGame({ id: "g1", title: "Only" })],
    });
    render(Library);
    await vi.waitFor(() => {
      expect(screen.getByText("1 game")).toBeTruthy();
    });
  });

  describe("filters", () => {
    it("shows filter options derived from loaded games", async () => {
      api.listGames.mockResolvedValue({
        games: [
          makeGame({ id: "g1", genre: "RPG", year: 1997 }),
          makeGame({ id: "g2", genre: "RPG", year: 1998 }),
          makeGame({ id: "g3", genre: "Adventure", year: 1995 }),
        ],
      });
      render(Library);
      await vi.waitFor(() => {
        // Year labels appear both as filter options and in game cards
        expect(screen.getAllByText("1995").length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText("1997").length).toBeGreaterThanOrEqual(1);
        expect(screen.getAllByText("1998").length).toBeGreaterThanOrEqual(1);
      });
    });

    it("applies genre filter from props", async () => {
      api.listGames.mockResolvedValue({
        games: [
          makeGame({ id: "g1", title: "RPG Game", genre: "RPG" }),
          makeGame({ id: "g2", title: "Adventure Game", genre: "Adventure" }),
        ],
      });
      render(Library, { props: { genreFilter: "RPG" } });
      await vi.waitFor(() => {
        expect(screen.getByText("RPG Game")).toBeTruthy();
        expect(screen.queryByText("Adventure Game")).toBeNull();
      });
    });

    it("applies year filter from props", async () => {
      api.listGames.mockResolvedValue({
        games: [
          makeGame({ id: "g1", title: "Old Game", year: 1993 }),
          makeGame({ id: "g2", title: "New Game", year: 1997 }),
        ],
      });
      render(Library, { props: { yearFilter: "1997" } });
      await vi.waitFor(() => {
        expect(screen.getByText("New Game")).toBeTruthy();
        expect(screen.queryByText("Old Game")).toBeNull();
      });
    });

    it("shows filter-empty message when no filter options exist", async () => {
      api.listGames.mockResolvedValue({
        games: [
          makeGame({ id: "g1", genre: null, year: null }),
          makeGame({ id: "g2", genre: null, year: null }),
        ],
      });
      render(Library);
      await vi.waitFor(() => {
        const dashes = screen.getAllByText("—");
        expect(dashes.length).toBeGreaterThanOrEqual(2);
      });
    });
  });
});
