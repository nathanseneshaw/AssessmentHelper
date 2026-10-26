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
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { faculty, section } from "../../test/observerFixtures";

let state: FacultyState;
let sent: { path: string; body: Record<string, unknown> }[];

beforeEach(() => {
  state = structuredClone(faculty);
  sent = [];
  fakeBackend({
    "/api/observer-eligibility/faculty/1": () => Response.json(state),
    "/api/observer-eligibility/faculty/1/requests/7/respond": (init, url) => {
      const body = JSON.parse(String(init?.body));
      sent.push({ path: url.pathname, body });
      const r = state.incoming[0];
      if (r) r.status = body.accept ? "CONFIRMED" : "DECLINED";
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
  await waitFor(() =>
    expect(
      screen.getByRole("heading", { name: "Accepted Requests (1)" }),
    ).toBeDefined(),
  );
  expect(sent).toEqual([
    {
      path: "/api/observer-eligibility/faculty/1/requests/7/respond",
      body: { accept: true },
    },
  ]);
  const accepted = screen
    .getByRole("heading", { name: "Accepted Requests (1)" })
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
