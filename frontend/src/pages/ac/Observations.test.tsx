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
import type { AuditEntry } from "../../api/observations";
import { observationTracking } from "../../test/acDashboardFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { completedObservation } from "../../test/observationFixtures";

type Answer = () => Response | Promise<Response>;

const REMINDERS = "/api/ac-dashboard/observations/reminders";
const unavailable = (detail: string) => () =>
  Response.json({ detail }, { status: 503 });

let answerObservations: Answer;
let answerAuditLog: Answer;
let reminderRequests: RequestInit[];
let auditLogRequests: number;

const auditLog: AuditEntry[] = [
  {
    event: "SUBMITTED",
    actorName: "Juniper Ashgrove",
    recipientName: null,
    occurredAt: "2026-10-20T14:00:00",
  },
  {
    event: "OBSERVEE_SIGNED",
    actorName: "Orrin Bramblecote",
    recipientName: null,
    occurredAt: "2026-10-20T14:15:00",
  },
  {
    event: "FORWARDED",
    actorName: "Alice Alice",
    recipientName: "Orrin Bramblecote",
    occurredAt: "2026-10-21T09:05:00",
  },
];

async function openCompletedDetails() {
  answerObservations = () =>
    Response.json({
      ...observationTracking,
      observations: observationTracking.observations.map((row) => ({
        ...row,
        observationId: row.status === "COMPLETED" ? 11 : null,
      })),
    });
  await renderObservations();
  await expandFaculty();
  fireEvent.click(
    screen.getByRole("button", {
      name: "View Orrin Bramblecote's observation",
    }),
  );
}

async function auditEntries() {
  const log = await screen.findByRole("region", { name: "Audit log" });
  const entries = await within(log).findAllByRole("listitem");
  return entries.map((entry) =>
    Array.from(entry.querySelectorAll("p"), (line) => line.textContent),
  );
}

beforeEach(() => {
  answerObservations = () => Response.json(observationTracking);
  answerAuditLog = () => Response.json(auditLog);
  reminderRequests = [];
  auditLogRequests = 0;
  fakeBackend({
    "/api/ac-dashboard/observations": () => answerObservations(),
    "/api/observations/11": () =>
      Response.json({ ...completedObservation, viewerRole: "COMMITTEE" }),
    "/api/observations/11/audit-log": () => {
      auditLogRequests += 1;
      return answerAuditLog();
    },
    [REMINDERS]: (init) => {
      reminderRequests.push(init ?? {});
      return Response.json({ sent: 3 });
    },
  });
  signInAs("ac");
});

afterEach(() => {
  cleanup();
  resetBackend();
});

async function renderObservations() {
  window.history.pushState({}, "", "/ac/observations");
  render(<App />);
  await screen.findByRole("heading", {
    level: 1,
    name: "Observation Tracking",
  });
}

// Reads the value shown on the KPI card with this label.
function kpi(label: string) {
  const card = screen.getByText(label, { selector: "p" }).parentElement;
  if (!card) throw new Error(`Missing ${label} card`);
  return card.querySelectorAll("p")[1]?.textContent;
}

// Each row's cells as text, observee first.
function rows() {
  const table = screen.getByRole("table", { name: "Observations" });
  return within(table)
    .getAllByRole("row")
    .filter((row) => row.hasAttribute("data-observation-row"))
    .map((row) =>
      Array.from(row.querySelectorAll("th, td"), (cell) => cell.textContent),
    );
}

async function expandFaculty() {
  const buttons = await screen.findAllByRole("button", {
    name: /^Observations for /,
  });
  for (const button of buttons) {
    if (button.getAttribute("aria-expanded") === "false")
      fireEvent.click(button);
  }
}

function remindButton() {
  return screen.getByRole("button", {
    name: /Send Reminders|Sending/,
  }) as HTMLButtonElement;
}

test("rows and card counts show each observation when the list loads", async () => {
  await renderObservations();
  await expandFaculty();
  await screen.findByRole("rowheader", { name: "Marlow Fenwick" });
  expect(rows()).toEqual([
    [
      "Marlow Fenwick",
      "Tessa Quillfeather",
      "CS 3345.001",
      "Nov 4, 8:30 AM",
      "Scheduled",
      "",
    ],
    [
      "Orrin Bramblecote",
      "Juniper Ashgrove",
      "CS 3345.002",
      "Oct 20, 1:00 PM",
      "Completed",
      "View",
    ],
    [
      "Cassius Wrenfield",
      "Delphine Marrowby",
      "CS 3345.003",
      "Oct 22, 10:00 AM",
      "Missing",
      "",
    ],
    [
      "Ewan Thistlewood",
      "—Not assigned",
      "CS 3345.004",
      "—Not scheduled",
      "Awaiting Observer",
      "",
    ],
  ]);
  expect(kpi("Scheduled")).toBe("1");
  expect(kpi("Completed")).toBe("1");
  expect(kpi("Missing")).toBe("1");
  expect(kpi("Observation Deadline")).toBe("Nov 15");
});

test("details open when View is clicked on a completed observation", async () => {
  await renderObservations();
  await expandFaculty();
  fireEvent.click(
    await screen.findByRole("button", {
      name: "View Orrin Bramblecote's observation",
    }),
  );
  const dialog = screen.getByRole("dialog", { name: "Observation Details" });
  expect(
    within(dialog).getByText("Orrin Bramblecote • CS 3345.002"),
  ).toBeDefined();
  expect(within(dialog).getByText("Juniper Ashgrove")).toBeDefined();

  fireEvent.click(within(dialog).getByRole("button", { name: "Close" }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("reports how many reminders went out when Send Reminders is clicked", async () => {
  await renderObservations();
  await waitFor(() => expect(remindButton().disabled).toBe(false));
  fireEvent.click(remindButton());
  expect((await screen.findByRole("status")).textContent).toBe(
    "Sent 3 reminders.",
  );
  expect(reminderRequests).toHaveLength(1);
  expect(reminderRequests[0]?.method).toBe("POST");
});

test("shows an error and Retry reloads when the observations fail to load", async () => {
  answerObservations = () =>
    Response.json({ detail: "Observations unavailable" }, { status: 503 });
  await renderObservations();
  const alert = await screen.findByRole("alert");
  expect(alert.textContent).toContain("Observations unavailable");
  expect(remindButton().disabled).toBe(true);

  answerObservations = () => Response.json(observationTracking);
  fireEvent.click(within(alert).getByRole("button", { name: "Retry" }));
  await expandFaculty();
  await screen.findByRole("rowheader", { name: "Marlow Fenwick" });
  expect(screen.queryByRole("alert")).toBeNull();
});

test("shows the deferral details instead of Awaiting Observer when a case is deferred", async () => {
  const data = structuredClone(observationTracking);
  const row = data.observations[0];
  if (!row) throw Error("Missing observation");
  row.status = "DEFERRED";
  row.observerId = null;
  row.observer = null;
  row.date = null;
  row.startTime = null;
  row.deferral = {
    reason: "No eligible observers",
    deferredBy: "Kaia Clearbrook",
    deferredAt: "2026-11-02T09:00:00",
    targetSemester: "Spring 2027",
  };
  data.observations = [row];
  answerObservations = () => Response.json(data);
  await renderObservations();
  await expandFaculty();
  await screen.findByText("Deferred", { selector: "td" });
  expect(screen.queryByText("Awaiting Observer")).toBeNull();
  fireEvent.click(
    screen.getByRole("button", { name: `View ${row.observee}'s deferral` }),
  );
  const dialog = await screen.findByRole("dialog", {
    name: "Deferral Details",
  });
  expect(within(dialog).getByText("No eligible observers")).toBeTruthy();
  expect(within(dialog).getByText("Deferred to Spring 2027")).toBeTruthy();
});

test("pending sign-off is distinct from completed and missing includes its unfinished step", async () => {
  const first = observationTracking.observations[0];
  if (!first) throw new Error("Missing observation fixture");
  answerObservations = () =>
    Response.json({
      ...observationTracking,
      observations: [
        { ...first, status: "PENDING_SIGN_OFF" },
        {
          ...first,
          signupId: 999,
          observerId: 999,
          status: "MISSING",
          missingReason: "Not signed",
        },
      ],
    });
  await renderObservations();
  await expandFaculty();
  expect(kpi("Pending sign-off")).toBe("1");
  expect(kpi("Completed")).toBe("0");
  expect(kpi("Missing")).toBe("1");
  expect(screen.getByText("Not signed")).toBeDefined();
  expect(rows().some((row) => row.includes("Pending sign-off"))).toBe(true);
});

test("faculty dropdown groups multiple observers and toggles their rows", async () => {
  const first = observationTracking.observations[0];
  if (!first) throw new Error("Missing observation");
  answerObservations = () =>
    Response.json({
      ...observationTracking,
      observations: [
        first,
        { ...first, observerId: 999, observer: "Second Observer" },
      ],
    });
  await renderObservations();
  const button = await screen.findByRole("button", {
    name: `Observations for ${first.observee}`,
  });
  expect(screen.queryByText("Second Observer")).toBeNull();
  expect(within(button).getByText("2 observations scheduled")).toBeDefined();
  expect(
    screen.getAllByRole("button", { name: /^Observations for / }),
  ).toHaveLength(1);
  fireEvent.click(button);
  expect(screen.getByText("Second Observer")).toBeDefined();
  expect(rows()).toHaveLength(2);
  fireEvent.click(button);
  expect(rows()).toHaveLength(0);
  expect(button.getAttribute("aria-expanded")).toBe("false");
});

test("collapsed faculty header summarizes mixed observation statuses", async () => {
  const first = observationTracking.observations[0];
  if (!first) throw new Error("Missing observation");
  answerObservations = () =>
    Response.json({
      ...observationTracking,
      observations: [
        first,
        {
          ...first,
          observerId: 999,
          observer: "Second Observer",
          status: "COMPLETED",
        },
      ],
    });
  await renderObservations();
  const button = await screen.findByRole("button", {
    name: `Observations for ${first.observee}`,
  });
  expect(button.getAttribute("aria-expanded")).toBe("false");
  expect(within(button).getByText("1 scheduled, 1 completed")).toBeDefined();
});

test("AC can view the submitted form from completed observation details", async () => {
  await openCompletedDetails();
  fireEvent.click(screen.getByRole("button", { name: "View submitted form" }));
  await screen.findByRole("dialog", { name: "Submitted Observation Form" });
  await screen.findByText("Student Engagement");
  expect(
    screen.queryByRole("button", { name: "Submit observation" }),
  ).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Hide submitted form" }));
  expect(screen.queryByText("Student Engagement")).toBeNull();
});

test("AC can view the audit log from completed observation details, oldest first", async () => {
  await openCompletedDetails();
  fireEvent.click(screen.getByRole("button", { name: "View audit log" }));
  expect(await auditEntries()).toEqual([
    [
      "Submitted and signed by observer",
      "By Juniper Ashgrove · Oct 20, 2026, 2:00 PM CT",
    ],
    ["Signed by observee", "By Orrin Bramblecote · Oct 20, 2026, 2:15 PM CT"],
    [
      "Record forwarded",
      "By Alice Alice to Orrin Bramblecote · Oct 21, 2026, 9:05 AM CT",
    ],
  ]);
  fireEvent.click(screen.getByRole("button", { name: "Hide audit log" }));
  expect(screen.queryByRole("region", { name: "Audit log" })).toBeNull();
});

test("an empty audit log says nothing has been recorded", async () => {
  answerAuditLog = () => Response.json([]);
  await openCompletedDetails();
  fireEvent.click(screen.getByRole("button", { name: "View audit log" }));
  expect(
    await screen.findByText("Nothing has been recorded yet."),
  ).toBeDefined();
});

test("a failed audit log shows the error and Retry loads it again", async () => {
  answerAuditLog = unavailable("Audit log is unavailable.");
  await openCompletedDetails();
  fireEvent.click(screen.getByRole("button", { name: "View audit log" }));
  const alert = await screen.findByRole("alert");
  expect(alert.textContent).toContain("Audit log is unavailable.");

  answerAuditLog = () => Response.json(auditLog);
  fireEvent.click(within(alert).getByRole("button", { name: "Retry" }));
  expect(await auditEntries()).toHaveLength(3);
});

test("opening the form while the audit log is open loads the log again to include the view", async () => {
  await openCompletedDetails();
  fireEvent.click(screen.getByRole("button", { name: "View audit log" }));
  await auditEntries();
  const before = auditLogRequests;

  fireEvent.click(screen.getByRole("button", { name: "View submitted form" }));
  await screen.findByText("Student Engagement");
  await waitFor(() => expect(auditLogRequests).toBe(before + 1));
});
