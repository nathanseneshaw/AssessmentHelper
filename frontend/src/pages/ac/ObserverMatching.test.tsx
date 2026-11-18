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
  refuseAssign = false;
  assigned = [];
  fakeBackend({
    "/api/observer-eligibility/signups/1/defer": (init, url) => {
      posts.push(url.pathname);
      const matching = state.matchingCases[0];
      if (!matching) throw Error("Missing case");
      matching.status = "DEFERRED";
      matching.canDefer = false;
      matching.deferral = {
        reason: JSON.parse(String(init?.body)).reason,
        deferredBy: "Kaia Clearbrook",
        deferredAt: "2026-11-02T10:00:00",
        targetSemester: "Spring 2027",
      };
      return new Response(null, { status: 204 });
    },
    "/api/observer-eligibility/signups/1/regenerate": (_init, url) => {
      posts.push(url.pathname);
      const first = state.signups[0];
      const matching = state.matchingCases[0];
      if (first && matching) {
        matching.previousRecommendations = first.candidates;
        const replacement = eligible[eligible.length - 1];
        if (!replacement) throw new Error("Missing replacement observer");
        first.candidates = [replacement];
        first.released = false;
        matching.reason = "RECOMMENDATIONS_AWAITING_RELEASE";
        matching.unusedEligibleObserverCount = 0;
      }
      return Response.json(state);
    },
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
      const matching = state.matchingCases[0];
      if (matching && first) {
        matching.status = "MATCHED";
        matching.reason = null;
        matching.invitations = [
          {
            requestId: 100,
            observerId: body.observerId,
            observerName: first.observerName ?? "",
            status: "ASSIGNED",
            requestedAt: "2026-10-21T10:00:00",
            expiresAt: null,
            respondedAt: "2026-10-21T10:00:00",
          },
        ];
      }
      return new Response(null, { status: 204 });
    },
    "/api/observer-eligibility/ac": () => Response.json(state),
    "/api/observer-eligibility/generate": (_init, url) => {
      posts.push(url.pathname);
      return Response.json(state);
    },
    "/api/observer-eligibility/signups/1/release": (_init, url) => {
      posts.push(url.pathname);
      const first = state.signups[0];
      if (first) first.released = true;
      const matching = state.matchingCases[0];
      if (matching?.reason === "RECOMMENDATIONS_AWAITING_RELEASE") {
        matching.status = "AWAITING_INVITATION";
        matching.reason = null;
      }
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

function candidateCard() {
  const card = screen
    .getByRole("heading", { name: "Eligible observers" })
    .closest("section");
  if (!card) throw new Error("Missing card");
  return card;
}

test("invitation history shows when each observer answered or was assigned", async () => {
  const matching = state.matchingCases[0];
  if (!matching) throw new Error("Missing case");
  matching.invitations = [
    {
      requestId: 1,
      observerId: 2,
      observerName: "Professor 2",
      status: "DECLINED",
      requestedAt: "2026-10-20T09:00:00",
      expiresAt: "2026-10-27T09:00:00",
      respondedAt: "2026-10-21T08:30:00.123456",
    },
    {
      requestId: 2,
      observerId: 3,
      observerName: "Professor 3",
      status: "PENDING",
      requestedAt: "2026-10-20T09:00:00",
      expiresAt: "2026-10-27T09:00:00",
      respondedAt: null,
    },
    {
      requestId: 3,
      observerId: 4,
      observerName: "Professor 4",
      status: "ASSIGNED",
      requestedAt: "2026-10-28T14:00:00",
      expiresAt: "2026-11-04T14:00:00",
      respondedAt: "2026-10-28T14:00:00",
    },
  ];
  await open();
  const table = within(
    screen.getByRole("region", { name: "Matching status" }),
  ).getByRole("table");
  const rows = within(table)
    .getAllByRole("row")
    .map((row) =>
      Array.from(row.querySelectorAll("th, td"), (cell) => cell.textContent),
    );
  expect(rows).toEqual([
    ["Observer", "Status", "Sent", "Expires", "Responded"],
    [
      "Professor 2",
      "Declined",
      "2026-10-20 09:00:00",
      "2026-10-27 09:00:00",
      "2026-10-21 08:30:00",
    ],
    [
      "Professor 3",
      "Pending",
      "2026-10-20 09:00:00",
      "2026-10-27 09:00:00",
      "—",
    ],
    [
      "Professor 4",
      "Assigned",
      "2026-10-28 14:00:00",
      "2026-11-04 14:00:00",
      "2026-10-28 14:00:00",
    ],
  ]);
});

function exhaustedCase() {
  const signup = state.signups[0];
  const matching = state.matchingCases[0];
  if (!signup || !matching) throw new Error("Missing signup");
  signup.released = true;
  state.cycle.candidateListsOpen = true;
  matching.status = "UNMATCHED";
  matching.reason = "INVITATIONS_UNSUCCESSFUL";
  matching.unusedEligibleObserverCount = 1;
  matching.invitations = signup.candidates.map((c, i) => ({
    requestId: i + 1,
    observerId: c.observerId,
    observerName: c.name,
    status: "DECLINED",
    requestedAt: null,
    expiresAt: null,
    respondedAt: null,
  }));
  return matching;
}

function button(
  name: string,
  scope: Pick<typeof screen, "getByRole"> = screen,
) {
  return scope.getByRole("button", { name }) as HTMLButtonElement;
}

test("Generate Matches asks the backend to generate the candidate lists", async () => {
  await open();
  fireEvent.click(button("Generate Matches"));
  await screen.findByRole("status");
  expect(posts).toEqual(["/api/observer-eligibility/generate"]);
});

test("shows the candidate list with each observer's courses and releases it", async () => {
  state.cycle.signupOpen = false;
  state.cycle.candidateListsOpen = true;
  await open();
  expect(within(candidateCard()).getAllByRole("listitem")).toHaveLength(5);

  fireEvent.click(button("See courses for Professor 2"));
  const dialog = await screen.findByRole("dialog", {
    name: "Qualifying courses",
  });
  expect(within(dialog).getByText("CS 3346.002 - Fall 2025")).toBeTruthy();
  fireEvent.click(button("Close", within(dialog)));
  expect(screen.queryByRole("dialog")).toBeNull();

  fireEvent.click(button("Send Candidate List"));
  await screen.findByRole("button", { name: "Candidate List Sent" });
  expect(posts).toEqual(["/api/observer-eligibility/signups/1/release"]);
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

test("manual assignment is disabled until all recommendations decline and the pool is exhausted", async () => {
  await open();
  expect(button("Assign Observer").disabled).toBe(true);
  expect(
    screen.queryByRole("button", { name: "Defer to Next Semester" }),
  ).toBeNull();
});

test("generates a replacement list for review and sends it separately while retaining history", async () => {
  exhaustedCase();
  await open();
  fireEvent.click(button("Generate New Recommendations"));
  await screen.findByText("Unmatched: New recommendations awaiting AC review");
  expect(within(candidateCard()).getByText("Professor 7")).toBeTruthy();
  expect(within(candidateCard()).queryByText("Professor 2")).toBeNull();
  expect(screen.getByText("Previous recommendations (5)")).toBeTruthy();

  fireEvent.click(button("Send Candidate List"));
  await screen.findByText("Awaiting invitation");
  expect(posts).toEqual([
    "/api/observer-eligibility/signups/1/regenerate",
    "/api/observer-eligibility/signups/1/release",
  ]);
});

async function openAssign() {
  const matching = exhaustedCase();
  matching.unusedEligibleObserverCount = 0;
  matching.canAssign = true;
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

  fireEvent.click(within(dialog).getByRole("radio", { name: /Professor 7/ }));
  fireEvent.click(button("Assign Observer", within(dialog)));

  await screen.findByText("Observer: Professor 7");
  expect(assigned).toEqual([{ observerId: 7 }]);
  expect(screen.queryByRole("dialog")).toBeNull();
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

test("AC can defer a case with zero eligible observers and see its recorded reason", async () => {
  const matching = state.matchingCases[0];
  if (!matching) throw Error("Missing case");
  matching.status = "UNMATCHED";
  matching.reason = "NO_ELIGIBLE_OBSERVERS";
  matching.eligibleObserverCount = 0;
  matching.canAssign = false;
  matching.canDefer = true;
  await open();
  fireEvent.click(button("Defer to Next Semester"));
  fireEvent.change(screen.getByLabelText(/Reason/), {
    target: { value: "No matching observers for this section" },
  });
  fireEvent.click(button("Confirm Deferral"));
  await screen.findByText("Due next semester");
  expect(
    screen.getByText("Reason: No matching observers for this section"),
  ).toBeTruthy();
  expect(posts).toEqual(["/api/observer-eligibility/signups/1/defer"]);
});
