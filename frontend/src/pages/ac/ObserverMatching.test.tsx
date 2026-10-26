import { afterEach, beforeEach, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { App } from "../../App";
import type { AcState } from "../../api/observerEligibility";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { ac } from "../../test/observerFixtures";

let state: AcState;
let posts: string[];
let unavailable: boolean;

beforeEach(() => {
  state = structuredClone(ac);
  posts = [];
  unavailable = false;
  fakeBackend({
    "/api/observer-eligibility/ac": () =>
      unavailable
        ? Response.json({ detail: "Backend unavailable" }, { status: 503 })
        : Response.json(state),
    "/api/observer-eligibility/generate": (_init, url) => {
      posts.push(url.pathname);
      return Response.json(state);
    },
    "/api/observer-eligibility/signups/1/release": (_init, url) => {
      posts.push(url.pathname);
      const first = state.signups[0];
      if (first) first.released = true;
      return new Response(null, { status: 204 });
    },
  });
  signInAs("ac");
});

afterEach(() => {
  cleanup();
  resetBackend();
});

async function open() {
  window.history.pushState({}, "", "/ac/matching");
  render(<App />);
  await screen.findByRole("heading", { name: "Observee: Professor 1" });
}

test("loads persisted candidates and releases the reviewed list", async () => {
  await open();
  const card = screen
    .getByRole("heading", { name: "Eligible observers" })
    .closest("section");
  if (!card) throw Error("Missing card");
  expect(within(card).getAllByRole("listitem")).toHaveLength(5);
  expect(within(card).getByText("CS 3346.002 - Data Structures")).toBeDefined();
  expect(within(card).queryByText(/3000-level/)).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Send Candidate List" }));
  await screen.findByRole("button", { name: "Candidate List Sent" });
  expect(posts).toEqual(["/api/observer-eligibility/signups/1/release"]);
});

test("zero candidates alert AC and prevent release; arrows wrap", async () => {
  await open();
  fireEvent.click(screen.getByRole("button", { name: "Next observee" }));
  expect(screen.getByRole("alert").textContent).toContain(
    "No eligible observers",
  );
  expect(
    (
      screen.getByRole("button", {
        name: "Send Candidate List",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Next observee" }));
  expect(
    screen.getByRole("heading", { name: "Observee: Professor 1" }),
  ).toBeDefined();
});

test("allows generation before the signup deadline", async () => {
  await open();
  fireEvent.click(screen.getByRole("button", { name: "Generate Matches" }));
  await screen.findByRole("status");
  expect(posts).toEqual(["/api/observer-eligibility/generate"]);
});

test("reports a failed load without inventing candidates", async () => {
  unavailable = true;
  window.history.pushState({}, "", "/ac/matching");
  render(<App />);
  await screen.findByText(/Backend unavailable/);
  expect(screen.queryByText("Professor 2")).toBeNull();
});
