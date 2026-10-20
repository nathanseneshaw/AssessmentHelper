import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { App } from "../../App";
import { STORAGE_KEY } from "../../auth/AuthContext";

function observeeHeading() {
  return screen.getByRole("heading", { level: 2, name: /^Observee:/ });
}

function candidateNames() {
  const card = screen
    .getByRole("heading", { name: "Eligible observers" })
    .closest("section");
  if (!card) throw new Error("No eligible observers card");
  return within(card)
    .queryAllByRole("listitem")
    .map((item) => item.firstElementChild?.textContent);
}

function press(name: "Previous observee" | "Next observee") {
  fireEvent.click(screen.getByRole("button", { name }));
}

beforeEach(async () => {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({ role: "ac", name: "Test User" }),
  );
  window.history.pushState({}, "", "/ac/matching");
  render(<App />);
  await screen.findByRole("heading", { level: 1, name: "Observer Matching" });
});

afterEach(() => {
  cleanup();
  localStorage.clear();
});

describe("observer matching", () => {
  test("opens on Professor 2 with five eligible observers", () => {
    expect(observeeHeading().textContent).toBe("Observee: Professor 2");
    expect(screen.getByText("MW 10:00–11:15")).toBeDefined();
    expect(candidateNames()).toEqual([
      "Professor 6",
      "Professor 7",
      "Professor 8",
      "Professor 9",
      "Professor 10",
    ]);
  });

  test("the arrows cycle through observees and wrap at both ends", () => {
    press("Next observee");
    expect(observeeHeading().textContent).toBe("Observee: Professor 4");
    expect(candidateNames()).toEqual([]);
    expect(screen.getByText(/^No eligible observers\./)).toBeDefined();

    press("Next observee");
    expect(observeeHeading().textContent).toBe("Observee: Professor 1");
    expect(candidateNames()).toEqual([
      "Professor 3",
      "Professor 11",
      "Professor 12",
    ]);

    press("Previous observee");
    expect(observeeHeading().textContent).toBe("Observee: Professor 4");
    press("Previous observee");
    press("Previous observee");
    expect(observeeHeading().textContent).toBe("Observee: Professor 1");
  });

  test("announces the new observee to screen readers", () => {
    const status = screen.getByRole("status");
    expect(status.textContent).toBe(
      "Observee 2 of 3: Professor 2, 5 eligible observers.",
    );
    press("Next observee");
    expect(status.textContent).toBe(
      "Observee 3 of 3: Professor 4, no eligible observers.",
    );
  });
});
