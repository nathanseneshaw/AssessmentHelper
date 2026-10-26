import { afterEach, beforeEach, expect, test } from "bun:test";
import {
  act,
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
const NOT_SIGNED_UP_NAMES = notSignedUpFaculty.map((person) => person.name);

function dueQueueOf(faculty: OverdueFaculty[]): OverdueQueue {
  return {
    cycleId: 1,
    cycleLabel: "Fall 2026",
    count: faculty.length,
    faculty,
  };
}

const dueQueue = dueQueueOf([...signedUpFaculty, ...notSignedUpFaculty]);

const state: AcState = {
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
const pending = () => new Promise<Response>(() => {});
const unavailable = (detail: string) => () =>
  Response.json({ detail }, { status: 503 });

let answerMetrics: Answer;
let answerDue: Answer;
let answerObserver: Answer;
let sentEmail: NotificationEmail | undefined;
const originalTimeZone = process.env.TZ;

beforeEach(() => {
  answerMetrics = () => Response.json(signupMetrics);
  answerDue = () => Response.json(dueQueue);
  answerObserver = () => Response.json(state);
  sentEmail = undefined;
  fakeBackend({
    "/api/ac-dashboard/metrics": () => answerMetrics(),
    "/api/due-evaluations": () => answerDue(),
    "/api/observer-eligibility/ac": () => answerObserver(),
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
  if (originalTimeZone === undefined) Reflect.deleteProperty(process.env, "TZ");
  else process.env.TZ = originalTimeZone;
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

async function openNotSignedUp() {
  await waitFor(() => expect(viewAllButton().disabled).toBe(false));
  fireEvent.click(viewAllButton());
  return screen.getByRole("dialog", { name: "Faculty Not Yet Signed Up" });
}

// The faculty names in the dialog's table, in order.
function listedNames(dialog: HTMLElement) {
  return within(dialog)
    .getAllByRole("row")
    .slice(1)
    .map((row) => within(row).getAllByRole("cell")[0]?.textContent);
}

test("each signup request has a Review link named for its professor", async () => {
  await renderSignups();
  await screen.findByRole("rowheader", { name: "Marlow Fenwick" });
  const table = screen.getByRole("table");

  for (const { name } of signedUpFaculty) {
    expect(within(table).getByRole("rowheader", { name })).toBeDefined();
    expect(
      within(table).getByRole("link", { name: `Review signup for ${name}` }),
    ).toBeDefined();
  }
});

test("count cards show live values, with participation rounded to a whole percent", async () => {
  await renderSignups();
  await waitFor(() => expect(kpi("Due Faculty").value).toBe("8"));
  expect(kpi("Due Faculty").note).toBe("Current cycle");
  expect(kpi("Signed Up")).toEqual({ value: "3", note: "38% participation" });
  expect(kpi("Not Yet Signed Up")).toEqual({
    value: "5",
    note: "Reminder recommended",
  });
});

test("with no faculty due, the counts are zero and say so", async () => {
  answerMetrics = () =>
    Response.json({
      ...signupMetrics,
      facultyDue: 0,
      participation: { count: 0, total: 0 },
    });
  await renderSignups();
  await waitFor(() => expect(kpi("Due Faculty").value).toBe("0"));
  expect(kpi("Signed Up")).toEqual({
    value: "0",
    note: "No faculty are due this cycle",
  });
  expect(kpi("Not Yet Signed Up")).toEqual({
    value: "0",
    note: "All due faculty have signed up",
  });
  expect(document.body.textContent).not.toMatch(/NaN|Infinity/);
});

test("Signup Deadline shows the cycle's deadline on the same day west of UTC", async () => {
  // Midnight UTC on Oct 31 is still Oct 30 in Chicago.
  process.env.TZ = "America/Chicago";
  await renderSignups();
  await waitFor(() => expect(kpi("Signup Deadline").value).toBe("Oct 31"));
  expect(kpi("Signup Deadline").note).toBe("Fall 2026 cycle");
});

test("Signup Deadline says Not set when the cycle has no deadline", async () => {
  answerObserver = () =>
    Response.json({ ...state, cycle: { ...cycle, signupDeadline: null } });
  await renderSignups();
  await waitFor(() => expect(kpi("Signup Deadline").value).toBe("Not set"));
  expect(kpi("Signup Deadline").note).toBe("Fall 2026 cycle");
});

test("cards show a dash and View All waits while the data loads", async () => {
  answerMetrics = pending;
  answerDue = pending;
  answerObserver = pending;
  await renderSignups();
  for (const label of [
    "Due Faculty",
    "Signed Up",
    "Not Yet Signed Up",
    "Signup Deadline",
  ]) {
    expect(kpi(label)).toEqual({ value: "—", note: "Loading…" });
  }
  expect(viewAllButton().disabled).toBe(true);
});

test("View All lists the due faculty who have not signed up", async () => {
  await renderSignups();
  await waitFor(() => expect(kpi("Not Yet Signed Up").value).toBe("5"));
  const dialog = await openNotSignedUp();

  expect(listedNames(dialog)).toEqual(NOT_SIGNED_UP_NAMES);
  expect(listedNames(dialog)).toHaveLength(
    Number(kpi("Not Yet Signed Up").value),
  );
  for (const { name } of signedUpFaculty) {
    expect(within(dialog).queryByText(name)).toBeNull();
  }
  expect(
    within(dialog).getByText(
      "Fall 2026 evaluation cycle • 5 faculty not signed up",
    ),
  ).toBeDefined();

  fireEvent.click(within(dialog).getByRole("button", { name: "Close" }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("notifying someone who has not signed up sends the signup reminder and marks them Notified", async () => {
  await renderSignups();
  const dialog = await openNotSignedUp();
  fireEvent.click(
    within(dialog).getByRole("button", { name: "Notify Juniper Ashgrove" }),
  );
  expect(
    (within(dialog).getByLabelText("Message") as HTMLTextAreaElement).value,
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
  expect(listedNames(dialog)).toEqual(NOT_SIGNED_UP_NAMES);
  expect(
    within(dialog).getByRole("button", { name: "Notify Cassius Wrenfield" }),
  ).toBeDefined();
});

test("when every due faculty member has signed up, View All says so", async () => {
  answerMetrics = () =>
    Response.json({
      ...signupMetrics,
      facultyDue: 3,
      participation: { count: 3, total: 3 },
    });
  answerDue = () => Response.json(dueQueueOf(signedUpFaculty));
  await renderSignups();
  await waitFor(() =>
    expect(kpi("Not Yet Signed Up")).toEqual({
      value: "0",
      note: "All due faculty have signed up",
    }),
  );
  expect(kpi("Signed Up").note).toBe("100% participation");
  const dialog = await openNotSignedUp();

  expect(
    within(dialog).getByText("All due faculty have signed up."),
  ).toBeDefined();
  expect(
    within(dialog).getByText(
      "Fall 2026 evaluation cycle • 0 faculty not signed up",
    ),
  ).toBeDefined();
  expect(within(dialog).queryAllByRole("button", { name: /^Notify/ })).toEqual(
    [],
  );
});

test("a failed metrics request marks its cards unavailable without an error message", async () => {
  answerMetrics = unavailable("Metrics unavailable");
  await renderSignups();
  await waitFor(() => expect(kpi("Due Faculty").value).toBe("Unavailable"));
  for (const label of ["Due Faculty", "Signed Up", "Not Yet Signed Up"]) {
    expect(kpi(label)).toEqual({
      value: "Unavailable",
      note: "Metrics could not be loaded",
    });
  }
  expect(screen.queryByRole("alert")).toBeNull();
  expect(screen.queryByText(/Metrics unavailable/)).toBeNull();
  expect(screen.queryByRole("button", { name: /Retry/ })).toBeNull();
  await waitFor(() => expect(kpi("Signup Deadline").value).toBe("Oct 31"));
  // The list comes from the due queue, so it can still be opened.
  await waitFor(() => expect(viewAllButton().disabled).toBe(false));
});

test("View All stays disabled without an error message when the due list fails", async () => {
  let dueRequested = false;
  answerDue = () => {
    dueRequested = true;
    return unavailable("Due list unavailable")();
  };
  await renderSignups();
  await screen.findByRole("rowheader", { name: "Marlow Fenwick" });
  await waitFor(() => expect(kpi("Due Faculty").value).toBe("8"));
  expect(dueRequested).toBe(true);
  // Let the failed response settle before checking the button.
  await act(() => new Promise((resolve) => setTimeout(resolve, 20)));
  expect(viewAllButton().disabled).toBe(true);
  expect(screen.queryByRole("alert")).toBeNull();
  expect(screen.queryByText(/Due list unavailable/)).toBeNull();
  expect(screen.queryByRole("button", { name: /Retry/ })).toBeNull();
});

test("when the signups fail to load, the deadline is unavailable and View All is disabled", async () => {
  answerObserver = unavailable("Signups unavailable");
  await renderSignups();
  expect((await screen.findByRole("alert")).textContent).toBe(
    "Signups unavailable",
  );
  expect(kpi("Signup Deadline")).toEqual({
    value: "Unavailable",
    note: "Cycle could not be loaded",
  });
  await waitFor(() => expect(kpi("Due Faculty").value).toBe("8"));
  expect(viewAllButton().disabled).toBe(true);
});
