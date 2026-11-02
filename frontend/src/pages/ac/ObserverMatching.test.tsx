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
let refuseAssign: boolean;
let assigned: unknown[];

// Everyone who passes the rules: the five listed candidates plus Professor 7, who was not drawn for the list.
const eligible = [
  ...(ac.signups[0]?.candidates ?? []),
  {
    observerId: 7,
    professorId: 7,
    name: "Professor 7",
    department: "Computer Science",
    level: "3000-level",
    qualifyingCourses: ["CS 3305.001 - Discrete Math II"],
  },
];

beforeEach(() => {
  state = structuredClone(ac);
  posts = [];
  unavailable = false;
  refuseAssign = false;
  assigned = [];
  fakeBackend({
    "/api/observer-eligibility/signups/1/eligible": () =>
      Response.json(eligible),
    "/api/observer-eligibility/signups/1/assign": (init) => {
      if (refuseAssign)
        return Response.json(
          {
            detail:
              "Professor 1 is waiting for Professor 2 to respond. Assign an observer if the request is declined or expires.",
          },
          { status: 409 },
        );
      const body = JSON.parse(String(init?.body)) as { observerId: number };
      assigned.push(body);
      const first = state.signups[0];
      if (first)
        first.observerName =
          eligible.find((c) => c.observerId === body.observerId)?.name ?? null;
      return new Response(null, { status: 204 });
    },
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
  state.cycle.signupOpen = false;
  state.cycle.candidateListsOpen = true;
  await open();
  const card = screen
    .getByRole("heading", { name: "Eligible observers" })
    .closest("section");
  if (!card) throw Error("Missing card");
  expect(within(card).getAllByRole("listitem")).toHaveLength(5);
  expect(within(card).getByText("CS 3346.002 - Fall 2025")).toBeDefined();
  expect(within(card).queryByText("CS 3346.002 - Data Structures")).toBeNull();
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

function button(
  name: string,
  scope: Pick<typeof screen, "getByRole"> = screen,
) {
  return scope.getByRole("button", { name }) as HTMLButtonElement;
}

async function openAssign() {
  await open();
  fireEvent.click(button("Assign Observer"));
  const dialog = await screen.findByRole("dialog", {
    name: "Assign an observer",
  });
  await within(dialog).findByText("Professor 7");
  return dialog;
}

test("assigns any professor who passes the rules as the observer", async () => {
  const dialog = await openAssign();
  expect(within(dialog).getAllByRole("radio")).toHaveLength(6);
  expect(within(dialog).getAllByText("On candidate list")).toHaveLength(5);
  expect(button("Assign Observer", within(dialog)).disabled).toBe(true);

  fireEvent.click(within(dialog).getByRole("radio", { name: /Professor 7/ }));
  expect(
    within(dialog).getByText(/will not need to send a request/),
  ).toBeDefined();
  fireEvent.click(button("Assign Observer", within(dialog)));

  await screen.findByText(
    "Professor 7 is now the observer for Professor 1. Both professors have been notified.",
  );
  expect(assigned).toEqual([{ observerId: 7 }]);
  expect(screen.queryByRole("dialog")).toBeNull();
  await screen.findByText("Observer: Professor 7");
  expect(button("Assign Observer").disabled).toBe(true);
  expect(button("Send Candidate List").disabled).toBe(true);
});

test("keeps the dialog open with the reason when assignment is refused", async () => {
  refuseAssign = true;
  const dialog = await openAssign();
  fireEvent.click(within(dialog).getByRole("radio", { name: /Professor 3/ }));
  fireEvent.click(button("Assign Observer", within(dialog)));
  expect((await within(dialog).findByRole("alert")).textContent).toContain(
    "Professor 1 is waiting for Professor 2 to respond.",
  );
  expect(screen.getByRole("dialog")).toBeDefined();
  expect(screen.queryByText(/^Observer:/)).toBeNull();
});

test("cancel closes the dialog without assigning", async () => {
  const dialog = await openAssign();
  fireEvent.click(within(dialog).getByRole("radio", { name: /Professor 7/ }));
  fireEvent.click(button("Cancel", within(dialog)));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(assigned).toEqual([]);
});

test("a signup with an observer shows it and cannot be reassigned", async () => {
  const first = state.signups[0];
  if (first) first.observerName = "Professor 3";
  await open();
  expect(screen.getByText("Observer: Professor 3")).toBeDefined();
  expect(button("Assign Observer").disabled).toBe(true);
  expect(button("Send Candidate List").disabled).toBe(true);
});

test("reports a failed load without inventing candidates", async () => {
  unavailable = true;
  window.history.pushState({}, "", "/ac/matching");
  render(<App />);
  await screen.findByText(/Backend unavailable/);
  expect(screen.queryByText("Professor 2")).toBeNull();
});

test("send stays disabled and gray until signup closes", async () => {
  await open();
  const send = screen.getByRole("button", {
    name: "Send Candidate List",
  }) as HTMLButtonElement;
  expect(send.disabled).toBe(true);
  expect(send.className).toContain("disabled:bg-btn-gray");
  fireEvent.click(send);
  expect(posts).toHaveLength(0);
});
