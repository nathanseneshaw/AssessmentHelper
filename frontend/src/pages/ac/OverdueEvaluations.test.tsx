import { afterEach, expect, mock, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { API_URL } from "../../api/client";
import {
  notificationTemplate,
  type OverdueQueue,
} from "../../api/evaluationEligibility";
import { AuthProvider } from "../../auth/AuthContext";
import { metrics } from "../../test/acDashboardFixtures";
import { OverdueEvaluations } from "./OverdueEvaluations";
import { AcOverview } from "./Overview";

const originalFetch = globalThis.fetch;
afterEach(() => {
  cleanup();
  globalThis.fetch = originalFetch;
});

// The dashboard also loads its KPI metrics, which these tests leave alone.
function mockFetch(
  handler: (input: unknown, init?: RequestInit) => Promise<Response>,
) {
  globalThis.fetch = mock(async (input: unknown, init?: RequestInit) =>
    String(input).endsWith("/ac-dashboard/metrics")
      ? Response.json(metrics)
      : handler(input, init),
  ) as unknown as typeof fetch;
}

const queue: OverdueQueue = {
  cycleId: 1,
  cycleLabel: "Fall 2026",
  count: 2,
  faculty: [
    {
      professorId: 1,
      name: "Alice Nguyen",
      lastName: "Nguyen",
      rank: "FULL",
      email: "alice@utdallas.edu",
      lastEvaluationCycle: "Spring 2023",
      nextDueDate: "2025-03-01",
      intervalYears: 2,
      lastNotifiedAt: null,
    },
    {
      professorId: 2,
      name: "Bob Martinez",
      lastName: "Martinez",
      rank: "ASSISTANT",
      email: "bob@utdallas.edu",
      lastEvaluationCycle: "Spring 2025",
      nextDueDate: "2026-03-01",
      intervalYears: 1,
      lastNotifiedAt: null,
    },
  ],
};

function openDashboard() {
  render(
    <MemoryRouter>
      <AuthProvider>
        <AcOverview />
      </AuthProvider>
    </MemoryRouter>,
  );
}

const emptyQueue: OverdueQueue = { ...queue, count: 0, faculty: [] };

function openModal(props: Partial<Parameters<typeof OverdueEvaluations>[0]>) {
  render(
    <OverdueEvaluations
      queue={queue}
      onClose={() => {}}
      onNotified={() => {}}
      {...props}
    />,
  );
}

function sampleFaculty(index: number) {
  const faculty = queue.faculty[index];
  if (!faculty) throw new Error("Missing faculty test fixture");
  return faculty;
}

test("personalizes rank, interval, name, history, and follow-up", () => {
  const alice = notificationTemplate(sampleFaculty(0), queue.cycleLabel);
  const bob = notificationTemplate(sampleFaculty(1), queue.cycleLabel);
  expect(alice.body).toContain("Dear Professor Nguyen,");
  expect(alice.body).toContain("every 2 years");
  expect(bob.body).toContain("every year");
  expect(bob.recipient).toBe("bob@utdallas.edu");
  expect(
    notificationTemplate(
      { ...sampleFaculty(0), lastNotifiedAt: "2026-10-20T10:00:00" },
      queue.cycleLabel,
    ).subject,
  ).toStartWith("Follow Up:");
});

test("opens overlay, saves edited email, confirms, and offers follow-up without reducing count", async () => {
  let body: Record<string, string> | undefined;
  mockFetch(async (_input: unknown, init?: RequestInit) => {
    if (init?.method === "POST") {
      body = JSON.parse(String(init.body));
      return Response.json({ sentAt: "2026-10-20T10:00:00" });
    }
    return Response.json(queue);
  });
  openDashboard();
  const viewAll = screen.getByRole("button", {
    name: "View all overdue evaluations",
  });
  await waitFor(() =>
    expect((viewAll as HTMLButtonElement).disabled).toBe(false),
  );
  fireEvent.click(viewAll);
  expect(screen.getByRole("dialog")).toBeDefined();
  fireEvent.click(screen.getByRole("button", { name: "Notify Alice Nguyen" }));
  expect((screen.getByLabelText("To") as HTMLInputElement).value).toBe(
    "alice@utdallas.edu",
  );
  fireEvent.change(screen.getByLabelText("Subject"), {
    target: { value: "Personal reminder" },
  });
  fireEvent.change(screen.getByLabelText("Message"), {
    target: { value: "Please complete your evaluation, Alice." },
  });
  fireEvent.click(screen.getByRole("button", { name: "Send Email" }));
  await screen.findByRole("heading", { name: "Email Sent" });
  expect(body?.subject).toBe("Personal reminder");
  expect(body?.body).toBe("Please complete your evaluation, Alice.");
  fireEvent.click(screen.getByRole("button", { name: "Done" }));
  expect(
    screen.getByText("Fall 2026 evaluation cycle • 2 faculty overdue"),
  ).toBeDefined();
  const followUp = screen.getByRole("button", {
    name: "Follow up with Alice Nguyen",
  });
  const row = followUp.closest("tr");
  if (!row) throw new Error("Missing professor row");
  expect(within(row).getByText("Notified")).toBeDefined();
  fireEvent.click(followUp);
  expect(
    (screen.getByLabelText("Subject") as HTMLInputElement).value,
  ).toStartWith("Follow Up:");
  fireEvent.click(screen.getByRole("button", { name: "Back" }));
  fireEvent.click(screen.getByRole("button", { name: "Notify Bob Martinez" }));
  expect((screen.getByLabelText("To") as HTMLInputElement).value).toBe(
    "bob@utdallas.edu",
  );
});

test("failed send keeps draft and overdue status", async () => {
  mockFetch(async (_input: unknown, init?: RequestInit) =>
    init?.method === "POST"
      ? Response.json(
          { detail: "Could not save notification" },
          { status: 500 },
        )
      : Response.json(queue),
  );
  openDashboard();
  const viewAll = screen.getByRole("button", {
    name: "View all overdue evaluations",
  });
  await waitFor(() =>
    expect((viewAll as HTMLButtonElement).disabled).toBe(false),
  );
  fireEvent.click(viewAll);
  fireEvent.click(screen.getByRole("button", { name: "Notify Alice Nguyen" }));
  fireEvent.click(screen.getByRole("button", { name: "Send Email" }));
  expect((await screen.findByRole("alert")).textContent).toContain(
    "Could not save notification",
  );
  expect(screen.queryByRole("heading", { name: "Email Sent" })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Back" }));
  expect(
    screen.getByRole("button", { name: "Notify Alice Nguyen" }),
  ).toBeDefined();
});

test("shows load error and retries without displaying a fabricated count", async () => {
  let attempts = 0;
  mockFetch(async (input: unknown) =>
    String(input).endsWith("/overdue-evaluations") && ++attempts === 1
      ? Response.json({ detail: "Backend unavailable" }, { status: 503 })
      : Response.json({ ...queue, count: 0, faculty: [] }),
  );
  openDashboard();
  await screen.findByRole("alert");
  fireEvent.click(screen.getByRole("button", { name: "Retry" }));
  const viewAll = screen.getByRole("button", {
    name: "View all overdue evaluations",
  });
  await waitFor(() =>
    expect((viewAll as HTMLButtonElement).disabled).toBe(false),
  );
  fireEvent.click(viewAll);
  expect(
    screen.getByText("No faculty are overdue for evaluation."),
  ).toBeDefined();
});

test("due overlay personalizes an editable email, saves to the due endpoint, and retains its count", async () => {
  let savedUrl = "";
  let savedBody: Record<string, string> | undefined;
  mockFetch(async (input: unknown, init?: RequestInit) => {
    if (init?.method === "POST") {
      savedUrl = String(input);
      savedBody = JSON.parse(String(init.body));
      return Response.json({ sentAt: "2026-10-21T10:00:00" });
    }
    return Response.json(
      String(input).endsWith("/due-evaluations")
        ? queue
        : { ...queue, count: 0, faculty: [] },
    );
  });
  openDashboard();
  const open = screen.getByRole("button", {
    name: "View all faculty due for evaluation",
  });
  await waitFor(() => expect((open as HTMLButtonElement).disabled).toBe(false));
  fireEvent.click(open);
  const dialog = screen.getByRole("dialog", {
    name: "Faculty Due for Evaluation",
  });
  expect(
    within(dialog).getByRole("columnheader", { name: "Next Due" }),
  ).toBeDefined();
  fireEvent.click(
    within(dialog).getByRole("button", { name: "Notify Alice Nguyen" }),
  );
  expect((screen.getByLabelText("Subject") as HTMLInputElement).value).toBe(
    "Faculty Evaluation Due - Fall 2026",
  );
  expect(
    (screen.getByLabelText("Message") as HTMLTextAreaElement).value,
  ).toContain("Dear Professor Nguyen,");
  fireEvent.change(screen.getByLabelText("Subject"), {
    target: { value: "Your evaluation is due" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Send Email" }));
  await screen.findByRole("heading", { name: "Email Sent" });
  expect(savedUrl).toBe(
    `${API_URL}/api/due-evaluations/1/professors/1/notifications`,
  );
  expect(savedBody?.recipient).toBe("alice@utdallas.edu");
  expect(savedBody?.subject).toBe("Your evaluation is due");
  fireEvent.click(screen.getByRole("button", { name: "Done" }));
  expect(
    screen.getByText("Fall 2026 evaluation cycle • 2 faculty due"),
  ).toBeDefined();
  fireEvent.click(
    screen.getByRole("button", { name: "Follow up with Alice Nguyen" }),
  );
  expect(
    (screen.getByLabelText("Subject") as HTMLInputElement).value,
  ).toStartWith("Follow Up: Faculty Evaluation Due");
});

test("due load failures can be retried independently and empty due results are explicit", async () => {
  let dueRequests = 0;
  mockFetch(async (input: unknown) =>
    String(input).endsWith("/due-evaluations") && ++dueRequests === 1
      ? Response.json({ detail: "Due service unavailable" }, { status: 503 })
      : Response.json({ ...queue, count: 0, faculty: [] }),
  );
  openDashboard();
  await screen.findByRole("alert");
  expect(
    (
      screen.getByRole("button", {
        name: "View all faculty due for evaluation",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  fireEvent.click(
    screen.getByRole("button", { name: "Retry due evaluations" }),
  );
  await screen.findByText("No faculty are due this cycle.");
});

const notSignedUp = {
  kind: "due",
  title: "Faculty Not Yet Signed Up",
  countLabel: "not signed up",
  emptyMessage: "All due faculty have signed up.",
} as const;

test("custom title and count label replace the default wording without changing the due rows or email", () => {
  openModal(notSignedUp);
  const dialog = screen.getByRole("dialog", {
    name: "Faculty Not Yet Signed Up",
  });
  expect(
    within(dialog).getByRole("heading", { name: "Faculty Not Yet Signed Up" }),
  ).toBeDefined();
  expect(
    within(dialog).getByText(
      "Fall 2026 evaluation cycle • 2 faculty not signed up",
    ),
  ).toBeDefined();
  expect(within(dialog).queryByText("Faculty Due for Evaluation")).toBeNull();
  expect(
    within(dialog).queryByText("All due faculty have signed up."),
  ).toBeNull();
  expect(
    within(dialog).getByRole("columnheader", { name: "Next Due" }),
  ).toBeDefined();
  const notify = within(dialog).getByRole("button", {
    name: "Notify Alice Nguyen",
  });
  const row = notify.closest("tr");
  if (!row) throw new Error("Missing professor row");
  expect(within(row).getByText("Due")).toBeDefined();
  fireEvent.click(notify);
  expect((screen.getByLabelText("Subject") as HTMLInputElement).value).toBe(
    "Faculty Evaluation Due - Fall 2026",
  );
});

test("custom empty message replaces the default empty state", () => {
  openModal({ ...notSignedUp, queue: emptyQueue });
  const dialog = screen.getByRole("dialog", {
    name: "Faculty Not Yet Signed Up",
  });
  expect(
    within(dialog).getByText(
      "Fall 2026 evaluation cycle • 0 faculty not signed up",
    ),
  ).toBeDefined();
  expect(
    within(dialog).getByText("All due faculty have signed up."),
  ).toBeDefined();
  expect(
    within(dialog).queryByText("No faculty are due for evaluation."),
  ).toBeNull();
});

test("default wording is derived from kind when no custom text is passed", () => {
  openModal({ kind: "due", queue: emptyQueue });
  let dialog = screen.getByRole("dialog", {
    name: "Faculty Due for Evaluation",
  });
  expect(
    within(dialog).getByText("Fall 2026 evaluation cycle • 0 faculty due"),
  ).toBeDefined();
  expect(
    within(dialog).getByText("No faculty are due for evaluation."),
  ).toBeDefined();
  cleanup();

  openModal({ queue: emptyQueue });
  dialog = screen.getByRole("dialog", {
    name: "Faculty Overdue for Evaluation",
  });
  expect(
    within(dialog).getByText("Fall 2026 evaluation cycle • 0 faculty overdue"),
  ).toBeDefined();
  expect(
    within(dialog).getByText("No faculty are overdue for evaluation."),
  ).toBeDefined();
});
