import { afterEach, describe, expect, spyOn, test } from "bun:test";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { App } from "../../App";
import type {
  DashboardMetrics,
  ObservationStatus,
  ObservationTracking,
} from "../../api/acDashboard";
import type { OverdueQueue } from "../../api/evaluationEligibility";
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
  test("shows live KPIs, executive metrics and cycle summary", async () => {
    await renderLoadedReports();

    expect(screen.getByText("38%")).toBeDefined();
    expect(screen.getByText("5 of 13 due faculty signed up")).toBeDefined();
    expect(screen.getByText("50%")).toBeDefined();
    expect(screen.getByText("3 of 6 signups have an observer")).toBeDefined();
    expect(screen.getByText("17%")).toBeDefined();
    expect(
      screen.getByText("1 of 6 signups have a full list of 5"),
    ).toBeDefined();
    expect(screen.getByText("Not tracked yet")).toBeDefined();
    expect(screen.queryByText("Eligibility Accuracy")).toBeNull();
    expect(screen.queryByText("78%")).toBeNull();

    const terms = screen.getAllByRole("term").map((el) => el.textContent);
    const values = screen
      .getAllByRole("definition")
      .map((el) => el.textContent);
    expect(terms).toEqual([
      "Faculty due for evaluation",
      "Missing observation count",
      "Unmatched faculty report",
      "Overdue evaluation count",
    ]);
    expect(values).toEqual(["13", "1", "1", "8"]);

    expect(
      screen.getAllByRole("listitem").map((item) => item.textContent),
    ).toEqual([
      "13 faculty due",
      "5 signed up",
      "3 observations scheduled",
      "1 unmatched faculty",
      "1 completed",
      "1 missing observation",
    ]);
  });

  test("Export Report downloads the loaded figures as CSV", async () => {
    await renderLoadedReports();
    const file = await download("Export Report");

    expect(file.filename).toBe("reports-and-kpis.csv");
    expect(file.type).toBe("text/csv");
    const lines = file.text.trim().split("\n");
    expect(lines).toEqual([
      '"Section","Metric","Value","Note"',
      '"Cycle","Evaluation cycle","Fall 2026",""',
      '"KPI","Assessment Participation Rate","38%","5 of 13 due faculty signed up"',
      '"KPI","Observer Utilization Rate","50%","3 of 6 signups have an observer"',
      '"KPI","List Sufficiency","17%","1 of 6 signups have a full list of 5"',
      '"KPI","Survey Completion","—","Not tracked yet"',
      '"Executive dashboard","Faculty due for evaluation","13",""',
      '"Executive dashboard","Missing observation count","1",""',
      '"Executive dashboard","Unmatched faculty report","1",""',
      '"Executive dashboard","Overdue evaluation count","8",""',
    ]);
  });

  test("Download Summary downloads the cycle summary as text", async () => {
    await renderLoadedReports();
    const file = await download("Download Summary");

    expect(file.filename).toBe("fall-2026-cycle-summary.txt");
    expect(file.type).toBe("text/plain");
    expect(file.text).toBe(
      [
        "Fall 2026 Cycle",
        "",
        "13 faculty due",
        "5 signed up",
        "3 observations scheduled",
        "1 unmatched faculty",
        "1 completed",
        "1 missing observation",
        "",
      ].join("\n"),
    );
  });

  test("shows placeholders and blocks exports until everything loads", async () => {
    await renderReports(() => new Promise<Response>(() => {}));

    expect(screen.getAllByText("Loading…")).toHaveLength(3);
    expect(screen.getByText("Loading cycle summary…")).toBeDefined();
    expect(
      screen.getAllByRole("definition").map((el) => el.textContent),
    ).toEqual(["—", "—", "—", "—"]);
    expect(button("Export Report").disabled).toBe(true);
    expect(button("Download Summary").disabled).toBe(true);
  });

  test("a failed request shows Unavailable without an error banner", async () => {
    await renderReports(() =>
      Response.json({ detail: "Backend unavailable" }, { status: 503 }),
    );

    expect(await screen.findAllByText("Unavailable")).toHaveLength(3);
    expect(
      screen.getByText("The cycle summary could not be loaded."),
    ).toBeDefined();
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.queryByText(/Backend unavailable/)).toBeNull();
    expect(button("Export Report").disabled).toBe(true);
    expect(button("Download Summary").disabled).toBe(true);
  });
});
