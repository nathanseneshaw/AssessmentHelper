import { afterEach, beforeEach, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { App } from "../../App";
import type { ObservationDetail } from "../../api/observations";
import { dashboard } from "../../test/facultyDashboardFixtures";
import {
  facultyStateFor,
  OLIVIA,
  oliviaObservations,
  submittedObservation,
} from "../../test/facultyObservationFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";

type View = ReturnType<typeof within>;

let current: ObservationDetail;
let sent: { comments: string | null }[];
let signFailure: Response | undefined;
let changes: number;
const countChange = () => changes++;

const OSCAR = { professorId: 22, name: "Oscar Observer" };
const COMMENTS = "Thanks, the pacing notes help.";
const SIGN_OFF_WARNING =
  "Your comments will be lost. Sign off first to keep them.";

function signed(comments: string | null): ObservationDetail {
  return {
    ...current,
    status: "COMPLETED",
    locked: true,
    canSign: false,
    observeeComments: comments,
    observeeSignedAt: "2026-10-22T14:05:00",
  };
}

beforeEach(() => {
  current = submittedObservation();
  sent = [];
  signFailure = undefined;
  changes = 0;
  window.addEventListener("observer-data-changed", countChange);
  fakeBackend({
    [`/api/observer-eligibility/faculty/${OLIVIA.professorId}`]: () =>
      Response.json(facultyStateFor(OLIVIA)),
    [`/api/professors/${OLIVIA.professorId}/dashboard`]: () =>
      Response.json(dashboard),
    [`/api/professors/${OLIVIA.professorId}/observations`]: () =>
      Response.json(oliviaObservations()),
    "/api/feedback-surveys": () => Response.json([]),
    "/api/observations/11": () => Response.json(current),
    "/api/observations/11/observee-sign": (init) => {
      const body = JSON.parse(String(init?.body));
      sent.push(body);
      const failure = signFailure;
      signFailure = undefined;
      if (failure) return failure;
      current = signed(body.comments);
      return Response.json(current);
    },
  });
  signInAs("professor", OLIVIA);
});

afterEach(() => {
  window.removeEventListener("observer-data-changed", countChange);
  cleanup();
  resetBackend();
});

async function openRecord() {
  window.history.pushState({}, "", "/faculty/observations/11");
  render(<App />);
  return within(
    await screen.findByRole("dialog", { name: "Classroom Observation" }),
  );
}

async function signOffPanel(view: View) {
  return within(await view.findByRole("region", { name: "Sign off" }));
}

function comments(panel: View) {
  return panel.getByLabelText(/Your comments/) as HTMLTextAreaElement;
}

function confirmSignOff(panel: View) {
  fireEvent.click(panel.getByRole("button", { name: "Sign off" }));
  const confirm = within(
    panel.getByRole("region", { name: "Sign off on this observation" }),
  );
  fireEvent.click(confirm.getByRole("button", { name: "Sign off" }));
}

function signOffs(view: View) {
  return view.getByRole("region", { name: "Sign-offs" }).textContent;
}

// waitFor checks compare identity rather than elements, because a failed element comparison prints the whole DOM,
// which is slow enough for waitFor to time out.
function expectFocused(element: Element) {
  expect(document.activeElement === element).toBe(true);
}

function leavingAsks() {
  const event = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(event);
  return event.defaultPrevented;
}

test("the observee adds comments, confirms, and the record is complete and locked", async () => {
  const view = await openRecord();
  const panel = await signOffPanel(view);
  expect(signOffs(view)).toContain("Signed Oct 20, 2026, 9:15 AM CT");
  expect(signOffs(view)).toContain("Not signed yet");

  fireEvent.change(comments(panel), { target: { value: COMMENTS } });
  fireEvent.click(panel.getByRole("button", { name: "Sign off" }));
  const confirm = panel.getByRole("region", {
    name: "Sign off on this observation",
  });
  const heading = within(confirm).getByRole("heading");
  await waitFor(() => expectFocused(heading));
  expect(confirm.textContent).toContain("the record is locked");
  expect(sent).toEqual([]);

  fireEvent.click(within(confirm).getByRole("button", { name: "Sign off" }));
  const notice = await view.findByText(
    "You signed off. The record is complete and locked.",
  );
  await waitFor(() => expectFocused(notice));
  expect(sent).toEqual([{ comments: COMMENTS }]);
  expect(changes).toBe(1);

  expect(signOffs(view)).toContain("Signed Oct 22, 2026, 2:05 PM CT");
  expect(signOffs(view)).toContain(COMMENTS);
  expect(signOffs(view)).toContain("this record is locked");
  expect(view.queryByRole("region", { name: "Sign off" })).toBeNull();
  expect(view.queryAllByRole("textbox")).toHaveLength(0);
  expect(view.queryAllByRole("button")).toHaveLength(0);
  expect(leavingAsks()).toBe(false);
});

test("signing off with blank comments sends none", async () => {
  const view = await openRecord();
  const panel = await signOffPanel(view);
  fireEvent.change(comments(panel), { target: { value: "   " } });
  confirmSignOff(panel);

  await view.findByText("You signed off. The record is complete and locked.");
  expect(sent).toEqual([{ comments: null }]);
  expect(signOffs(view)).not.toContain("Observee's comments");
});

test("Go back sends nothing, keeps the comments, and returns to Sign off", async () => {
  const view = await openRecord();
  const panel = await signOffPanel(view);
  fireEvent.change(comments(panel), { target: { value: COMMENTS } });
  fireEvent.click(panel.getByRole("button", { name: "Sign off" }));
  fireEvent.click(panel.getByRole("button", { name: "Go back" }));

  const signOff = panel.getByRole("button", { name: "Sign off" });
  await waitFor(() => expectFocused(signOff));
  expect(
    panel.queryByRole("region", { name: "Sign off on this observation" }),
  ).toBeNull();
  expect(comments(panel).value).toBe(COMMENTS);
  expect(sent).toEqual([]);
});

test("a refused sign-off says why and keeps the comments for another try", async () => {
  signFailure = Response.json(
    { detail: "Comments must be at most 4000 characters." },
    { status: 400 },
  );
  const view = await openRecord();
  const panel = await signOffPanel(view);
  fireEvent.change(comments(panel), { target: { value: COMMENTS } });
  confirmSignOff(panel);

  expect((await panel.findByRole("alert")).textContent).toBe(
    "Your sign-off was not saved. Comments must be at most 4000 characters. Your comments are still here.",
  );
  expect(comments(panel).value).toBe(COMMENTS);
  expect(changes).toBe(0);

  fireEvent.click(
    within(
      panel.getByRole("region", { name: "Sign off on this observation" }),
    ).getByRole("button", { name: "Sign off" }),
  );
  await view.findByText("You signed off. The record is complete and locked.");
  expect(sent).toEqual([{ comments: COMMENTS }, { comments: COMMENTS }]);
});

test("a sign-off refused because the record was signed elsewhere shows the locked record", async () => {
  const view = await openRecord();
  const panel = await signOffPanel(view);
  current = signed("Signed in another tab.");
  signFailure = Response.json(
    { detail: "This observation has already been signed off." },
    { status: 409 },
  );
  confirmSignOff(panel);

  await waitFor(() =>
    expect(view.queryByRole("region", { name: "Sign off" }) === null).toBe(
      true,
    ),
  );
  expect(signOffs(view)).toContain("Signed in another tab.");
  expect(signOffs(view)).toContain("this record is locked");
  expect(
    view.queryByText("You signed off. The record is complete and locked."),
  ).toBeNull();
});

test("leaving with comments that are not sent asks first", async () => {
  const view = await openRecord();
  const panel = await signOffPanel(view);
  expect(leavingAsks()).toBe(false);
  fireEvent.change(comments(panel), { target: { value: COMMENTS } });

  expect(leavingAsks()).toBe(true);
  fireEvent.keyDown(window, { key: "Escape" });
  const prompt = within(
    await screen.findByRole("alertdialog", { name: "Leave without saving?" }),
  );
  expect(prompt.getByText(SIGN_OFF_WARNING)).toBeDefined();
});

test("the observer sees who still has to sign, and has nothing to sign", async () => {
  current = {
    ...submittedObservation(),
    viewerRole: "OBSERVER",
    canSign: false,
  };
  signInAs("professor", OSCAR);
  const view = await openRecord();
  await view.findByText(
    "You submitted this observation on Oct 20, 2026, 9:15 AM CT.",
  );

  expect(signOffs(view)).toContain("Signed Oct 20, 2026, 9:15 AM CT");
  expect(signOffs(view)).toContain("Not signed yet");
  expect(signOffs(view)).not.toContain("locked");
  expect(view.queryByRole("region", { name: "Sign off" })).toBeNull();
  expect(view.queryAllByRole("button")).toHaveLength(0);
});
