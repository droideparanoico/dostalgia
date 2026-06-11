import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/svelte";
import GameCard from "../GameCard.svelte";

function makeGame(overrides = {}) {
  return {
    id: "test-1",
    title: "Test Game",
    year: 1995,
    genre: "Adventure",
    platform: "dos",
    has_cover: false,
    ...overrides,
  };
}

describe("GameCard", () => {
  it("renders the game title", () => {
    render(GameCard, { props: { game: makeGame() } });
    expect(screen.getByText("Test Game")).toBeTruthy();
  });

  it("renders the year when provided", () => {
    render(GameCard, { props: { game: makeGame({ year: 1997 }) } });
    expect(screen.getByText("1997")).toBeTruthy();
  });

  it("does not render year when absent", () => {
    render(GameCard, { props: { game: makeGame({ year: null }) } });
    expect(screen.queryByText("1995")).toBeNull();
  });

  it("renders the genre when provided", () => {
    render(GameCard, { props: { game: makeGame({ genre: "RPG" }) } });
    expect(screen.getByText("RPG")).toBeTruthy();
  });

  it("shows a cover image when has_cover is true", () => {
    const game = makeGame({ has_cover: true });
    render(GameCard, { props: { game } });
    const img = screen.getByRole("img");
    expect(img).toBeTruthy();
    expect(img.getAttribute("src")).toBe(`/games/${game.id}/cover.jpg`);
    expect(img.getAttribute("alt")).toBe(game.title);
  });

  it("shows a placeholder when has_cover is false", () => {
    render(GameCard, { props: { game: makeGame({ has_cover: false }) } });
    expect(screen.getByText("💾")).toBeTruthy();
  });

  it("shows Windows badge for windows platform", () => {
    render(GameCard, {
      props: { game: makeGame({ platform: "windows" }) },
    });
    expect(screen.getByText(/🪟/)).toBeTruthy();
  });

  it("does not show Windows badge for DOS platform", () => {
    render(GameCard, { props: { game: makeGame({ platform: "dos" }) } });
    expect(screen.queryByText(/🪟/)).toBeNull();
  });

  it("navigates on click", async () => {
    window.location.hash = "";
    render(GameCard, { props: { game: makeGame() } });
    const btn = screen.getByRole("button");
    btn.click();
    expect(window.location.hash).toBe("#/game/test-1");
  });
});
