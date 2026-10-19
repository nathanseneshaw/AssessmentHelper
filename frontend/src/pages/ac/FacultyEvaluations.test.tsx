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

beforeEach(async () => {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({ role: "ac", name: "Test User" }),
  );
  window.history.pushState({}, "", "/ac/evaluations");
  render(<App />);
  await screen.findByRole("heading", { level: 1, name: "Faculty Evaluations" });
});

afterEach(() => {
  cleanup();
  localStorage.clear();
});

// The first cell of each body row (the header row has column headers, not cells).
function facultyShown() {
  return within(screen.getByRole("table"))
    .getAllByRole("row")
    .slice(1)
    .map((row) => within(row).getAllByRole("cell")[0]?.textContent);
}

function filterButton(name: string) {
  const filters = screen.getByRole("group", { name: "Filters:" });
  return within(filters).getByRole("button", { name });
}

describe("evaluation queue", () => {
  test("shows every faculty member in the mockup's order", () => {
    expect(facultyShown()).toEqual([
      "Professor 1",
      "Professor 4",
      "Professor 2",
      "Professor 5",
    ]);
    expect(filterButton("All Faculty").getAttribute("aria-pressed")).toBe(
      "true",
    );
  });

  test("each row's button names the professor", () => {
    expect(
      screen.getByRole("button", { name: "View Evaluation for Professor 1" }),
    ).toBeDefined();
    expect(
      screen.getByRole("button", {
        name: "View Past Evaluations for Professor 4",
      }),
    ).toBeDefined();
  });

  test.each([
    ["Due This Term", ["Professor 1", "Professor 5"]],
    ["Assistant", ["Professor 1"]],
    ["Associate", ["Professor 4", "Professor 2"]],
    ["Full", ["Professor 5"]],
  ])("the %s filter shows %p", (name, expected) => {
    fireEvent.click(filterButton(name));
    expect(facultyShown()).toEqual(expected);
    expect(filterButton(name).getAttribute("aria-pressed")).toBe("true");
    expect(filterButton("All Faculty").getAttribute("aria-pressed")).toBe(
      "false",
    );
  });

  test("a filter with no matches says so, and All Faculty brings everyone back", () => {
    fireEvent.click(filterButton("Overdue"));
    expect(screen.getByText("No faculty match this filter.")).toBeDefined();
    expect(screen.getByText("0 of 4 faculty shown")).toBeDefined();

    fireEvent.click(filterButton("All Faculty"));
    expect(facultyShown()).toHaveLength(4);
    expect(screen.queryByText("No faculty match this filter.")).toBeNull();
  });
});
