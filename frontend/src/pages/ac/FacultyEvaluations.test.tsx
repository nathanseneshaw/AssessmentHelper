import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { App } from "../../App";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";

const faculty = [
  {
    professorId: 1,
    name: "Professor 1",
    rank: "ASSISTANT",
    status: "Due",
    nextDueDate: "2026-10-01",
  },
  {
    professorId: 4,
    name: "Professor 4",
    rank: "ASSOCIATE",
    status: "Overdue",
    nextDueDate: "2026-03-01",
    nextDueSemester: "Spring 2026",
  },
  {
    professorId: 2,
    name: "Professor 2",
    rank: "ASSOCIATE",
    status: "Upcoming",
    nextDueDate: "2027-03-01",
    nextDueSemester: "Spring 2027",
  },
  {
    professorId: 5,
    name: "Professor 5",
    rank: "FULL",
    status: "Due",
    nextDueDate: "2026-11-01",
  },
].map((person) => ({
  ...person,
  lastName: "Test",
  email: `p${person.professorId}@test.edu`,
  lastEvaluationCycle: "Spring 2025",
  intervalYears: 2,
  lastNotifiedAt: null,
}));

let eligibility: () => Response;
function queueOf(rows: typeof faculty) {
  return Response.json({
    cycleId: 1,
    cycleLabel: "Fall 2026",
    count: rows.length,
    faculty: rows,
  });
}

beforeEach(async () => {
  eligibility = () => queueOf(faculty);
  fakeBackend({ "/api/evaluation-eligibility": () => eligibility() });
  signInAs("ac");
  window.history.pushState({}, "", "/ac/evaluations");
  render(<App />);
  await screen.findByRole("heading", { level: 1, name: "Faculty Evaluations" });
  await screen.findByText("4 of 4 faculty shown");
});

afterEach(() => {
  cleanup();
  resetBackend();
});

// The first cell of each body row (the header row has column headers, not cells).
function facultyShown() {
  return within(screen.getByRole("table"))
    .getAllByRole("row")
    .slice(1)
    .map((row) => within(row).getAllByRole("cell")[0]?.textContent);
}

function filterButton(name: string) {
  const filters = screen.getByRole("group", { name: "Filters:" });
  return within(filters).getByRole("button", { name });
}

describe("evaluation queue", () => {
  test("previews four rows and opens all matching faculty in a dismissible overlay", async () => {
    cleanup();
    const extra = faculty[0];
    if (!extra) throw new Error("Missing faculty fixture");
    const allFaculty = [
      ...faculty,
      { ...extra, professorId: 6, name: "Professor 6" },
      { ...extra, professorId: 7, name: "Professor 7" },
    ];
    eligibility = () => queueOf(allFaculty);
    render(<App />);
    await screen.findByText("6 of 6 faculty shown");
    expect(facultyShown()).toHaveLength(4);
    const open = screen.getByRole("button", {
      name: "View all matching faculty",
    });
    fireEvent.click(open);
    const dialog = screen.getByRole("dialog", {
      name: "Evaluation queue - All Faculty",
    });
    expect(within(dialog).getAllByRole("row")).toHaveLength(7);
    expect(within(dialog).getByText("Professor 7")).toBeDefined();
    fireEvent.click(within(dialog).getByRole("button", { name: "Close" }));
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(facultyShown()).toHaveLength(4);
  });

  test("View All retains the overdue filter and supports dialog cancellation", () => {
    fireEvent.click(filterButton("Overdue"));
    fireEvent.click(
      screen.getByRole("button", { name: "View all matching faculty" }),
    );
    const dialog = screen.getByRole("dialog", {
      name: "Evaluation queue - Overdue",
    });
    expect(within(dialog).getByText("Professor 4")).toBeDefined();
    expect(within(dialog).getByText("Spring 2026")).toBeDefined();
    expect(within(dialog).queryByText("Professor 1")).toBeNull();
    fireEvent(
      dialog,
      new Event("cancel", { bubbles: false, cancelable: true }),
    );
    expect(screen.queryByRole("dialog")).toBeNull();
  });
  test("shows an API failure and successfully retries without mock rows", async () => {
    cleanup();
    let fail = true;
    eligibility = () =>
      fail
        ? Response.json(
            { detail: "Eligibility service unavailable" },
            { status: 503 },
          )
        : queueOf(faculty);
    render(<App />);
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Eligibility service unavailable",
    );
    expect(screen.queryByText("Professor 1")).toBeNull();
    fail = false;
    fireEvent.click(screen.getByRole("button", { name: "Retry" }));
    await screen.findByText("4 of 4 faculty shown");
    expect(facultyShown()).toHaveLength(4);
  });

  test("an empty backend result shows an explicit empty state", async () => {
    cleanup();
    eligibility = () => queueOf([]);
    render(<App />);
    await screen.findByText("No faculty match this filter.");
    expect(screen.getByText("0 of 0 faculty shown")).toBeDefined();
  });
  test("shows all faculty returned by the backend, including upcoming professors", () => {
    expect(screen.getByText("Spring 2027")).toBeDefined();
    expect(screen.queryByText("2027-03-01")).toBeNull();
    expect(facultyShown()).toEqual([
      "Professor 1",
      "Professor 4",
      "Professor 2",
      "Professor 5",
    ]);
    expect(filterButton("All Faculty").getAttribute("aria-pressed")).toBe(
      "true",
    );
  });

  test("each row's button names the professor", () => {
    expect(
      screen.getByRole("button", { name: "View Evaluation for Professor 1" }),
    ).toBeDefined();
    expect(
      screen.getByRole("button", {
        name: "View Past Evaluations for Professor 2",
      }),
    ).toBeDefined();
  });

  test.each([
    ["Due This Term", ["Professor 1", "Professor 5"]],
    ["Overdue", ["Professor 4"]],
    ["Assistant", ["Professor 1"]],
    ["Associate", ["Professor 4", "Professor 2"]],
    ["Full", ["Professor 5"]],
  ])("the %s filter shows %p", (name, expected) => {
    fireEvent.click(filterButton(name));
    expect(facultyShown()).toEqual(expected);
    expect(filterButton(name).getAttribute("aria-pressed")).toBe("true");
    expect(filterButton("All Faculty").getAttribute("aria-pressed")).toBe(
      "false",
    );
  });

  test("switching between overdue and all faculty preserves the loaded data", () => {
    fireEvent.click(filterButton("Overdue"));
    expect(facultyShown()).toEqual(["Professor 4"]);
    expect(screen.getByText("1 of 4 faculty shown")).toBeDefined();

    fireEvent.click(filterButton("All Faculty"));
    expect(facultyShown()).toHaveLength(4);
    expect(screen.queryByText("No faculty match this filter.")).toBeNull();
  });
});
