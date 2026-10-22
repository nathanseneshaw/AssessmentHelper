import { afterEach, beforeEach, expect, mock, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ac } from "../../../test/observerFixtures";
import type { AcState } from "../../api/observerEligibility";
import { AuthProvider } from "../../auth/AuthContext";
import { AcObserverMatching } from "./ObserverMatching";

const originalFetch = globalThis.fetch;
let state: AcState;
let posts: string[];
beforeEach(() => {
  state = structuredClone(ac);
  posts = [];
  globalThis.fetch = mock(async (input: unknown, init?: RequestInit) => {
    const path = String(input);
    if (init?.method === "POST") {
      posts.push(path);
      if (path.endsWith("/release")) {
        const first = state.signups[0];
        if (first) first.released = true;
      } else return Response.json(state);
      return new Response(null, { status: 204 });
    }
    return Response.json(state);
  }) as unknown as typeof fetch;
});
afterEach(() => {
  cleanup();
  globalThis.fetch = originalFetch;
});
async function open() {
  render(
    <MemoryRouter>
      <AuthProvider>
        <AcObserverMatching />
      </AuthProvider>
    </MemoryRouter>,
  );
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
  expect(posts[0]).toEndWith("/signups/1/release");
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
  expect(posts[0]).toEndWith("/generate");
});
test("reports a failed load without inventing candidates", async () => {
  globalThis.fetch = mock(async () =>
    Response.json({ detail: "Backend unavailable" }, { status: 503 }),
  ) as unknown as typeof fetch;
  render(
    <MemoryRouter>
      <AuthProvider>
        <AcObserverMatching />
      </AuthProvider>
    </MemoryRouter>,
  );
  await screen.findByRole("alert");
  expect(screen.queryByText("Professor 2")).toBeNull();
});
