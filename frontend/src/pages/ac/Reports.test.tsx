import { afterEach, describe, expect, spyOn, test } from "bun:test";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { App } from "../../App";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";

async function renderReports() {
  fakeBackend();
  signInAs("ac");
  window.history.pushState({}, "", "/ac/reports");
  render(<App />);
  await screen.findByRole("heading", { level: 1, name: "Reports & KPIs" });
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

afterEach(() => {
  cleanup();
  resetBackend();
});

describe("Reports & KPIs", () => {
  test("shows the KPIs, executive metrics and cycle summary", async () => {
    await renderReports();

    expect(screen.getByText("Eligibility Accuracy")).toBeDefined();
    expect(screen.getByText("96%")).toBeDefined();
    expect(screen.getByText("78%")).toBeDefined();

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
    expect(values).toEqual(["18", "4", "2", "8"]);

    expect(
      screen.getByRole("heading", { level: 2, name: "Fall 2026 Cycle" }),
    ).toBeDefined();
    expect(screen.getByText("13 observations scheduled").tagName).toBe("LI");
  });

  test("Export Report downloads the KPIs and metrics as CSV", async () => {
    await renderReports();
    const file = await download("Export Report");

    expect(file.filename).toBe("reports-and-kpis.csv");
    expect(file.type).toBe("text/csv");
    const lines = file.text.trim().split("\n");
    expect(lines[0]).toBe('"Section","Metric","Value","Note"');
    expect(lines).toContain(
      '"KPI","Eligibility Accuracy","96%","Faculty evaluation KPI"',
    );
    expect(lines).toContain(
      '"Executive dashboard","Overdue evaluation count","8",""',
    );
    expect(lines).toHaveLength(10);
  });

  test("Download Summary downloads the cycle summary as text", async () => {
    await renderReports();
    const file = await download("Download Summary");

    expect(file.filename).toBe("fall-2026-cycle-summary.txt");
    expect(file.type).toBe("text/plain");
    expect(file.text).toBe(
      [
        "Fall 2026 Cycle",
        "",
        "18 faculty due",
        "15 signed up",
        "13 observations scheduled",
        "2 unmatched faculty",
        "9 completed",
        "4 missing observations",
        "",
      ].join("\n"),
    );
  });
});
