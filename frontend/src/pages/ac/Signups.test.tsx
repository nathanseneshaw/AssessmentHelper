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
import type { DashboardMetrics } from "../../api/acDashboard";
import type {
  NotificationEmail,
  OverdueFaculty,
  OverdueQueue,
} from "../../api/evaluationEligibility";
import type { AcState } from "../../api/observerEligibility";
import { metrics } from "../../test/acDashboardFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { cycle, signup } from "../../test/observerFixtures";

type Answer = () => Response | Promise<Response>;

function dueFaculty(
  professorId: number,
  firstName: string,
  lastName: string,
): OverdueFaculty {
  return {
    professorId,
    name: `${firstName} ${lastName}`,
    lastName,
    rank: "ASSOCIATE",
    email: `${lastName.toLowerCase()}@test.edu`,
    lastEvaluationCycle: "Fall 2024",
    nextDueDate: "2026-08-24",
    intervalYears: 2,
    lastNotifiedAt: null,
  };
}

function dueQueueOf(faculty: OverdueFaculty[]): OverdueQueue {
  return {
    cycleId: 1,
    cycleLabel: "Fall 2026",
    count: faculty.length,
    faculty,
  };
}

// Eight faculty are due this cycle and the first three have signed up.
const signedUpFaculty = [
  dueFaculty(11, "Marlow", "Fenwick"),
  dueFaculty(12, "Tessa", "Quillfeather"),
  dueFaculty(13, "Orrin", "Bramblecote"),
];
const notSignedUpFaculty = [
  dueFaculty(21, "Juniper", "Ashgrove"),
  dueFaculty(22, "Cassius", "Wrenfield"),
  dueFaculty(23, "Delphine", "Marrowby"),
  dueFaculty(24, "Ewan", "Thistlewood"),
  dueFaculty(25, "Fenna", "Larkspur"),
];

const dueQueue: OverdueQueue = {
  cycleId: 1,
  cycleLabel: "Fall 2026",
  count: 8,
  faculty: [...signedUpFaculty, ...notSignedUpFaculty],
};

const state: AcState = {
  matchingCases: [],
  cycle,
  signups: signedUpFaculty.map((person, i) => ({
    ...signup,
    signupId: i + 1,
    professorId: person.professorId,
    name: person.name,
  })),
};

// 3 of 8 is 37.5%, which rounds up.
const signupMetrics: DashboardMetrics = {
  ...metrics,
  facultyDue: 8,
  participation: { count: 3, total: 8 },
};

const NOTIFY_PATH = "/api/due-evaluations/1/professors/21/notifications";
const unavailable = (detail: string) => () =>
  Response.json({ detail }, { status: 503 });

let answerObserver: Answer;
let answerOverdue: Answer;
let answerEligibility: Answer;
let sentEmail: NotificationEmail | undefined;

beforeEach(() => {
  answerObserver = () => Response.json(state);
  answerOverdue = () => Response.json(dueQueueOf([]));
  answerEligibility = () =>
    Response.json({
      ...dueQueue,
      faculty: dueQueue.faculty.map((p) => ({ ...p, status: "Due" })),
    });
  sentEmail = undefined;
  fakeBackend({
    "/api/ac-dashboard/metrics": () => Response.json(signupMetrics),
    "/api/due-evaluations": () => Response.json(dueQueue),
    "/api/overdue-evaluations": () => answerOverdue(),
    "/api/evaluation-eligibility": () => answerEligibility(),
    "/api/observer-eligibility/ac": () => answerObserver(),
    [NOTIFY_PATH.replace("/notifications", "/email-preview")]: () =>
      Response.json({
        recipient: "ashgrove@test.edu",
        subject: "Faculty Evaluation Due - Fall 2026",
        body: "Please complete your evaluation signup.",
      }),
    [NOTIFY_PATH]: (init) => {
      sentEmail = JSON.parse(String(init?.body));
      return Response.json({ sentAt: "2026-10-26T09:30:00" });
    },
  });
  signInAs("ac");
});

afterEach(() => {
  cleanup();
  resetBackend();
});

async function renderSignups() {
  window.history.pushState({}, "", "/ac/signups");
  render(<App />);
  await screen.findByRole("heading", { level: 1, name: "Evaluation Signups" });
}

// Reads the value and note shown on the KPI card with this label.
function kpi(label: string) {
  const card = screen.getByText(label, { selector: "p" }).parentElement;
  if (!card) throw new Error(`Missing ${label} card`);
  const [, value, note] = card.querySelectorAll("p");
  return { value: value?.textContent, note: note?.textContent };
}

function viewAllButton() {
  return screen.getByRole("button", {
    name: "View all faculty not yet signed up",
  }) as HTMLButtonElement;
}

// The faculty names in the dialog's table, in order.
function listedNames(dialog: HTMLElement) {
  return within(dialog)
    .getAllByRole("row")
    .slice(1)
    .map((row) => within(row).getAllByRole("cell")[0]?.textContent);
}

test("each signup has a Review link and cards show live counts when signups load", async () => {
  await renderSignups();
  await screen.findByRole("rowheader", { name: "Marlow Fenwick" });
  const table = screen.getByRole("table");
  for (const { name } of signedUpFaculty) {
    expect(
      within(table).getByRole("link", { name: `Review signup for ${name}` }),
    ).toBeDefined();
  }

  await waitFor(() => expect(kpi("Due Faculty").value).toBe("8"));
  expect(kpi("Signed Up Due This Term")).toEqual({
    value: "3",
    note: "38% participation",
  });
  expect(kpi("Not Yet Signed Up").value).toBe("5");
  expect(kpi("Signup Deadline").value).toBe("Oct 31");
});

test("a professor is marked Notified when their reminder is sent from View All", async () => {
  await renderSignups();
  await waitFor(() => expect(viewAllButton().disabled).toBe(false));
  fireEvent.click(viewAllButton());
  const dialog = screen.getByRole("dialog", {
    name: "Faculty Not Yet Signed Up",
  });
  expect(listedNames(dialog)).toEqual(
    notSignedUpFaculty.map((person) => person.name),
  );

  fireEvent.click(
    within(dialog).getByRole("button", { name: "Notify Juniper Ashgrove" }),
  );
  expect(
    ((await within(dialog).findByLabelText("Message")) as HTMLTextAreaElement)
      .value,
  ).toContain("complete your evaluation signup");
  fireEvent.click(within(dialog).getByRole("button", { name: "Send Email" }));
  await within(dialog).findByRole("heading", { name: "Email Sent" });
  expect(sentEmail?.recipient).toBe("ashgrove@test.edu");
  fireEvent.click(within(dialog).getByRole("button", { name: "Done" }));

  const row = within(dialog)
    .getByRole("button", { name: "Follow up with Juniper Ashgrove" })
    .closest("tr");
  if (!row) throw new Error("Missing row for Juniper Ashgrove");
  expect(within(row).getByText("Notified")).toBeDefined();
});

test("shows the error and disables View All when the signups fail to load", async () => {
  answerObserver = () =>
    Response.json({ detail: "Signups unavailable" }, { status: 503 });
  await renderSignups();
  expect((await screen.findByRole("alert")).textContent).toBe(
    "Signups unavailable",
  );
  expect(kpi("Signup Deadline").value).toBe("Unavailable");
  await waitFor(() => expect(kpi("Due Faculty").value).toBe("8"));
  expect(viewAllButton().disabled).toBe(true);
});

test("signup counts and row statuses distinguish due, overdue and not yet due faculty", async () => {
  const overdue = dueFaculty(31, "Alex", "Overdue");
  const upcoming = dueFaculty(32, "Sam", "Upcoming");
  answerEligibility = () =>
    Response.json({
      ...dueQueue,
      faculty: [
        ...dueQueue.faculty.map((p) => ({ ...p, status: "Due" })),
        { ...overdue, status: "Overdue", nextDueSemester: "Fall 2025" },
        { ...upcoming, status: "Upcoming", nextDueSemester: "Spring 2028" },
      ],
    });
  answerOverdue = () => Response.json(dueQueueOf([overdue]));
  answerObserver = () =>
    Response.json({
      ...state,
      signups: [
        ...state.signups,
        ...[overdue, upcoming].map((person, i) => ({
          ...signup,
          signupId: i + 4,
          professorId: person.professorId,
          name: person.name,
        })),
      ],
    });
  await renderSignups();
  await waitFor(() => {
    const card = screen.getByText("Other Signups").parentElement;
    if (!card) throw new Error("Missing Other Signups card");
    for (const label of ["Overdue", "Not Yet Due"]) {
      expect(
        within(card).getByText(label).previousElementSibling?.textContent,
      ).toBe("1");
    }
  });
  expect(kpi("Signed Up Due This Term").value).toBe("3");
  for (const [name, status] of [
    ["Marlow Fenwick", "Due this term (Fall 2026)"],
    [overdue.name, "Overdue from Fall 2025"],
    [upcoming.name, "Not yet due (Spring 2028)"],
  ] as const) {
    const row = screen.getByRole("rowheader", { name }).closest("tr");
    if (!row) throw new Error(`Missing signup row for ${name}`);
    await within(row).findByText(status);
    expect(within(row).getByText("Awaiting review")).toBeDefined();
  }
});

test("deferred signups show the target term even when eligibility is unavailable", async () => {
  answerEligibility = unavailable("Eligibility unavailable");
  answerObserver = () =>
    Response.json({
      ...state,
      matchingCases: [
        {
          signupId: state.signups[0]?.signupId,
          status: "DEFERRED",
          deferral: { targetSemester: "Spring 2027" },
        },
      ],
    });
  await renderSignups();
  await screen.findByText("Deferred to Spring 2027");
});

test("eligibility deferred to next semester shows the target term", async () => {
  answerEligibility = () =>
    Response.json({
      ...dueQueue,
      faculty: dueQueue.faculty.map((p) => ({
        ...p,
        status: "Due next semester",
        nextDueSemester: "Spring 2027",
      })),
    });
  await renderSignups();
  await waitFor(() =>
    expect(screen.getAllByText("Deferred to Spring 2027")).toHaveLength(3),
  );
});

test("failed eligibility lookup does not label signups as not yet due", async () => {
  answerOverdue = unavailable("Overdue list unavailable");
  await renderSignups();
  await waitFor(() =>
    expect(screen.getAllByText("Eligibility unavailable")).toHaveLength(3),
  );
  expect(screen.queryByText("Not yet due")).toBeNull();
});
