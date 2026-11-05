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
let eligibleDown: boolean;
let generateDown: boolean;
let assigned: unknown[];
let refuseRegeneration: boolean;

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
  eligibleDown = false;
  generateDown = false;
  assigned = [];
  refuseRegeneration = false;
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
      if (refuseRegeneration)
        return Response.json(
          { detail: "No additional eligible observers remain." },
          { status: 409 },
        );
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
      eligibleDown
        ? Response.json(
            { detail: "Teaching schedules could not be read." },
            { status: 503 },
          )
        : Response.json(eligible),
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
    "/api/observer-eligibility/ac": () =>
      unavailable
        ? Response.json({ detail: "Backend unavailable" }, { status: 503 })
        : Response.json(state),
    "/api/observer-eligibility/generate": (_init, url) => {
      posts.push(url.pathname);
      if (generateDown)
        return Response.json(
          {
            detail:
              "Configure exactly one current semester and evaluation cycle.",
          },
          { status: 409 },
        );
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

test("shows unsuccessful invitation history and additional eligible observers", async () => {
  const signup = state.signups[0];
  if (signup) signup.candidates = signup.candidates.slice(0, 2);
  const matching = state.matchingCases[0];
  if (!matching) throw new Error("Missing case");
  matching.status = "UNMATCHED";
  matching.reason = "INVITATIONS_UNSUCCESSFUL";
  matching.remainingObserverCount = 4;
  matching.unusedEligibleObserverCount = 4;
  matching.invitations = [
    {
      requestId: 1,
      observerId: 2,
      observerName: "Professor 2",
      status: "DECLINED",
      requestedAt: "2026-10-20T09:00:00.123456",
      expiresAt: "2026-10-22T09:00:00.987654",
      respondedAt: "2026-10-20T10:00:00",
    },
    {
      requestId: 2,
      observerId: 3,
      observerName: "Professor 3",
      status: "EXPIRED",
      requestedAt: "2026-10-20T09:00:00",
      expiresAt: "2026-10-22T09:00:00",
      respondedAt: null,
    },
  ];
  await open();
  const details = within(
    screen.getByRole("region", { name: "Matching status" }),
  );
  expect(
    details.getByText("Unmatched: Invitations declined or expired"),
  ).toBeTruthy();
  expect(details.getByText(/4 additional eligible observer/)).toBeTruthy();
  expect(details.getByRole("table")).toBeTruthy();
  expect(details.getByText("Declined")).toBeTruthy();
  expect(details.getByText("Expired")).toBeTruthy();
  expect(details.queryByText(/123456|987654/)).toBeNull();
  expect(details.getAllByText("2026-10-20 09:00:00")).toHaveLength(2);
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

test("generates a replacement list for review and sends it separately while retaining history", async () => {
  exhaustedCase();
  await open();
  fireEvent.click(
    screen.getByRole("button", { name: "Generate New Recommendations" }),
  );
  await screen.findByText(
    "New recommendations generated. Review the list, then send it to the observee.",
  );
  await screen.findByText("Unmatched: New recommendations awaiting AC review");
  const card = screen
    .getByRole("heading", { name: "Eligible observers" })
    .closest("section");
  if (!card) throw new Error("Missing card");
  expect(within(card).getByText("Professor 7")).toBeTruthy();
  expect(within(card).queryByText("Professor 2")).toBeNull();
  expect(screen.getByText("Previous recommendations (5)")).toBeTruthy();
  expect(
    screen.getByRole("heading", { name: "Invitation history" }),
  ).toBeTruthy();
  expect(posts).toEqual(["/api/observer-eligibility/signups/1/regenerate"]);
  fireEvent.click(screen.getByRole("button", { name: "Send Candidate List" }));
  await screen.findByText("Awaiting invitation");
  expect(posts).toEqual([
    "/api/observer-eligibility/signups/1/regenerate",
    "/api/observer-eligibility/signups/1/release",
  ]);
});

test("no unused observers leaves the case unmatched and disables new recommendations", async () => {
  const matching = exhaustedCase();
  matching.unusedEligibleObserverCount = 0;
  await open();
  expect(
    screen.getByText(/No additional eligible observers remain/),
  ).toBeTruthy();
  expect(
    (
      screen.getByRole("button", {
        name: "Generate New Recommendations",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
});

test("refused regeneration preserves the current recommendation list", async () => {
  exhaustedCase();
  refuseRegeneration = true;
  await open();
  fireEvent.click(
    screen.getByRole("button", { name: "Generate New Recommendations" }),
  );
  await screen.findByRole("alert");
  const card = screen
    .getByRole("heading", { name: "Eligible observers" })
    .closest("section");
  if (!card) throw new Error("Missing card");
  expect(within(card).getByText("Professor 2")).toBeTruthy();
  expect(within(card).queryByText("Professor 7")).toBeNull();
});

test("loads persisted candidates and releases the reviewed list", async () => {
  state.cycle.signupOpen = false;
  state.cycle.candidateListsOpen = true;
  await open();
  const card = screen
    .getByRole("heading", { name: "Eligible observers" })
    .closest("section");
  if (!card) throw Error("Missing card");
  expect(within(card).getAllByRole("listitem")).toHaveLength(5);
  expect(within(card).queryByText("CS 3346.002 - Fall 2025")).toBeNull();
  expect(
    within(card).getAllByRole("button", { name: /^See courses for/ }),
  ).toHaveLength(5);
  expect(within(card).queryByText("CS 3346.002 - Data Structures")).toBeNull();
  expect(within(card).queryByText(/3000-level/)).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Send Candidate List" }));
  await screen.findByRole("button", { name: "Candidate List Sent" });
  expect(posts).toEqual(["/api/observer-eligibility/signups/1/release"]);
});

test("See Courses opens the selected observer's history and closes without leaving matching", async () => {
  await open();
  const button = screen.getByRole("button", {
    name: "See courses for Professor 2",
  });
  button.focus();
  fireEvent.click(button);
  const dialog = await screen.findByRole("dialog", {
    name: "Qualifying courses",
  });
  expect(within(dialog).getByText("Professor 2")).toBeTruthy();
  expect(within(dialog).getByText("CS 3346.002 - Fall 2025")).toBeTruthy();
  expect(within(dialog).queryByText("CS 3346.003 - Fall 2025")).toBeNull();
  fireEvent.click(within(dialog).getByRole("button", { name: "Close" }));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(document.activeElement).toBe(button);
  expect(window.location.pathname).toBe("/ac/matching");
});

test("zero candidates alert AC and prevent release; arrows wrap", async () => {
  await open();
  fireEvent.click(screen.getByRole("button", { name: "Next observee" }));
  expect(screen.getByRole("alert").textContent).toContain(
    "No generated observers.",
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
  expect(within(screen.getByRole("table")).getByText("Assigned")).toBeTruthy();
  expect(within(screen.getByRole("table")).queryByText("Accepted")).toBeNull();
  expect(button("Assign Observer").disabled).toBe(true);
  expect(button("Candidate List Sent").disabled).toBe(true);
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

test("a failed eligibility check says why and offers nobody to assign", async () => {
  const matching = exhaustedCase();
  matching.unusedEligibleObserverCount = 0;
  matching.canAssign = true;
  eligibleDown = true;
  await open();
  fireEvent.click(button("Assign Observer"));
  const dialog = await screen.findByRole("dialog", {
    name: "Assign an observer",
  });
  expect((await within(dialog).findByRole("alert")).textContent).toBe(
    "Teaching schedules could not be read.",
  );
  expect(within(dialog).queryAllByRole("radio")).toHaveLength(0);
  expect(button("Assign Observer", within(dialog)).disabled).toBe(true);
});

test("a failed Generate Matches shows the reason and keeps the lists", async () => {
  generateDown = true;
  await open();
  fireEvent.click(screen.getByRole("button", { name: "Generate Matches" }));
  expect((await screen.findByRole("alert")).textContent).toContain(
    "Configure exactly one current semester and evaluation cycle.",
  );
  expect(screen.queryByRole("status")).toBeNull();
  expect(
    screen.getByRole("heading", { name: "Observee: Professor 1" }),
  ).toBeDefined();
  expect(button("Generate Matches").disabled).toBe(false);
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

test("manual assignment is disabled until all recommendations decline and the pool is exhausted", async () => {
  await open();
  expect(button("Assign Observer").disabled).toBe(true);
  expect(
    screen.queryByRole("button", { name: "Defer to Next Semester" }),
  ).toBeNull();
});

test("expired invitations allow manual assignment after recommendations are exhausted", async () => {
  const matching = exhaustedCase();
  matching.canAssign = true;
  matching.unusedEligibleObserverCount = 0;
  const invitation = matching.invitations[0];
  if (!invitation) throw Error("Missing invitation");
  invitation.status = "EXPIRED";
  await open();
  expect(button("Assign Observer").disabled).toBe(false);
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
  expect(button("Assign Observer").disabled).toBe(true);
  fireEvent.click(button("Defer to Next Semester"));
  const reason = screen.getByLabelText(/Reason/);
  fireEvent.change(reason, { target: { value: " " } });
  expect(button("Confirm Deferral").disabled).toBe(true);
  fireEvent.change(reason, {
    target: { value: "No matching observers for this section" },
  });
  fireEvent.click(button("Confirm Deferral"));
  await screen.findByText("Due next semester");
  expect(
    screen.getByText("Reason: No matching observers for this section"),
  ).toBeTruthy();
  expect(screen.getByText("Due: Spring 2027")).toBeTruthy();
  expect(screen.getByText(/Deferred by Kaia Clearbrook/)).toBeTruthy();
  expect(
    screen.queryByRole("button", { name: "Defer to Next Semester" }),
  ).toBeNull();
  expect(button("Send Candidate List").disabled).toBe(true);
  expect(posts).toContain("/api/observer-eligibility/signups/1/defer");
});
