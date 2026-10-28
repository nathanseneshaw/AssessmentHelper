import { afterEach, beforeEach, expect, type Mock, test } from "bun:test";
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
/** One-shot failures: the next read, or the next write. */
let readFailure: Response | undefined;
let writeFailure: Response | undefined;
/** While set, writes wait for it, so a test can look at the pending state. */
let gate: Promise<void> | undefined;
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
  summary({
    observationId: 8,
    status: "COMPLETED",
    observeeName: "Carla Completed",
    courseLabel: "CS 1200.001",
    semesterLabel: "Spring 2026",
    cycleId: 1,
    observationDate: "2026-03-10",
    startTime: "10:00:00",
    submittedAt: "2026-03-10T11:00:00",
  }),
];

async function write(
  init: RequestInit | undefined,
  url: URL,
  change: (body: Body) => ObservationDetail,
) {
  const body: Body = init?.body ? JSON.parse(String(init.body)) : undefined;
  sent.push({ method: init?.method ?? "GET", path: url.pathname, body });
  await gate;
  const failure = writeFailure;
  writeFailure = undefined;
  if (failure) return failure;
  current = change(body);
  return Response.json(current);
}

beforeEach(() => {
  current = notStarted();
  sent = [];
  readFailure = undefined;
  writeFailure = undefined;
  gate = undefined;
  changes = 0;
  window.addEventListener("observer-data-changed", countChange);
  fakeBackend({
    "/api/observer-eligibility/faculty/2": () => Response.json(faculty),
    "/api/professors/2/observations": () => Response.json(myObservations),
    "/api/observations/5": () => {
      const failure = readFailure;
      readFailure = undefined;
      return failure ?? Response.json(current);
    },
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

/** A short name for an element, so a failed focus check prints something readable rather than the whole DOM. */
function label(element: Element | null) {
  if (!element) return "nothing";
  const id = element.id ? `#${element.id}` : "";
  return `<${element.tagName.toLowerCase()}${id}> ${element.textContent?.slice(0, 60) ?? ""}`;
}

function expectFocused(element: Element) {
  expect(label(document.activeElement)).toBe(label(element));
  expect(document.activeElement === element).toBe(true);
}

/** The text of everything an element's aria-describedby points at. */
function description(element: HTMLElement) {
  return (element.getAttribute("aria-describedby") ?? "")
    .split(" ")
    .map((id) => document.getElementById(id)?.textContent ?? "")
    .join(" ");
}

function observationRequests() {
  return (globalThis.fetch as unknown as Mock<typeof fetch>).mock.calls.filter(
    ([input]) =>
      new URL(String(input)).pathname.startsWith("/api/observations"),
  );
}

test("the list shows only the observations I do, with the right action for each", async () => {
  open("/faculty/observations/others");
  const list = await dialog("Observation Records for Others");
  await list.findByText("CS 3345.001 · Olivia Observee");
  const items = list.getAllByRole("listitem");
  expect(items).toHaveLength(4);
  expect(list.queryByText(/Erin Elsewhere|CS 1337\.002/)).toBeNull();

  const [olivia, ivan, sam, carla] = items.map((item) => within(item));
  if (!olivia || !ivan || !sam || !carla) throw new Error("Missing items");
  expect(olivia.getByText("Fall 2026 · Not scheduled")).toBeDefined();
  expect(olivia.getByText("Not started")).toBeDefined();
  expect(
    olivia.getByRole("button", {
      name: "Start observation of Olivia Observee, CS 3345.001",
    }).textContent,
  ).toBe("Start observation");

  expect(ivan.getByText("Fall 2026 · Oct 26, 2026, 1:00 PM")).toBeDefined();
  expect(ivan.getByText("In progress")).toBeDefined();
  const resume = ivan.getByRole("link", {
    name: "Continue observation of Ivan Inprogress, CS 2336.001",
  });
  expect(resume.textContent).toBe("Continue");
  expect(resume.getAttribute("href")).toBe("/faculty/observations/6");

  expect(sam.getByText("Fall 2026 · Oct 23, 2026")).toBeDefined();
  expect(sam.getByText("Submitted")).toBeDefined();
  expect(
    sam
      .getByRole("link", {
        name: "View observation of Sam Submitted, CS 4337.001",
      })
      .getAttribute("href"),
  ).toBe("/faculty/observations/7");

  expect(carla.getByText("Spring 2026 · Mar 10, 2026, 10:00 AM")).toBeDefined();
  expect(carla.getByText("Completed")).toBeDefined();
  expect(
    carla.getByRole("link", { name: /^View observation/ }).getAttribute("href"),
  ).toBe("/faculty/observations/8");
});

test("Start on the list starts the observation and opens its form", async () => {
  open("/faculty/observations/others");
  const list = await dialog("Observation Records for Others");
  fireEvent.click(
    await list.findByRole("button", { name: /^Start observation of Olivia/ }),
  );

  const view = await dialog();
  await view.findByRole("radiogroup", { name: "Student Engagement" });
  expect(window.location.pathname).toBe("/faculty/observations/5");
  expect(sent).toEqual([
    { method: "POST", path: "/api/observations/5/start", body: undefined },
  ]);
  expect(changes).toBe(1);
});

test("closing the list while Start is pending does not open the form afterwards", async () => {
  let release = () => {};
  gate = new Promise((resolve) => {
    release = resolve;
  });
  open("/faculty/observations/others");
  const list = await dialog("Observation Records for Others");
  fireEvent.click(
    await list.findByRole("button", { name: /^Start observation of Olivia/ }),
  );
  await list.findByRole("button", { name: /^Starting observation of Olivia/ });

  fireEvent.keyDown(window, { key: "Escape" });
  await waitFor(() => expect(window.location.pathname).toBe("/faculty"));
  release();

  // The observation still started, so the dashboard hears about it, but the form stays closed.
  await waitFor(() => expect(changes).toBe(1));
  expect(sent.map((request) => request.path)).toEqual([
    "/api/observations/5/start",
  ]);
  expect(window.location.pathname).toBe("/faculty");
  expect(
    screen.queryByRole("dialog", { name: "Classroom Observation" }),
  ).toBeNull();
});

test("a failed start on the list shows the reason and stays on the list", async () => {
  writeFailure = Response.json(
    { detail: "This observation has already been submitted." },
    { status: 409 },
  );
  open("/faculty/observations/others");
  const list = await dialog("Observation Records for Others");
  const start = await list.findByRole("button", {
    name: /^Start observation of Olivia/,
  });
  fireEvent.click(start);

  const alert = await list.findByRole("alert");
  expect(alert.textContent).toBe(
    "This observation has already been submitted.",
  );
  expect(window.location.pathname).toBe("/faculty/observations/others");
  expect((start as HTMLButtonElement).disabled).toBe(false);
  expect(changes).toBe(0);
});

test("before the observation starts, the details show the class and a Start button", async () => {
  open();
  const view = await dialog();
  await view.findByText("CS 3345.001 · Fall 2026");
  expect(view.getByText("Olivia Observee")).toBeDefined();
  expect(
    view.getByText("Data Structures · MW 8:30 AM – 9:45 AM"),
  ).toBeDefined();
  expect(view.getByText("ECSS 2.410")).toBeDefined();
  expect(view.getByText("Not scheduled")).toBeDefined();
  expect(view.queryByRole("radiogroup")).toBeNull();

  fireEvent.click(view.getByRole("button", { name: "Start observation" }));
  await view.findByRole("radiogroup", { name: "Student Engagement" });
  expect(window.location.pathname).toBe("/faculty/observations/5");
  expect(sent).toEqual([
    { method: "POST", path: "/api/observations/5/start", body: undefined },
  ]);
  // Starting records when the observation happened.
  expect(view.getByText("Oct 27, 2026")).toBeDefined();
  expect(view.getByText("10:00 AM")).toBeDefined();
});

test("the form asks every question from the template, in order", async () => {
  const view = await openForm();

  const items = view.getAllByRole("listitem");
  const questions = [
    "Student Engagement",
    "Preparedness & Organization",
    "Strengths observed",
    "Suggestions for improvement",
  ];
  expect(items).toHaveLength(questions.length);
  questions.forEach((question, index) => {
    expect(
      items[index]?.textContent?.startsWith(`${index + 1}.${question}`),
    ).toBe(true);
  });

  const engagement = view.getByRole("radiogroup", {
    name: "Student Engagement",
  });
  expect(engagement.getAttribute("aria-required")).toBe("true");
  expect(description(engagement)).toBe("1 = Needs improvement, 5 = Excellent");
  expect(
    within(engagement)
      .getAllByRole("radio")
      .map((radio) => radio.getAttribute("value")),
  ).toEqual(["1", "2", "3", "4", "5"]);
  expect(
    view.getByRole("radiogroup", { name: "Preparedness & Organization" }),
  ).toBeDefined();
  expect(textbox(view, ENGAGEMENT_NOTES)).toBeDefined();
  expect(
    textbox(view, "Notes on Preparedness & Organization (optional)"),
  ).toBeDefined();

  const strengths = textbox(view, STRENGTHS);
  expect(strengths.getAttribute("aria-required")).toBe("true");
  expect(strengths.maxLength).toBe(4000);
  const suggestions = textbox(view, SUGGESTIONS);
  expect(suggestions.getAttribute("aria-required")).toBeNull();

  // A count appears near the 4,000 character limit.
  expect(view.queryByText(/characters left/)).toBeNull();
  fireEvent.change(strengths, { target: { value: "a".repeat(3600) } });
  expect(view.getByText("400 characters left (4,000 maximum)")).toBeDefined();
  expect(description(strengths)).toBe("400 characters left (4,000 maximum)");
});

test("a resumed draft starts from the saved answers", async () => {
  const view = await openForm([
    { criterionId: 1, rating: 4, comments: "Students asked questions." },
    { criterionId: 6, rating: null, comments: "Clear examples." },
  ]);

  expect(rating(view, "Student Engagement", 4).checked).toBe(true);
  expect(textbox(view, ENGAGEMENT_NOTES).value).toBe(
    "Students asked questions.",
  );
  for (const value of [1, 2, 3, 4, 5]) {
    expect(rating(view, "Preparedness & Organization", value).checked).toBe(
      false,
    );
  }
  expect(textbox(view, STRENGTHS).value).toBe("Clear examples.");
  expect(textbox(view, SUGGESTIONS).value).toBe("");
  expect(view.queryByText("Unsaved changes")).toBeNull();
});

test("Save draft sends the answers, trimmed and without blank ones", async () => {
  const view = await openForm([
    { criterionId: 1, rating: 4, comments: "Old note" },
  ]);
  expect(view.queryByText("Unsaved changes")).toBeNull();

  fireEvent.click(rating(view, "Preparedness & Organization", 2));
  fireEvent.change(textbox(view, ENGAGEMENT_NOTES), {
    target: { value: "   " },
  });
  fireEvent.change(textbox(view, STRENGTHS), {
    target: { value: "  Clear examples.\nGood pacing.  \n" },
  });
  fireEvent.change(textbox(view, SUGGESTIONS), { target: { value: " \n " } });
  expect(view.getByText("Unsaved changes")).toBeDefined();

  let release = () => {};
  gate = new Promise((resolve) => {
    release = resolve;
  });
  fireEvent.click(view.getByRole("button", { name: "Save draft" }));
  const saving = view.getByRole("button", { name: "Saving…" });
  expect((saving as HTMLButtonElement).disabled).toBe(true);
  expect(
    (view.getByRole("button", { name: "Submit" }) as HTMLButtonElement)
      .disabled,
  ).toBe(true);
  release();

  await waitFor(() =>
    expect(view.getByRole("status").textContent).toMatch(
      /^Draft saved at \d{1,2}:\d{2} [AP]M$/,
    ),
  );
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

  // Another change makes the draft unsaved again.
  fireEvent.click(rating(view, "Student Engagement", 5));
  expect(view.getByText("Unsaved changes")).toBeDefined();
  expect(view.getByRole("status").textContent).toBe("");
});

test("submitting with a required question unanswered shows what is missing and sends nothing", async () => {
  const view = await openForm([{ criterionId: 1, rating: 3, comments: null }]);
  fireEvent.click(view.getByRole("button", { name: "Submit" }));

  const summary = view.getByRole("alert");
  expect(summary.textContent).toContain(
    "Answer these 2 required questions before you submit:",
  );
  expect(
    within(summary)
      .getAllByRole("button")
      .map((button) => button.textContent),
  ).toEqual(["Preparedness & Organization", "Strengths observed"]);

  const preparedness = view.getByRole("radiogroup", {
    name: "Preparedness & Organization",
  });
  expect(preparedness.getAttribute("aria-invalid")).toBe("true");
  expect(description(preparedness)).toContain("Choose a rating.");
  expectFocused(rating(view, "Preparedness & Organization", 1));
  const strengths = textbox(view, STRENGTHS);
  expect(strengths.getAttribute("aria-invalid")).toBe("true");
  expect(description(strengths)).toBe("Write an answer.");
  expect(
    view
      .getByRole("radiogroup", { name: "Student Engagement" })
      .getAttribute("aria-invalid"),
  ).toBeNull();
  expect(textbox(view, SUGGESTIONS).getAttribute("aria-invalid")).toBeNull();

  // The summary's links move to the question.
  fireEvent.click(within(summary).getByRole("button", { name: STRENGTHS }));
  expectFocused(strengths);

  // Answering a question clears its error.
  fireEvent.click(rating(view, "Preparedness & Organization", 3));
  expect(preparedness.getAttribute("aria-invalid")).toBeNull();
  expect(view.getByRole("alert").textContent).toContain(
    "Answer this required question before you submit:",
  );

  // Whitespace is not an answer.
  fireEvent.change(strengths, { target: { value: "   " } });
  fireEvent.click(view.getByRole("button", { name: "Submit" }));
  expect(view.queryByRole("region", { name: "Submit observation" })).toBeNull();
  expectFocused(strengths);
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

  let confirm = within(
    view.getByRole("region", { name: "Submit observation" }),
  );
  expectFocused(confirm.getByRole("heading", { name: "Submit observation" }));
  expect(
    confirm.getByText(
      "After you submit, you can no longer change your answers.",
    ),
  ).toBeDefined();
  expect(confirm.queryByRole("checkbox")).toBeNull();
  expect(view.queryByRole("alert")).toBeNull();
  expect(sent).toEqual([]);

  // Keep editing goes back to the form.
  fireEvent.click(confirm.getByRole("button", { name: "Keep editing" }));
  expect(view.queryByRole("region", { name: "Submit observation" })).toBeNull();
  await waitFor(() =>
    expectFocused(view.getByRole("button", { name: "Submit" })),
  );

  fireEvent.click(view.getByRole("button", { name: "Submit" }));
  confirm = within(view.getByRole("region", { name: "Submit observation" }));
  fireEvent.click(confirm.getByRole("button", { name: "Submit" }));

  // The form's buttons are gone, so focus moves to the confirmation.
  const notice = await view.findByText("You submitted this observation.");
  await waitFor(() => expectFocused(notice));
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
  expect(view.queryByRole("button", { name: "Save draft" })).toBeNull();
  expect(
    within(view.getByRole("region", { name: "Summary" })).getByText(
      "Submitted",
    ),
  ).toBeDefined();
});

test("an API error shows the reason and keeps every answer", async () => {
  const view = await openForm([
    { criterionId: 1, rating: 4, comments: null },
    { criterionId: 2, rating: 5, comments: null },
  ]);
  fireEvent.change(textbox(view, STRENGTHS), {
    target: { value: "Clear examples." },
  });

  writeFailure = Response.json(
    { detail: "Comments must be 4000 characters or fewer." },
    { status: 400 },
  );
  fireEvent.click(view.getByRole("button", { name: "Save draft" }));
  const alert = await view.findByRole("alert");
  expect(alert.textContent).toBe(
    "The draft was not saved. Comments must be 4000 characters or fewer. Your answers are still here.",
  );
  expect(textbox(view, STRENGTHS).value).toBe("Clear examples.");
  expect(rating(view, "Student Engagement", 4).checked).toBe(true);
  expect(view.getByText("Unsaved changes")).toBeDefined();
  expect(
    (view.getByRole("button", { name: "Save draft" }) as HTMLButtonElement)
      .disabled,
  ).toBe(false);

  writeFailure = Response.json(
    { detail: "This observation is not in progress." },
    { status: 409 },
  );
  fireEvent.click(view.getByRole("button", { name: "Submit" }));
  const confirm = within(
    view.getByRole("region", { name: "Submit observation" }),
  );
  fireEvent.click(confirm.getByRole("button", { name: "Submit" }));
  await waitFor(() =>
    expect(view.getByRole("alert").textContent).toBe(
      "The observation was not submitted. This observation is not in progress. Your answers are still here.",
    ),
  );
  expect(textbox(view, STRENGTHS).value).toBe("Clear examples.");
  // The confirmation stays open, so the observer can try again.
  expect(
    view.getByRole("region", { name: "Submit observation" }),
  ).toBeDefined();
  expect(sent.map((request) => request.path)).toEqual([
    "/api/observations/5/responses",
    "/api/observations/5/submit",
  ]);
});

/** The observation as another tab left it, submitted. */
function submittedElsewhere(): ObservationDetail {
  return {
    ...inProgress([{ criterionId: 1, rating: 2, comments: null }]),
    status: "SUBMITTED",
    submittedAt: "2026-10-27T10:55:00",
    canEdit: false,
  };
}

const SUBMITTED_LINE =
  "You submitted this observation on Oct 27, 2026, 10:55 AM CT.";

const ALREADY_SUBMITTED = () =>
  Response.json(
    { detail: "This observation has already been submitted." },
    { status: 409 },
  );

test("a save refused because the form was submitted elsewhere ends on the submitted record", async () => {
  const view = await openForm([{ criterionId: 1, rating: 4, comments: null }]);
  fireEvent.change(textbox(view, STRENGTHS), {
    target: { value: "Clear examples." },
  });
  current = submittedElsewhere();
  writeFailure = ALREADY_SUBMITTED();
  fireEvent.click(view.getByRole("button", { name: "Save draft" }));

  await view.findByText(SUBMITTED_LINE);
  expect(view.queryByRole("radiogroup")).toBeNull();
  expect(view.queryByRole("button", { name: "Save draft" })).toBeNull();
  // This tab did not submit it, so it does not say so.
  expect(view.queryByText("You submitted this observation.")).toBeNull();
  expect(sent.map((request) => request.path)).toEqual([
    "/api/observations/5/responses",
  ]);

  // The answers that could not be saved no longer hold the dialog open.
  await waitFor(() => expect(leavingAsks()).toBe(false));
  fireEvent.keyDown(window, { key: "Escape" });
  await waitFor(() => expect(window.location.pathname).toBe("/faculty"));
});

test("a start refused because the form was submitted elsewhere ends on the submitted record", async () => {
  open();
  const view = await dialog();
  const start = await view.findByRole("button", { name: "Start observation" });
  current = submittedElsewhere();
  writeFailure = ALREADY_SUBMITTED();
  fireEvent.click(start);

  await view.findByText(SUBMITTED_LINE);
  expect(view.queryByRole("button", { name: "Start observation" })).toBeNull();
  expect(view.queryByRole("radiogroup")).toBeNull();
});

test("reopening a submitted observation shows the answers read-only, and when they were submitted", async () => {
  current = {
    ...structuredClone(completedObservation),
    observationId: 5,
    status: "SUBMITTED",
  };
  open();
  const view = await dialog();
  await view.findByText(
    "You submitted this observation on Oct 20, 2026, 9:15 AM CT.",
  );
  // The confirmation is only for a submission made just now.
  expect(view.queryByText("You submitted this observation.")).toBeNull();

  expect(
    within(view.getByRole("region", { name: "Summary" })).getByText(
      "Submitted",
    ),
  ).toBeDefined();
  const responses = within(view.getByRole("region", { name: "Responses" }));
  expect(responses.getByText("Rated 4 out of 5")).toBeDefined();
  expect(responses.getByText("Students asked questions.")).toBeDefined();
  expect(responses.getByText("Rated 5 out of 5")).toBeDefined();
  // The optional last question was left blank.
  expect(responses.getAllByText("No response")).toHaveLength(1);

  expect(view.queryByRole("radiogroup")).toBeNull();
  expect(view.queryAllByRole("textbox")).toHaveLength(0);
  expect(view.queryAllByRole("button")).toHaveLength(0);
  expect(sent).toEqual([]);
});

test("an observation that can't be opened shows the API's reason, and Retry tries again", async () => {
  readFailure = Response.json(
    { detail: "You do not have access to this resource." },
    { status: 403 },
  );
  open();
  const view = await dialog();
  const alert = await view.findByRole("alert");
  expect(alert.textContent).toContain(
    "You do not have access to this resource.",
  );

  fireEvent.click(within(alert).getByRole("button", { name: "Retry" }));
  await view.findByRole("button", { name: "Start observation" });
});

const LEAVE_WARNING =
  "Your unsaved answers will be lost. Save a draft first to keep them.";

/** The dialog's own question before unsaved answers are thrown away. */
async function leavePrompt() {
  return within(
    await screen.findByRole("alertdialog", { name: "Leave without saving?" }),
  );
}

function leavePromptShown() {
  return (
    screen.queryByRole("alertdialog", { name: "Leave without saving?" }) !==
    null
  );
}

/** True when leaving the page would make the browser ask first. */
function leavingAsks() {
  const event = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(event);
  return event.defaultPrevented;
}

/** The shaded area around the dialog, which closes it like the Close link. */
function backdrop() {
  const link = screen
    .getAllByRole("link", { name: "Close" })
    .find((element) => !element.closest("dialog"));
  if (!link) throw new Error("No backdrop");
  return link;
}

const CLOSE_WAYS: [string, (view: View) => void][] = [
  ["Escape", () => fireEvent.keyDown(window, { key: "Escape" })],
  [
    "Close",
    (view) => fireEvent.click(view.getByRole("link", { name: "Close" })),
  ],
  ["the backdrop", () => fireEvent.click(backdrop())],
];

test("with unsaved answers, closing asks in the dialog first, and Keep editing keeps the form and its answers", async () => {
  const view = await openForm();
  fireEvent.click(rating(view, "Student Engagement", 4));
  const strengths = textbox(view, STRENGTHS);
  fireEvent.change(strengths, { target: { value: "Clear examples." } });

  for (const [, close] of CLOSE_WAYS) {
    strengths.focus();
    close(view);
    const prompt = await leavePrompt();
    expect(prompt.getByText(LEAVE_WARNING)).toBeDefined();
    const stay = prompt.getByRole("button", { name: "Keep editing" });
    expectFocused(stay);
    // The form behind the question can't be used until it is answered.
    expect(strengths.closest("[inert]")).not.toBeNull();

    fireEvent.click(stay);
    expect(leavePromptShown()).toBe(false);
    await waitFor(() => expectFocused(strengths));
  }

  // Escape answers the question like Keep editing.
  fireEvent.keyDown(window, { key: "Escape" });
  await leavePrompt();
  fireEvent.keyDown(window, { key: "Escape" });
  expect(leavePromptShown()).toBe(false);

  expect(window.location.pathname).toBe("/faculty/observations/5");
  const form = await dialog();
  expect(rating(form, "Student Engagement", 4).checked).toBe(true);
  expect(textbox(form, STRENGTHS).value).toBe("Clear examples.");
  expect(form.getByText("Unsaved changes")).toBeDefined();
  expect(sent).toEqual([]);
});

test("Escape that ends an input method's composition neither asks nor closes", async () => {
  const view = await openForm();
  fireEvent.change(textbox(view, STRENGTHS), {
    target: { value: "Clear examples." },
  });

  fireEvent.keyDown(window, { key: "Escape", isComposing: true });

  expect(leavePromptShown()).toBe(false);
  expect(window.location.pathname).toBe("/faculty/observations/5");
  expect(textbox(await dialog(), STRENGTHS).value).toBe("Clear examples.");
});

test.each(CLOSE_WAYS)(
  "with unsaved answers, Leave without saving lets %s close the form",
  async (_, close) => {
    const view = await openForm();
    fireEvent.click(rating(view, "Student Engagement", 4));

    close(view);
    const prompt = await leavePrompt();
    fireEvent.click(
      prompt.getByRole("button", { name: "Leave without saving" }),
    );

    await waitFor(() => expect(window.location.pathname).toBe("/faculty"));
    expect(
      screen.queryByRole("dialog", { name: "Classroom Observation" }),
    ).toBeNull();
    expect(sent).toEqual([]);
  },
);

test.each(CLOSE_WAYS)(
  "an untouched form closes with %s without asking",
  async (_, close) => {
    const view = await openForm([
      { criterionId: 1, rating: 4, comments: null },
    ]);

    close(view);

    await waitFor(() => expect(window.location.pathname).toBe("/faculty"));
  },
);

test("saved or submitted answers close without asking", async () => {
  let view = await openForm();
  fireEvent.click(rating(view, "Student Engagement", 4));
  fireEvent.click(view.getByRole("button", { name: "Save draft" }));
  await view.findByText(/^Draft saved at/);
  // The dialog hears that the form is clean on its next render.
  await waitFor(() => expect(leavingAsks()).toBe(false));
  fireEvent.click(view.getByRole("link", { name: "Close" }));
  await waitFor(() => expect(window.location.pathname).toBe("/faculty"));

  // Typed answers that go out with the submission are not lost either.
  cleanup();
  view = await openForm([
    { criterionId: 1, rating: 4, comments: null },
    { criterionId: 2, rating: 5, comments: null },
  ]);
  fireEvent.change(textbox(view, STRENGTHS), {
    target: { value: "Clear examples." },
  });
  fireEvent.click(view.getByRole("button", { name: "Submit" }));
  const confirm = within(
    view.getByRole("region", { name: "Submit observation" }),
  );
  fireEvent.click(confirm.getByRole("button", { name: "Submit" }));
  await view.findByText("You submitted this observation.");
  await waitFor(() => expect(leavingAsks()).toBe(false));
  fireEvent.keyDown(window, { key: "Escape" });
  await waitFor(() => expect(window.location.pathname).toBe("/faculty"));
});

test("while answers are unsaved, leaving or reloading the page asks first", async () => {
  const view = await openForm();
  expect(leavingAsks()).toBe(false);

  fireEvent.click(rating(view, "Student Engagement", 4));
  expect(leavingAsks()).toBe(true);

  fireEvent.click(view.getByRole("button", { name: "Save draft" }));
  await view.findByText(/^Draft saved at/);
  await waitFor(() => expect(leavingAsks()).toBe(false));
});

test("a link without a numeric id says so and loads nothing", async () => {
  open("/faculty/observations/cs4348-501");
  const view = await dialog();
  expect(view.getByText("This observation link is not valid.")).toBeDefined();
  // Let the dashboard behind the dialog finish loading first.
  await screen.findByRole("heading", { name: "My Evaluation Dashboard" });
  expect(observationRequests()).toHaveLength(0);
});
