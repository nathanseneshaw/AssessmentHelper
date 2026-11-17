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
import type {
  ObservationAnswer,
  ObservationDetail,
  ObservationSummary,
} from "../../api/observations";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { completedObservation } from "../../test/observationFixtures";
import { faculty } from "../../test/observerFixtures";

type Body = { responses: ObservationAnswer[] } | undefined;

let current: ObservationDetail;
let sent: { method: string; path: string; body: Body }[];
/** A one-shot failure for the next write. */
let writeFailure: Response | undefined;
/** How many times the dialog told the dashboard behind it that observations changed. */
let changes = 0;
const countChange = () => {
  changes += 1;
};

/** Oscar's observation of Olivia, before he starts it. The signed-in professor (id 2) is Oscar. */
function notStarted(): ObservationDetail {
  return {
    ...structuredClone(completedObservation),
    observationId: 5,
    status: "ACCEPTED",
    viewerRole: "OBSERVER",
    observer: {
      professorId: 2,
      name: "Oscar Observer",
      department: "Computer Science",
    },
    observationDate: null,
    startTime: null,
    responses: [],
    submittedAt: null,
    canStart: true,
    canEdit: false,
    locked: false,
    observeeSignedAt: null,
  };
}

function inProgress(responses: ObservationAnswer[] = []): ObservationDetail {
  return {
    ...notStarted(),
    status: "IN_PROGRESS",
    observationDate: "2026-10-27",
    startTime: "10:00:00",
    responses,
    canStart: false,
    canEdit: true,
  };
}

function summary(
  changes: Partial<ObservationSummary> &
    Pick<
      ObservationSummary,
      "observationId" | "status" | "observeeName" | "courseLabel"
    >,
): ObservationSummary {
  return {
    role: "OBSERVER",
    observerName: "Oscar Observer",
    semesterLabel: "Fall 2026",
    cycleId: 2,
    observationDate: null,
    startTime: null,
    submittedAt: null,
    ...changes,
  };
}

const myObservations: ObservationSummary[] = [
  summary({
    observationId: 5,
    status: "ACCEPTED",
    observeeName: "Olivia Observee",
    courseLabel: "CS 3345.001",
  }),
  summary({
    observationId: 6,
    status: "IN_PROGRESS",
    observeeName: "Ivan Inprogress",
    courseLabel: "CS 2336.001",
    observationDate: "2026-10-26",
    startTime: "13:00:00",
  }),
  summary({
    observationId: 7,
    status: "SUBMITTED",
    observeeName: "Sam Submitted",
    courseLabel: "CS 4337.001",
    observationDate: "2026-10-23",
    submittedAt: "2026-10-23T14:30:00",
  }),
  // Oscar's own evaluation, observed by someone else, belongs in the other list.
  summary({
    observationId: 9,
    role: "OBSERVEE",
    status: "SUBMITTED",
    observeeName: "Oscar Observer",
    observerName: "Erin Elsewhere",
    courseLabel: "CS 1337.002",
  }),
];

async function write(
  init: RequestInit | undefined,
  url: URL,
  change: (body: Body) => ObservationDetail,
) {
  const body: Body = init?.body ? JSON.parse(String(init.body)) : undefined;
  sent.push({ method: init?.method ?? "GET", path: url.pathname, body });
  const failure = writeFailure;
  writeFailure = undefined;
  if (failure) return failure;
  current = change(body);
  return Response.json(current);
}

beforeEach(() => {
  current = notStarted();
  sent = [];
  writeFailure = undefined;
  changes = 0;
  window.addEventListener("observer-data-changed", countChange);
  fakeBackend({
    "/api/observer-eligibility/faculty/2": () => Response.json(faculty),
    "/api/professors/2/observations": () => Response.json(myObservations),
    "/api/observations/5": () => Response.json(current),
    "/api/observations/5/start": (init, url) =>
      write(init, url, () => ({
        ...current,
        status: "IN_PROGRESS",
        observationDate: current.observationDate ?? "2026-10-27",
        startTime: current.startTime ?? "10:00:00",
        canStart: false,
        canEdit: true,
      })),
    "/api/observations/5/responses": (init, url) =>
      write(init, url, (body) => ({
        ...current,
        responses: body?.responses ?? [],
      })),
    "/api/observations/5/submit": (init, url) =>
      write(init, url, (body) => ({
        ...current,
        responses: body?.responses ?? [],
        status: "SUBMITTED",
        submittedAt: "2026-10-27T10:55:00",
        canEdit: false,
      })),
  });
  signInAs("professor", { name: "Oscar Observer" });
});

afterEach(() => {
  cleanup();
  resetBackend();
  window.removeEventListener("observer-data-changed", countChange);
});

function open(path = "/faculty/observations/5") {
  window.history.pushState({}, "", path);
  render(<App />);
}

async function dialog(name = "Classroom Observation") {
  return within(await screen.findByRole("dialog", { name }));
}

async function openForm(responses: ObservationAnswer[] = []) {
  current = inProgress(responses);
  open();
  const view = await dialog();
  await view.findByRole("radiogroup", { name: "Student Engagement" });
  return view;
}

type View = Awaited<ReturnType<typeof dialog>>;

function rating(view: View, question: string, value: number) {
  return within(view.getByRole("radiogroup", { name: question })).getByRole(
    "radio",
    { name: String(value) },
  ) as HTMLInputElement;
}

function textbox(view: View, name: string) {
  return view.getByRole("textbox", { name }) as HTMLTextAreaElement;
}

const STRENGTHS = "Strengths observed";
const SUGGESTIONS = "Suggestions for improvement Optional";
const ENGAGEMENT_NOTES = "Notes on Student Engagement (optional)";

/** The dialog's own question before unsaved answers are thrown away. */
async function leavePrompt() {
  return within(
    await screen.findByRole("alertdialog", { name: "Leave without saving?" }),
  );
}

/** True when leaving the page would make the browser ask first. */
function leavingAsks() {
  const event = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(event);
  return event.defaultPrevented;
}

test("the list shows the observations I do, and Start opens the form", async () => {
  open("/faculty/observations/others");
  const list = await dialog("Observation Records for Others");
  await list.findByText("CS 3345.001 · Olivia Observee");
  // Oscar's own evaluation is on the other list.
  expect(list.getAllByRole("listitem")).toHaveLength(3);
  expect(list.queryByText(/Erin Elsewhere/)).toBeNull();
  expect(
    list
      .getByRole("link", {
        name: "Continue observation of Ivan Inprogress, CS 2336.001",
      })
      .getAttribute("href"),
  ).toBe("/faculty/observations/6");
  expect(
    list
      .getByRole("link", {
        name: "View observation of Sam Submitted, CS 4337.001",
      })
      .getAttribute("href"),
  ).toBe("/faculty/observations/7");

  fireEvent.click(
    list.getByRole("button", {
      name: "Start observation of Olivia Observee, CS 3345.001",
    }),
  );
  const view = await dialog();
  await view.findByRole("radiogroup", { name: "Student Engagement" });
  expect(window.location.pathname).toBe("/faculty/observations/5");
  expect(sent).toEqual([
    { method: "POST", path: "/api/observations/5/start", body: undefined },
  ]);
  expect(changes).toBe(1);
});

test("before the observation starts, the details show the class and a Start button", async () => {
  open();
  const view = await dialog();
  await view.findByText("CS 3345.001 · Fall 2026");
  expect(view.getByText("Olivia Observee")).toBeDefined();
  expect(view.getByText("ECSS 2.410")).toBeDefined();
  expect(view.queryByRole("radiogroup")).toBeNull();

  fireEvent.click(view.getByRole("button", { name: "Start observation" }));
  await view.findByRole("radiogroup", { name: "Student Engagement" });
  expect(sent).toEqual([
    { method: "POST", path: "/api/observations/5/start", body: undefined },
  ]);
  // Starting records when the observation happened.
  expect(view.getByText("Oct 27, 2026")).toBeDefined();
});

test("the form asks the template's questions, starting from the saved answers", async () => {
  const view = await openForm([
    { criterionId: 1, rating: 4, comments: "Students asked questions." },
    { criterionId: 6, rating: null, comments: "Clear examples." },
  ]);

  expect(rating(view, "Student Engagement", 4).checked).toBe(true);
  expect(textbox(view, ENGAGEMENT_NOTES).value).toBe(
    "Students asked questions.",
  );
  expect(rating(view, "Preparedness & Organization", 4).checked).toBe(false);
  expect(textbox(view, STRENGTHS).value).toBe("Clear examples.");
  expect(textbox(view, SUGGESTIONS).value).toBe("");
});

test("Save draft sends the answers, trimmed and without blank ones", async () => {
  const view = await openForm([
    { criterionId: 1, rating: 4, comments: "Old note" },
  ]);
  fireEvent.click(rating(view, "Preparedness & Organization", 2));
  fireEvent.change(textbox(view, ENGAGEMENT_NOTES), {
    target: { value: "   " },
  });
  fireEvent.change(textbox(view, STRENGTHS), {
    target: { value: "  Clear examples.\nGood pacing.  \n" },
  });
  expect(view.getByText("Unsaved changes")).toBeDefined();

  fireEvent.click(view.getByRole("button", { name: "Save draft" }));
  await view.findByText(/^Draft saved at/);
  expect(sent).toEqual([
    {
      method: "PUT",
      path: "/api/observations/5/responses",
      body: {
        responses: [
          { criterionId: 1, rating: 4, comments: null },
          { criterionId: 2, rating: 2, comments: null },
          {
            criterionId: 6,
            rating: null,
            comments: "Clear examples.\nGood pacing.",
          },
        ],
      },
    },
  ]);
  expect(view.queryByText("Unsaved changes")).toBeNull();
});

test("submitting with a required question unanswered lists what is missing and sends nothing", async () => {
  const view = await openForm([{ criterionId: 1, rating: 3, comments: null }]);
  fireEvent.click(view.getByRole("button", { name: "Submit" }));

  const missing = view.getByRole("alert");
  expect(
    within(missing)
      .getAllByRole("button")
      .map((button) => button.textContent),
  ).toEqual(["Preparedness & Organization", "Strengths observed"]);
  expect(view.queryByRole("region", { name: "Submit observation" })).toBeNull();
  expect(sent).toEqual([]);
});

test("a complete form is submitted only after the observer confirms", async () => {
  const view = await openForm([
    { criterionId: 1, rating: 4, comments: "Students asked questions." },
    { criterionId: 2, rating: 5, comments: null },
  ]);
  fireEvent.change(textbox(view, STRENGTHS), {
    target: { value: " Clear examples. " },
  });
  fireEvent.click(view.getByRole("button", { name: "Submit" }));
  const confirm = within(
    view.getByRole("region", { name: "Submit observation" }),
  );
  expect(sent).toEqual([]);

  fireEvent.click(confirm.getByRole("button", { name: "Submit" }));
  await view.findByText("You submitted this observation.");
  expect(sent).toEqual([
    {
      method: "POST",
      path: "/api/observations/5/submit",
      body: {
        responses: [
          {
            criterionId: 1,
            rating: 4,
            comments: "Students asked questions.",
          },
          { criterionId: 2, rating: 5, comments: null },
          { criterionId: 6, rating: null, comments: "Clear examples." },
        ],
      },
    },
  ]);
  expect(changes).toBe(1);

  // The form is replaced by the read-only record.
  expect(view.queryByRole("radiogroup")).toBeNull();
  expect(
    within(view.getByRole("region", { name: "Summary" })).getByText(
      "Pending sign-off",
    ),
  ).toBeDefined();
});

test("a failed save shows the reason and keeps every answer", async () => {
  const view = await openForm([{ criterionId: 1, rating: 4, comments: null }]);
  fireEvent.change(textbox(view, STRENGTHS), {
    target: { value: "Clear examples." },
  });

  writeFailure = Response.json(
    { detail: "Comments must be 4000 characters or fewer." },
    { status: 400 },
  );
  fireEvent.click(view.getByRole("button", { name: "Save draft" }));
  expect((await view.findByRole("alert")).textContent).toBe(
    "The draft was not saved. Comments must be 4000 characters or fewer. Your answers are still here.",
  );
  expect(textbox(view, STRENGTHS).value).toBe("Clear examples.");
  expect(rating(view, "Student Engagement", 4).checked).toBe(true);
  expect(view.getByText("Unsaved changes")).toBeDefined();
});

test("with unsaved answers, closing asks first: Keep editing keeps them, Leave without saving closes", async () => {
  const view = await openForm();
  expect(leavingAsks()).toBe(false);
  fireEvent.change(textbox(view, STRENGTHS), {
    target: { value: "Clear examples." },
  });
  // Leaving or reloading the page asks too.
  expect(leavingAsks()).toBe(true);

  fireEvent.click(view.getByRole("link", { name: "Close" }));
  fireEvent.click(
    (await leavePrompt()).getByRole("button", { name: "Keep editing" }),
  );
  expect(window.location.pathname).toBe("/faculty/observations/5");
  expect(textbox(await dialog(), STRENGTHS).value).toBe("Clear examples.");

  fireEvent.keyDown(window, { key: "Escape" });
  fireEvent.click(
    (await leavePrompt()).getByRole("button", {
      name: "Leave without saving",
    }),
  );
  await waitFor(() => expect(window.location.pathname).toBe("/faculty"));
  expect(sent).toEqual([]);
});
