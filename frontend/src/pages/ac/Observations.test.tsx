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
import type { ObservationTracking } from "../../api/acDashboard";
import { observationTracking } from "../../test/acDashboardFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";

type Answer = () => Response | Promise<Response>;

const REMINDERS = "/api/ac-dashboard/observations/reminders";
const pending = () => new Promise<Response>(() => {});
const unavailable = (detail: string) => () =>
  Response.json({ detail }, { status: 503 });

let answerObservations: Answer;
let answerReminders: Answer;
let reminderRequests: RequestInit[];
const originalTimeZone = process.env.TZ;

beforeEach(() => {
  answerObservations = () => Response.json(observationTracking);
  answerReminders = () => Response.json({ sent: 3 });
  reminderRequests = [];
  fakeBackend({
    "/api/ac-dashboard/observations": () => answerObservations(),
    [REMINDERS]: (init) => {
      reminderRequests.push(init ?? {});
      return answerReminders();
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

async function renderObservations() {
  window.history.pushState({}, "", "/ac/observations");
  render(<App />);
  await screen.findByRole("heading", {
    level: 1,
    name: "Observation Tracking",
  });
}

// Reads the value and note shown on the KPI card with this label.
function kpi(label: string) {
  const card = screen.getByText(label, { selector: "p" }).parentElement;
  if (!card) throw new Error(`Missing ${label} card`);
  const [, value, note] = card.querySelectorAll("p");
  return { value: value?.textContent, note: note?.textContent };
}

// Each row's cells as text, observee first.
function rows() {
  const table = screen.getByRole("table", { name: "Observations" });
  return within(table)
    .getAllByRole("row")
    .slice(1)
    .map((row) =>
      Array.from(row.querySelectorAll("th, td"), (cell) => cell.textContent),
    );
}

function remindButton() {
  return screen.getByRole("button", {
    name: /Send Reminders|Sending/,
  }) as HTMLButtonElement;
}

test("each signup shows its observer, course, time and status", async () => {
  await renderObservations();
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
});

test("cards count the observations in each status and show the deadline on the same day west of UTC", async () => {
  // Midnight UTC on Nov 15 is still Nov 14 in Chicago.
  process.env.TZ = "America/Chicago";
  await renderObservations();
  await waitFor(() => expect(kpi("Scheduled").value).toBe("1"));
  expect(kpi("Scheduled").note).toBe("Upcoming observations");
  expect(kpi("Completed")).toEqual({
    value: "1",
    note: "Completed this cycle",
  });
  expect(kpi("Missing")).toEqual({
    value: "1",
    note: "Past their date and not completed",
  });
  expect(kpi("Observation Deadline")).toEqual({
    value: "Nov 15",
    note: "Fall 2026 cycle",
  });
});

test("the deadline says Not set when the cycle has none", async () => {
  answerObservations = () =>
    Response.json({ ...observationTracking, observationDeadline: null });
  await renderObservations();
  await waitFor(() =>
    expect(kpi("Observation Deadline").value).toBe("Not set"),
  );
});

test("View opens the completed observation's details", async () => {
  await renderObservations();
  fireEvent.click(
    await screen.findByRole("button", {
      name: "View Orrin Bramblecote's observation",
    }),
  );
  const dialog = screen.getByRole("dialog", { name: "Observation Details" });
  expect(
    within(dialog).getByText("Orrin Bramblecote • CS 3345.002"),
  ).toBeDefined();
  const details = Array.from(dialog.querySelectorAll("dt"), (term) => [
    term.textContent,
    term.nextElementSibling?.textContent,
  ]);
  expect(details).toEqual([
    ["Observer", "Juniper Ashgrove"],
    ["Date / Time", "Oct 20, 1:00 PM"],
    ["Signed by observee", "Yes"],
    ["Signed by observer", "Yes"],
    ["Completed", "Oct 20"],
  ]);

  fireEvent.click(within(dialog).getByRole("button", { name: "Close" }));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("only completed observations can be viewed", async () => {
  await renderObservations();
  await screen.findByRole("rowheader", { name: "Marlow Fenwick" });
  expect(
    screen
      .getAllByRole("button", { name: /^View / })
      .map((button) => button.getAttribute("aria-label")),
  ).toEqual(["View Orrin Bramblecote's observation"]);
});

test("cards show a dash and Send Reminders waits while the observations load", async () => {
  answerObservations = pending;
  await renderObservations();
  for (const label of [
    "Scheduled",
    "Completed",
    "Missing",
    "Observation Deadline",
  ]) {
    expect(kpi(label)).toEqual({ value: "—", note: "Loading…" });
  }
  expect(screen.getByText("Loading observations…")).toBeDefined();
  expect(remindButton().disabled).toBe(true);
});

test("with no generated signups, the table says so and the counts are zero", async () => {
  answerObservations = () =>
    Response.json({
      ...observationTracking,
      observations: [],
    } satisfies ObservationTracking);
  await renderObservations();
  await screen.findByText(/No observations to track yet/);
  expect(kpi("Scheduled").value).toBe("0");
  expect(kpi("Missing").value).toBe("0");
  expect(remindButton().disabled).toBe(true);
});

test("Send Reminders posts once and says how many reminders went out", async () => {
  await renderObservations();
  await waitFor(() => expect(remindButton().disabled).toBe(false));
  fireEvent.click(remindButton());
  expect((await screen.findByRole("status")).textContent).toBe(
    "Sent 3 reminders.",
  );
  expect(reminderRequests).toHaveLength(1);
  expect(reminderRequests[0]?.method).toBe("POST");
});

test("Send Reminders says so when no one needed one", async () => {
  answerReminders = () => Response.json({ sent: 0 });
  await renderObservations();
  await waitFor(() => expect(remindButton().disabled).toBe(false));
  fireEvent.click(remindButton());
  await waitFor(() =>
    expect(screen.getByRole("status").textContent).toBe(
      "No one needs a reminder right now.",
    ),
  );
});

test("a failed reminder shows the error and lets the committee try again", async () => {
  answerReminders = unavailable("Reminders are unavailable.");
  await renderObservations();
  await waitFor(() => expect(remindButton().disabled).toBe(false));
  fireEvent.click(remindButton());
  expect((await screen.findByRole("alert")).textContent).toBe(
    "Reminders are unavailable.",
  );
  expect(remindButton().disabled).toBe(false);
});

test("Send Reminders is disabled when every observation is completed", async () => {
  answerObservations = () =>
    Response.json({
      ...observationTracking,
      observations: observationTracking.observations.filter(
        (o) => o.status === "COMPLETED",
      ),
    });
  await renderObservations();
  await screen.findByRole("rowheader", { name: "Orrin Bramblecote" });
  expect(remindButton().disabled).toBe(true);
});

test("when the observations fail to load, the cards are unavailable and Retry loads them", async () => {
  answerObservations = unavailable("Observations unavailable");
  await renderObservations();
  const alert = await screen.findByRole("alert");
  expect(alert.textContent).toBe("Observations unavailable Retry");
  expect(kpi("Scheduled")).toEqual({
    value: "Unavailable",
    note: "Observations could not be loaded",
  });
  expect(remindButton().disabled).toBe(true);

  answerObservations = () => Response.json(observationTracking);
  fireEvent.click(within(alert).getByRole("button", { name: "Retry" }));
  await screen.findByRole("rowheader", { name: "Marlow Fenwick" });
  expect(screen.queryByRole("alert")).toBeNull();
  expect(kpi("Scheduled").value).toBe("1");
});
