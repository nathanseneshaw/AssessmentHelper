import { afterEach, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { App } from "../App";
import { apiFetch } from "../api/client";
import { metrics, observationTracking } from "../test/acDashboardFixtures";
import { fakeBackend, resetBackend, signInAs } from "../test/fakeBackend";
import { ac } from "../test/observerFixtures";
import { AcCycleScope } from "./AcCycleScope";

afterEach(() => {
  cleanup();
  resetBackend();
});

test("switches cycle-scoped requests and prevents writes to a past cycle", async () => {
  let fetched = "";
  fakeBackend({
    "/api/ac/cycles": () =>
      Response.json([
        { cycleId: 1, term: "Fall", year: 2026, period: "CURRENT" },
        { cycleId: 2, term: "Spring", year: 2026, period: "PAST" },
      ]),
    "/api/evaluation-eligibility/cycles/2": (_init, url) => {
      fetched = url.pathname;
      return Response.json({ cycleId: 2 });
    },
  });
  render(
    <AcCycleScope>
      <span>Cycle content</span>
    </AcCycleScope>,
  );
  const selector = await screen.findByLabelText("Evaluation cycle");
  fireEvent.click(selector);
  fireEvent.click(
    screen.getByRole("button", { name: "Spring 2026 Evaluation Cycle" }),
  );
  expect(screen.getByText("This past cycle is read-only.")).toBeTruthy();
  await screen.findByText("Cycle content");
  await apiFetch("/api/evaluation-eligibility");
  expect(fetched).toBe("/api/evaluation-eligibility/cycles/2");
  await expect(
    apiFetch("/api/observer-eligibility/generate", { method: "POST" }),
  ).rejects.toThrow("read-only");
});

test("shows an empty state when there is no current cycle", async () => {
  fakeBackend({ "/api/ac/cycles": () => Response.json([]) });
  render(
    <AcCycleScope>
      <span>Cycle content</span>
    </AcCycleScope>,
  );
  await waitFor(() =>
    expect(
      screen.getByText(/No current evaluation cycle\. Select/),
    ).toBeTruthy(),
  );
  expect(screen.queryByText("Cycle content")).toBeNull();
});

test("labels an upcoming cycle in the menu and after selecting it", async () => {
  fakeBackend({
    "/api/ac/cycles": () =>
      Response.json([
        { cycleId: 3, term: "Spring", year: 2027, period: "UPCOMING" },
      ]),
  });
  render(
    <AcCycleScope>
      <span>Cycle content</span>
    </AcCycleScope>,
  );
  fireEvent.click(await screen.findByLabelText("Evaluation cycle"));
  fireEvent.click(
    screen.getByRole("button", {
      name: "Spring 2027 Evaluation Cycle (UPCOMING)",
    }),
  );
  expect(screen.getByLabelText("Evaluation cycle").textContent).toBe(
    "Spring 2027 Evaluation Cycle (UPCOMING)",
  );
});

test("keeps the selected historical cycle when navigating to reports", async () => {
  const currentQueue = {
    cycleId: 1,
    cycleLabel: "Fall 2026",
    count: 0,
    faculty: [],
  };
  const pastQueue = {
    cycleId: 2,
    cycleLabel: "Spring 2026",
    count: 0,
    faculty: [],
  };
  const pastMatching = {
    ...ac,
    cycle: { ...ac.cycle, cycleId: 2, label: "Spring 2026" },
  };
  fakeBackend({
    "/api/ac/cycles": () =>
      Response.json([
        { cycleId: 1, term: "Fall", year: 2026, period: "CURRENT" },
        { cycleId: 2, term: "Spring", year: 2026, period: "PAST" },
      ]),
    "/api/observer-eligibility/ac": () => Response.json(ac),
    "/api/ac-dashboard/metrics": () => Response.json(metrics),
    "/api/due-evaluations": () => Response.json(currentQueue),
    "/api/overdue-evaluations": () => Response.json(currentQueue),
    "/api/observer-eligibility/ac/cycles/2": () => Response.json(pastMatching),
    "/api/ac-dashboard/cycles/2/metrics": () =>
      Response.json({ ...metrics, cycleId: 2, cycleLabel: "Spring 2026" }),
    "/api/ac-dashboard/cycles/2/observations": () =>
      Response.json({
        ...observationTracking,
        cycleId: 2,
        cycleLabel: "Spring 2026",
      }),
    "/api/evaluation-eligibility/cycles/2/due": () => Response.json(pastQueue),
    "/api/evaluation-eligibility/cycles/2/overdue": () =>
      Response.json(pastQueue),
    "/api/cycles/2/missing-observations": () =>
      Response.json({
        ...pastQueue,
        observationDeadline: "2026-05-01",
        signups: [],
      }),
  });
  signInAs("ac");
  window.history.pushState({}, "", "/ac");
  render(<App />);
  fireEvent.click(await screen.findByLabelText("Evaluation cycle"));
  fireEvent.click(
    screen.getByRole("button", { name: "Spring 2026 Evaluation Cycle" }),
  );
  await screen.findByText("This past cycle is read-only.");
  fireEvent.click(await screen.findByRole("link", { name: "Reports & KPIs" }));
  await screen.findByRole("heading", { name: "Spring 2026 Cycle" });
  expect(
    (screen.getByRole("button", { name: "Export Report" }) as HTMLButtonElement)
      .disabled,
  ).toBe(false);
});
