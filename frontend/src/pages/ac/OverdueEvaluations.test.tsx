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
import type { OverdueQueue } from "../../api/evaluationEligibility";
import { AuthProvider } from "../../auth/AuthContext";
import { metrics } from "../../test/acDashboardFixtures";
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
  const notified = new Set<number>();
  globalThis.fetch = mock(async (input: unknown, init?: RequestInit) => {
    const path = String(input);
    const preview = path.match(
      /\/api\/(due|overdue)-evaluations\/1\/professors\/(\d+)\/email-preview$/,
    );
    if (preview) {
      const id = Number(preview[2]);
      const person = queue.faculty.find((p) => p.professorId === id);
      if (!person) throw new Error("Unknown preview fixture");
      return Response.json({
        recipient: person.email,
        subject:
          (notified.has(id) ? "Follow Up: " : "") +
          (preview[1] === "due"
            ? "Faculty Evaluation Due - Fall 2026"
            : "Faculty Evaluation Overdue - Fall 2026 Evaluation Required"),
        body:
          "Dear Professor " +
          person.lastName +
          ", Please complete your evaluation signup.",
      });
    }
    if (path.endsWith("/ac-dashboard/metrics")) return Response.json(metrics);
    if (path.endsWith("/api/missing-observations"))
      return Response.json({
        cycleId: 1,
        cycleLabel: "Fall 2026",
        observationDeadline: null,
        signups: [],
      });
    const response = await handler(input, init);
    const sent = path.match(/professors\/(\d+)\/notifications$/);
    if (sent && init?.method === "POST" && response.ok)
      notified.add(Number(sent[1]));
    return response;
  }) as unknown as typeof fetch;
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

async function openList(name: string) {
  const viewAll = screen.getByRole("button", { name }) as HTMLButtonElement;
  await waitFor(() => expect(viewAll.disabled).toBe(false));
  fireEvent.click(viewAll);
}

test("the edited email is sent and a follow-up offered when an overdue professor is notified", async () => {
  let body: Record<string, string> | undefined;
  mockFetch(async (_input: unknown, init?: RequestInit) => {
    if (init?.method === "POST") {
      body = JSON.parse(String(init.body));
      return Response.json({ sentAt: "2026-10-20T10:00:00" });
    }
    return Response.json(queue);
  });
  openDashboard();
  await openList("View all overdue evaluations");
  fireEvent.click(screen.getByRole("button", { name: "Notify Alice Nguyen" }));
  expect(((await screen.findByLabelText("To")) as HTMLInputElement).value).toBe(
    "alice@utdallas.edu",
  );
  fireEvent.change(await screen.findByLabelText("Subject"), {
    target: { value: "Personal reminder" },
  });
  fireEvent.change(await screen.findByLabelText("Message"), {
    target: { value: "Please complete your evaluation, Alice." },
  });
  fireEvent.click(screen.getByRole("button", { name: "Send Email" }));
  await screen.findByRole("heading", { name: "Email Sent" });
  expect(body?.subject).toBe("Personal reminder");
  expect(body?.body).toBe("Please complete your evaluation, Alice.");

  fireEvent.click(screen.getByRole("button", { name: "Done" }));
  const followUp = screen.getByRole("button", {
    name: "Follow up with Alice Nguyen",
  });
  const row = followUp.closest("tr");
  if (!row) throw new Error("Missing professor row");
  expect(within(row).getByText("Notified")).toBeDefined();
  fireEvent.click(followUp);
  expect(
    ((await screen.findByLabelText("Subject")) as HTMLInputElement).value,
  ).toStartWith("Follow Up:");
});

test("the draft is kept and the reason shown when sending fails", async () => {
  mockFetch(async (_input: unknown, init?: RequestInit) =>
    init?.method === "POST"
      ? Response.json(
          { detail: "Could not save notification" },
          { status: 500 },
        )
      : Response.json(queue),
  );
  openDashboard();
  await openList("View all overdue evaluations");
  fireEvent.click(screen.getByRole("button", { name: "Notify Alice Nguyen" }));
  await screen.findByLabelText("Subject");
  fireEvent.click(screen.getByRole("button", { name: "Send Email" }));
  expect((await screen.findByRole("alert")).textContent).toContain(
    "Could not save notification",
  );
  expect(screen.queryByRole("heading", { name: "Email Sent" })).toBeNull();
  expect(screen.getByLabelText("Subject")).toBeDefined();
});

test("the email goes to the due endpoint when sent from the due list", async () => {
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
  await openList("View all faculty due for evaluation");
  const dialog = screen.getByRole("dialog", {
    name: "Faculty Due for Evaluation",
  });
  fireEvent.click(
    within(dialog).getByRole("button", { name: "Notify Alice Nguyen" }),
  );
  expect(
    ((await screen.findByLabelText("Subject")) as HTMLInputElement).value,
  ).toBe("Faculty Evaluation Due - Fall 2026");
  fireEvent.click(screen.getByRole("button", { name: "Send Email" }));
  await screen.findByRole("heading", { name: "Email Sent" });
  expect(savedUrl).toBe(
    `${API_URL}/api/due-evaluations/1/professors/1/notifications`,
  );
  expect(savedBody?.recipient).toBe("alice@utdallas.edu");
});
