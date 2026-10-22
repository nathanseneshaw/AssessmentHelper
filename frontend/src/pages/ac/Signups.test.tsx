import { afterEach, beforeEach, expect, mock, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { ac } from "../../../test/observerFixtures";
import { App } from "../../App";
import { STORAGE_KEY } from "../../auth/AuthContext";

const originalFetch = globalThis.fetch;
beforeEach(() => {
  globalThis.fetch = mock(async () =>
    Response.json(ac),
  ) as unknown as typeof fetch;
});
async function renderSignups() {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({ role: "ac", name: "Test User" }),
  );
  window.history.pushState({}, "", "/ac/signups");
  render(<App />);
  await screen.findByRole("heading", { level: 1, name: "Evaluation Signups" });
}

afterEach(() => {
  cleanup();
  globalThis.fetch = originalFetch;
  localStorage.clear();
});

test("each signup request has a Review link named for its professor", async () => {
  await renderSignups();
  await screen.findByRole("rowheader", { name: "Professor 1" });
  const table = screen.getByRole("table");

  for (const name of ["Professor 1", "Professor 8"]) {
    expect(within(table).getByRole("rowheader", { name })).toBeDefined();
    expect(
      within(table).getByRole("link", { name: `Review signup for ${name}` }),
    ).toBeDefined();
  }
});

test("View All opens the faculty evaluations page", async () => {
  await renderSignups();
  fireEvent.click(
    screen.getByRole("link", { name: "View all faculty not yet signed up" }),
  );
  expect(window.location.pathname).toBe("/ac/evaluations");
});
