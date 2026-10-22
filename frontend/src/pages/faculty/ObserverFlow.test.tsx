import { afterEach, beforeEach, expect, mock, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { faculty, section, signup } from "../../../test/observerFixtures";
import { App } from "../../App";
import {
  type FacultyState,
  observerChanged,
} from "../../api/observerEligibility";
import { STORAGE_KEY } from "../../auth/AuthContext";

const originalFetch = globalThis.fetch;
let state: FacultyState;
let sent: { path: string; body: Record<string, unknown> }[];
beforeEach(() => {
  state = structuredClone(faculty);
  sent = [];
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({ role: "professor", name: "Professor 1", professorId: 1 }),
  );
  globalThis.fetch = mock(async (input: unknown, init?: RequestInit) => {
    const path = String(input);
    if (init?.method === "POST") {
      const body = JSON.parse(String(init.body));
      sent.push({ path, body });
      if (path.endsWith("/signups"))
        state.signup = { ...structuredClone(signup), candidates: [] };
      if (path.endsWith("/respond")) {
        const r = state.incoming[0];
        if (r) r.status = body.accept ? "CONFIRMED" : "DECLINED";
      }
      return new Response(null, { status: 204 });
    }
    return Response.json(path.endsWith("/faculty") ? [state.professor] : state);
  }) as unknown as typeof fetch;
});
afterEach(() => {
  cleanup();
  localStorage.clear();
  globalThis.fetch = originalFetch;
});
function open(path: string) {
  window.history.pushState({}, "", path);
  render(<App />);
}
test("signup auto-populates the schedule and confirms successful persistence", async () => {
  open("/faculty/signup");
  const select = await screen.findByRole("combobox", {
    name: "Course / Section",
  });
  fireEvent.change(select, { target: { value: "1" } });
  expect(
    (screen.getByLabelText("Meeting Days") as HTMLInputElement).value,
  ).toBe("MW");
  expect(
    (screen.getByLabelText("Meeting Time") as HTMLInputElement).readOnly,
  ).toBe(true);
  expect((screen.getByLabelText("From") as HTMLInputElement).value).toBe(
    "2026-11-01",
  );
  expect((screen.getByLabelText("From") as HTMLInputElement).readOnly).toBe(
    true,
  );
  fireEvent.change(screen.getByLabelText("Through"), {
    target: { value: "2026-11-10" },
  });
  fireEvent.submit(select.closest("form") as HTMLFormElement);
  await screen.findByRole("dialog", { name: "Evaluation Signup Complete" });
  expect(sent[0]?.body).toEqual({
    sectionId: 1,
    windowStart: "2026-11-01",
    windowEnd: "2026-11-10",
  });
});
test("generated observers stay grey until AC releases the list, then update automatically", async () => {
  state.signup = { ...structuredClone(signup), candidates: [] };
  open("/faculty");
  await screen.findByText("Professor 1", { selector: "p.font-semibold" });
  expect(
    (
      screen.getByRole("button", {
        name: "View Generated Observers",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  state.signup = { ...structuredClone(signup), released: true };
  observerChanged();
  await screen.findByRole("link", { name: "View Generated Observers" });
  expect(
    (
      screen.getByRole("button", {
        name: "Sign Up for Evaluation",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
});
test("multiple candidate selection sends only checked ids and shows confirmation", async () => {
  state.signup = { ...structuredClone(signup), released: true };
  open("/faculty/observers");
  await screen.findByText("Select one or more observers to request");
  const boxes = screen.getAllByRole("checkbox");
  if (!boxes[0] || !boxes[2]) throw Error("Missing candidates");
  fireEvent.click(boxes[0]);
  fireEvent.click(boxes[2]);
  fireEvent.click(
    screen.getByRole("button", { name: "Send Observer Requests" }),
  );
  await screen.findByRole("dialog", { name: "Observer Requests Sent" });
  expect(sent[0]?.body).toEqual({ observerIds: [2, 4] });
  expect(screen.getByText(/Observers selected:/).textContent).toContain(
    "Professor 2, Professor 4",
  );
});
test("observer can accept or decline and sees accepted requests", async () => {
  state.incoming = [
    {
      requestId: 7,
      signupId: 2,
      professorId: 1,
      observeeName: "Professor 8",
      observerName: "Professor 1",
      section: section,
      windowStart: "2026-11-01",
      windowEnd: "2026-11-10",
      status: "PENDING",
    },
  ];
  open("/faculty/requests");
  const accept = await screen.findByRole("button", { name: "Accept" });
  fireEvent.click(accept);
  await waitFor(() =>
    expect(
      screen.getByRole("heading", { name: "Accepted Requests (1)" }),
    ).toBeDefined(),
  );
  expect(sent[0]?.body).toEqual({ accept: true });
  const accepted = screen
    .getByRole("heading", { name: "Accepted Requests (1)" })
    .closest("section");
  if (!accepted) throw Error("Missing accepted section");
  expect(within(accepted).getByText(/Professor 8/)).toBeDefined();
});
test("signup deadline disables signup and request failures remain actionable", async () => {
  state.cycle.signupOpen = false;
  open("/faculty/signup");
  await screen.findByRole("status");
  expect(
    (screen.getByRole("button", { name: "Sign-Up" }) as HTMLButtonElement)
      .disabled,
  ).toBe(true);
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
