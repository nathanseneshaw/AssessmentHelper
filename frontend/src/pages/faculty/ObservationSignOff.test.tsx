import { afterEach, beforeEach, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
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

const COMMENTS = "Thanks, the pacing notes help.";
const SIGNED_OFF = "You signed off. The record is complete and locked.";

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
      current = {
        ...current,
        status: "COMPLETED",
        locked: true,
        canSign: false,
        observeeComments: body.comments,
        observeeSignedAt: "2026-10-22T14:05:00",
      };
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

async function openSignOff() {
  window.history.pushState({}, "", "/faculty/observations/11");
  render(<App />);
  const view = within(
    await screen.findByRole("dialog", { name: "Classroom Observation" }),
  );
  const panel = within(await view.findByRole("region", { name: "Sign off" }));
  fireEvent.change(panel.getByLabelText(/Your comments/), {
    target: { value: COMMENTS },
  });
  return { view, panel };
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

test("the record is completed and locked when the observee confirms sign-off", async () => {
  const { view, panel } = await openSignOff();
  expect(signOffs(view)).toContain("Not signed yet");

  fireEvent.click(panel.getByRole("button", { name: "Sign off" }));
  expect(sent).toEqual([]);
  fireEvent.click(
    within(
      panel.getByRole("region", { name: "Sign off on this observation" }),
    ).getByRole("button", { name: "Sign off" }),
  );

  await view.findByText(SIGNED_OFF);
  expect(sent).toEqual([{ comments: COMMENTS }]);
  expect(changes).toBe(1);
  expect(signOffs(view)).toContain("Signed Oct 22, 2026, 2:05 PM CT");
  expect(signOffs(view)).toContain(COMMENTS);
  expect(view.queryByRole("region", { name: "Sign off" })).toBeNull();
  expect(view.queryAllByRole("button")).toHaveLength(0);
});

test("shows why and keeps the comments when sign-off is refused", async () => {
  signFailure = Response.json(
    { detail: "Comments must be at most 4000 characters." },
    { status: 400 },
  );
  const { view, panel } = await openSignOff();
  confirmSignOff(panel);

  expect((await panel.findByRole("alert")).textContent).toBe(
    "Your sign-off was not saved. Comments must be at most 4000 characters. Your comments are still here.",
  );
  expect(
    (panel.getByLabelText(/Your comments/) as HTMLTextAreaElement).value,
  ).toBe(COMMENTS);
  expect(changes).toBe(0);

  fireEvent.click(
    within(
      panel.getByRole("region", { name: "Sign off on this observation" }),
    ).getByRole("button", { name: "Sign off" }),
  );
  await view.findByText(SIGNED_OFF);
  expect(sent).toEqual([{ comments: COMMENTS }, { comments: COMMENTS }]);
});
