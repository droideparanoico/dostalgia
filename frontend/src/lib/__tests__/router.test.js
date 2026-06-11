import { describe, it, expect, vi } from "vitest";
import { push, replace } from "../router.js";

describe("router", () => {
  describe("push", () => {
    it("sets window.location.hash with # prefix", () => {
      window.location.hash = "";
      push("/game/123");
      expect(window.location.hash).toBe("#/game/123");
    });

    it("handles root path", () => {
      window.location.hash = "";
      push("/");
      expect(window.location.hash).toBe("#/");
    });

    it("replaces hash on subsequent calls", () => {
      window.location.hash = "";
      push("/first");
      push("/second");
      expect(window.location.hash).toBe("#/second");
    });
  });

  describe("replace", () => {
    it("sets hash via replace (no back-navigation allowed)", () => {
      window.location.hash = "#/old";
      replace("/new");
      expect(window.location.hash).toBe("#/new");
    });
  });
});
