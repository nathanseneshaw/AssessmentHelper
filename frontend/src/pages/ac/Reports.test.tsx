import { afterEach, describe, expect, spyOn, test } from "bun:test";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from "@testing-library/react";
import { App } from "../../App";
import type {
  DashboardMetrics,
  ObservationStatus,
  ObservationTracking,
} from "../../api/acDashboard";
import type { OverdueQueue } from "../../api/evaluationEligibility";
import { observerChanged } from "../../api/observerEligibility";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { ac } from "../../test/observerFixtures";

const metrics: DashboardMetrics = {
  cycleId: 1,
  cycleLabel: "Fall 2026",
  facultyDue: 13,
  listSize: 5,
  participation: { count: 5, total: 13 },
  observerUtilization: { count: 3, total: 6 },
  listSufficiency: { count: 1, total: 6 },
  missingObservations: 1,
  surveyCompletion: { count: 2, total: 6 },
  surveyStatus: "CLOSED",
};

const overdue: OverdueQueue = {
  cycleId: 1,
  cycleLabel: "Fall 2026",
  count: 8,
  faculty: [],
};

const observation = (signupId: number, status: ObservationStatus) => ({
  signupId,
  observeeId: signupId,
  observee: `Professor ${signupId}`,
  observerId: status === "AWAITING_OBSERVER" ? null : signupId + 10,
  observer: status === "AWAITING_OBSERVER" ? null : `Observer ${signupId}`,
  course: "CS 3345.001",
  listReleased: true,
  date: null,
  startTime: null,
  status,
  observeeSigned: false,
  observerSigned: false,
  completedAt: null,
});

// Three observations have an observer: one upcoming, one completed and one missed.
const tracking: ObservationTracking = {
  cycleId: 1,
  cycleLabel: "Fall 2026",
  observationDeadline: "2026-11-15",
  observations: [
    observation(1, "SCHEDULED"),
    observation(2, "COMPLETED"),
    observation(3, "MISSING"),
    observation(4, "AWAITING_OBSERVER"),
  ],
};

async function renderReports(
  metricsRoute: () => Response | Promise<Response> = () =>
    Response.json(metrics),
) {
  fakeBackend({
    "/api/ac-dashboard/metrics": metricsRoute,
    "/api/overdue-evaluations": () => Response.json(overdue),
    // One signup has a full candidate list and the other has none, so one faculty member is unmatched.
    "/api/observer-eligibility/ac": () => Response.json(ac),
    "/api/ac-dashboard/observations": () => Response.json(tracking),
  });
  signInAs("ac");
  window.history.pushState({}, "", "/ac/reports");
  render(<App />);
  await screen.findByRole("heading", { level: 1, name: "Reports & KPIs" });
}

async function renderLoadedReports() {
  await renderReports();
  await screen.findByRole("heading", { level: 2, name: "Fall 2026 Cycle" });
}

/** Clicks a button and returns the file the page handed to the browser. */
async function download(buttonName: string) {
  let blob: Blob | undefined;
  let filename = "";
  const create = spyOn(URL, "createObjectURL").mockImplementation((file) => {
    blob = file as Blob;
    return "blob:report";
  });
  const revoke = spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
  // Stop happy-dom from following the link; just note the file name.
  const click = spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(
    function (this: HTMLAnchorElement) {
      filename = this.download;
    },
  );

  try {
    fireEvent.click(screen.getByRole("button", { name: buttonName }));
    expect(revoke).toHaveBeenCalledWith("blob:report");
    return { filename, type: blob?.type, text: (await blob?.text()) ?? "" };
  } finally {
    create.mockRestore();
    revoke.mockRestore();
    click.mockRestore();
  }
}

function button(name: string) {
  return screen.getByRole("button", { name }) as HTMLButtonElement;
}

afterEach(() => {
  cleanup();
  resetBackend();
});

describe("Reports & KPIs", () => {
  test("shows live KPIs, executive metrics and the cycle summary when the data loads", async () => {
    await renderLoadedReports();

    expect(screen.getByText("38%")).toBeDefined();
    expect(screen.getByText("5 of 13 due faculty signed up")).toBeDefined();
    expect(screen.getByText("50%")).toBeDefined();
    expect(screen.getByText("17%")).toBeDefined();
    expect(
      screen.getAllByRole("definition").map((el) => el.textContent),
    ).toEqual(["13", "1", "1", "8"]);
    expect(screen.getByText("3 observations scheduled")).toBeDefined();
    expect(
      screen.getByText("2 of 6 observees submitted feedback"),
    ).toBeDefined();
  });

  test("the report refreshes after a change is announced", async () => {
    let current = metrics;
    await renderReports(() => Response.json(current));
    await screen.findByText("2 of 6 observees submitted feedback");

    current = { ...metrics, surveyCompletion: { count: 3, total: 6 } };
    act(() => observerChanged());
    await screen.findByText("3 of 6 observees submitted feedback");
    const file = await download("Export Report");
    expect(file.text).toContain(
      '"KPI","Survey Completion","50%","3 of 6 observees submitted feedback"',
    );
  });

  test("downloads the figures as CSV when Export Report is clicked", async () => {
    await renderLoadedReports();
    const file = await download("Export Report");

    expect(file.filename).toBe("reports-and-kpis.csv");
    expect(file.type).toBe("text/csv");
    const lines = file.text.trim().split("\n");
    expect(lines[0]).toBe('"Section","Metric","Value","Note"');
    expect(lines).toContain(
      '"KPI","Assessment Participation Rate","38%","5 of 13 due faculty signed up"',
    );
    expect(lines).toContain(
      '"Executive dashboard","Overdue evaluation count","8",""',
    );
  });

  test("downloads the cycle summary as text when Download Summary is clicked", async () => {
    await renderLoadedReports();
    const file = await download("Download Summary");

    expect(file.filename).toBe("fall-2026-cycle-summary.txt");
    expect(file.type).toBe("text/plain");
    expect(file.text).toStartWith("Fall 2026 Cycle\n");
    expect(file.text).toContain("3 observations scheduled");
  });

  test("shows Unavailable and disables exports when the request fails", async () => {
    await renderReports(() =>
      Response.json({ detail: "Backend unavailable" }, { status: 503 }),
    );

    expect(await screen.findAllByText("Unavailable")).toHaveLength(4);
    expect(button("Export Report").disabled).toBe(true);
    expect(button("Download Summary").disabled).toBe(true);
  });
});
