import { afterEach, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { App } from "../../App";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";

async function renderSignups() {
  fakeBackend();
  signInAs("ac");
  window.history.pushState({}, "", "/ac/signups");
  render(<App />);
  await screen.findByRole("heading", { level: 1, name: "Evaluation Signups" });
}

afterEach(() => {
  cleanup();
  resetBackend();
});

test("each signup request has a Review button named for its professor", async () => {
  await renderSignups();
  const table = screen.getByRole("table");

  for (const name of ["Professor 2", "Professor 1", "Professor 5"]) {
    expect(within(table).getByRole("rowheader", { name })).toBeDefined();
    expect(
      within(table).getByRole("button", { name: `Review signup for ${name}` }),
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
