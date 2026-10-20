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
import { AuthProvider } from "../../auth/AuthContext";
import { AcOverview } from "./Overview";
import { notificationTemplate, type OverdueQueue } from "./overdueApi";

const originalFetch = globalThis.fetch;
afterEach(() => {
  cleanup();
  globalThis.fetch = originalFetch;
});

const queue: OverdueQueue = {
  cycleId: 1,
  cycleLabel: "Fall 2026",
  count: 2,
  faculty: [
    {
      professorId: 1,
      name: "Alice Nguyen",
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

function sampleFaculty(index: number) {
  const faculty = queue.faculty[index];
  if (!faculty) throw new Error("Missing faculty test fixture");
  return faculty;
}

test("personalizes rank, interval, name, history, and follow-up", () => {
  const alice = notificationTemplate(sampleFaculty(0), queue.cycleLabel);
  const bob = notificationTemplate(sampleFaculty(1), queue.cycleLabel);
  expect(alice.body).toContain("Alice Nguyen");
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
  globalThis.fetch = mock(async (_input: unknown, init?: RequestInit) => {
    if (init?.method === "POST") {
      body = JSON.parse(String(init.body));
      return Response.json({ sentAt: "2026-10-20T10:00:00" });
    }
    return Response.json(queue);
  }) as unknown as typeof fetch;
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
  globalThis.fetch = mock(async (_input: unknown, init?: RequestInit) =>
    init?.method === "POST"
      ? Response.json(
          { detail: "Could not save notification" },
          { status: 500 },
        )
      : Response.json(queue),
  ) as unknown as typeof fetch;
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
  globalThis.fetch = mock(async () =>
    ++attempts === 1
      ? Response.json({ message: "Backend unavailable" }, { status: 503 })
      : Response.json({ ...queue, count: 0, faculty: [] }),
  ) as unknown as typeof fetch;
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
