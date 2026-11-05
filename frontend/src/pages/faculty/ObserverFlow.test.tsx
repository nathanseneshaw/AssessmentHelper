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
import type { FacultyState } from "../../api/observerEligibility";
import { dashboard } from "../../test/facultyDashboardFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { faculty, section, signup } from "../../test/observerFixtures";

let state: FacultyState;
let sent: { path: string; body: Record<string, unknown> }[];

beforeEach(() => {
  state = structuredClone(faculty);
  sent = [];
  fakeBackend({
    "/api/observer-eligibility/faculty/1/requests/7/dates": () =>
      Response.json(["2026-11-03", "2026-11-05"]),
    "/api/observer-eligibility/faculty/1": () => Response.json(state),
    "/api/professors/1/observations": () => Response.json([]),
    "/api/professors/1/dashboard": () => Response.json(dashboard),
    "/api/observer-eligibility/faculty/1/requests/7/schedule": (init, url) => {
      const body = JSON.parse(String(init?.body));
      sent.push({ path: url.pathname, body });
      if (state.incoming[0])
        state.incoming[0].observationDate = body.observationDate;
      return new Response(null, { status: 204 });
    },
    "/api/observer-eligibility/faculty/1/requests/7/respond": (init, url) => {
      const body = JSON.parse(String(init?.body));
      sent.push({ path: url.pathname, body });
      const r = state.incoming[0];
      if (r) {
        r.status = body.accept ? "CONFIRMED" : "DECLINED";
        r.observationDate = body.observationDate;
      }
      return new Response(null, { status: 204 });
    },
  });
  signInAs("professor", { professorId: 1, name: "Professor 1" });
});

afterEach(() => {
  cleanup();
  resetBackend();
});

function open(path: string) {
  window.history.pushState({}, "", path);
  render(<App />);
}

function pendingRequest() {
  state.incoming = [
    {
      requestId: 7,
      signupId: 2,
      professorId: 1,
      observeeName: "Professor 8",
      observerName: "Professor 1",
      section,
      windowStart: "2026-11-01",
      windowEnd: "2026-11-10",
      status: "PENDING",
    },
  ];
}

test("the dashboard shows the signed-in professor and pending requests", async () => {
  pendingRequest();
  open("/faculty");
  await screen.findByText("Professor 1", { selector: "p.font-semibold" });
  expect(
    screen.getByRole("link", { name: "Observer Requests (1 pending)" }),
  ).toBeDefined();
  expect(screen.queryByLabelText(/Demo faculty identity/)).toBeNull();
});

test("observer can accept and sees accepted requests", async () => {
  pendingRequest();
  open("/faculty/requests");
  fireEvent.click(await screen.findByRole("button", { name: "Accept" }));
  const dates = await screen.findByLabelText("Observation date");
  expect(sent).toHaveLength(0);
  expect(
    (
      screen.getByRole("button", {
        name: "Accept & Schedule",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  fireEvent.change(dates, { target: { value: "2026-11-03" } });
  fireEvent.click(screen.getByRole("button", { name: "Accept & Schedule" }));
  await waitFor(() =>
    expect(
      screen.getByRole("heading", { name: "Accepted / Assigned Requests (1)" }),
    ).toBeDefined(),
  );
  expect(sent).toEqual([
    {
      path: "/api/observer-eligibility/faculty/1/requests/7/respond",
      body: { accept: true, observationDate: "2026-11-03" },
    },
  ]);
  const accepted = screen
    .getByRole("heading", { name: "Accepted / Assigned Requests (1)" })
    .closest("section");
  if (!accepted) throw Error("Missing accepted section");
  expect(within(accepted).getByText(/Professor 8/)).toBeDefined();
});

test("observer can decline a request", async () => {
  pendingRequest();
  open("/faculty/requests");
  fireEvent.click(await screen.findByRole("button", { name: "Decline" }));
  await screen.findByRole("heading", { name: "Declined Requests (1)" });
  expect(sent[0]?.body).toEqual({ accept: false });
});

test("notification history includes timestamps and entries beyond the first five", async () => {
  state.notifications = Array.from({ length: 7 }, (_, i) => ({
    notificationId: i + 1,
    message: `Notification ${i + 1}`,
    createdAt: "2026-10-21T10:30:00-05:00",
  }));
  open("/faculty");
  const history = await screen.findByRole("list", {
    name: "Notification history",
  });
  expect(within(history).getAllByRole("listitem")).toHaveLength(7);
  expect(
    within(history).getAllByText("Oct 21, 2026, 10:30 AM CT"),
  ).toHaveLength(7);
  expect(history.className).toContain("overflow-y-auto");
});

test("sessions without a faculty profile load nothing instead of using another professor", async () => {
  signInAs("professor", { professorId: null });
  open("/faculty/requests");
  await screen.findByText(/Your session has no faculty profile/);
  await screen.findByText(/This account has no faculty profile/);
  const observerCalls = (
    globalThis.fetch as unknown as Mock<typeof fetch>
  ).mock.calls.filter(([input]) =>
    String(input).includes("/api/observer-eligibility/"),
  );
  expect(observerCalls).toHaveLength(0);
});

test("generated observers stay disabled until the committee sends the list", async () => {
  state.signup = { ...signup, released: false };
  open("/faculty");
  await screen.findByText("Professor 1", { selector: "p.font-semibold" });
  const button = screen.getByRole("button", {
    name: "View Generated Observers",
  }) as HTMLButtonElement;
  expect(button.disabled).toBe(true);
  fireEvent.click(button);
  expect(window.location.pathname).toBe("/faculty");
});

test("a sent list enables the orange generated observers link", async () => {
  state.signup = { ...signup, released: true };
  open("/faculty");
  const link = await screen.findByRole("link", {
    name: "View Generated Observers",
  });
  expect(link.className).toContain("bg-brand-orange");
  fireEvent.click(link);
  expect(window.location.pathname).toBe("/faculty/observers");
});

test("signup is disabled once the signup period closes", async () => {
  state.cycle.signupOpen = false;
  open("/faculty");
  await screen.findByText("Professor 1", { selector: "p.font-semibold" });
  expect(
    (
      screen.getByRole("button", {
        name: "Sign Up for Evaluation",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
});

test("an AC-assigned observer can choose a date and see the saved schedule", async () => {
  pendingRequest();
  const request = state.incoming[0];
  if (!request) throw Error("Missing request");
  request.status = "CONFIRMED";
  request.assignedByAc = true;
  open("/faculty/requests");
  await screen.findByText("Assigned by AC");
  expect(screen.queryByRole("button", { name: "Accept" })).toBeNull();
  fireEvent.click(
    screen.getByRole("button", { name: "Choose Observation Date" }),
  );
  fireEvent.change(await screen.findByLabelText("Observation date"), {
    target: { value: "2026-11-05" },
  });
  fireEvent.click(
    screen.getByRole("button", { name: "Save Observation Date" }),
  );
  await screen.findByText(/Observation scheduled: 2026-11-05/);
  expect(sent).toEqual([
    {
      path: "/api/observer-eligibility/faculty/1/requests/7/schedule",
      body: { observationDate: "2026-11-05" },
    },
  ]);
  expect(
    screen.queryByRole("button", { name: "Choose Observation Date" }),
  ).toBeNull();
});

test("an assigned observation stays pending on the dashboard until its date is chosen", async () => {
  pendingRequest();
  const request = state.incoming[0];
  if (!request) throw Error("Missing request");
  request.status = "CONFIRMED";
  request.assignedByAc = true;
  open("/faculty");
  await screen.findByRole("link", { name: "Observer Requests (1 pending)" });
  fireEvent.click(
    screen.getByRole("link", { name: "Observer Requests (1 pending)" }),
  );
  fireEvent.click(
    await screen.findByRole("button", { name: "Choose Observation Date" }),
  );
  fireEvent.change(await screen.findByLabelText("Observation date"), {
    target: { value: "2026-11-05" },
  });
  fireEvent.click(
    screen.getByRole("button", { name: "Save Observation Date" }),
  );
  await screen.findByText(/Observation scheduled: 2026-11-05/);
  await screen.findByRole("link", { name: "Observer Requests (0 pending)" });
});
