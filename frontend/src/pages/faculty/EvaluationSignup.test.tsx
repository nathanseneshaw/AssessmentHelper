import { afterEach, beforeEach, expect, test } from "bun:test";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { App } from "../../App";
import type { EvaluationSignup } from "../../api/courseSelection";
import { dashboard } from "../../test/facultyDashboardFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { faculty } from "../../test/observerFixtures";

let saved: EvaluationSignup | undefined;
let savedEnd: string | null;
let saveFailure: boolean;
let signupOpen: boolean;

function signupRoute(init?: RequestInit) {
  if (init?.method === "PUT") {
    if (saveFailure)
      return Response.json(
        { detail: "You can only select a section you teach." },
        { status: 400 },
      );
    const body = JSON.parse(String(init.body));
    saved = {
      signupId: 31,
      observeeId: 31,
      sectionId: body.sectionId,
      signupDate: "2026-10-21",
      status: "PENDING",
    };
    savedEnd = body.windowEnd;
  }
  return saved ? Response.json(saved) : new Response(null, { status: 204 });
}

beforeEach(() => {
  saved = undefined;
  savedEnd = null;
  saveFailure = false;
  signupOpen = true;
  fakeBackend({
    // The faculty dashboard behind the dialog loads its own status and observations.
    "/api/observer-eligibility/faculty/3": () => Response.json(faculty),
    "/api/professors/3/observations": () => Response.json([]),
    "/api/professors/3/dashboard": () => Response.json(dashboard),
    "/api/semesters": () =>
      Response.json([{ semesterId: 1, term: "Fall", academicYear: 2026 }]),
    "/api/courses": () =>
      Response.json([
        {
          courseId: 1,
          courseCode: "CS",
          courseNumber: "101",
          courseName: "Intro to Programming",
        },
      ]),
    "/api/sections": () =>
      Response.json([
        {
          sectionId: 9,
          courseId: 1,
          semesterId: 1,
          sectionNumber: "002",
          meetingDays: "TR",
          startTime: "09:00:00",
          endTime: "10:15:00",
          location: "Science Hall 206",
        },
      ]),
    "/api/professors/3/evaluation-signups/1": signupRoute,
    "/api/professors/3/evaluation-signups/1/window": () =>
      Response.json({
        start: "2026-11-01",
        latestEnd: "2026-11-15",
        end: savedEnd,
        signupDeadline: "2026-10-31",
        signupOpen,
      }),
  });
  signInAs("professor", { professorId: 3 });
});

afterEach(() => {
  cleanup();
  resetBackend();
});

async function openSignup() {
  window.history.pushState({}, "", "/faculty/signup");
  render(<App />);
  await screen.findByRole("option", { name: "Fall 2026" });
}

function chooseSemester() {
  fireEvent.change(screen.getByLabelText("Semester"), {
    target: { value: "1" },
  });
}

async function chooseCourse() {
  chooseSemester();
  await screen.findByRole("option", { name: "CS 101 — Intro to Programming" });
  fireEvent.change(screen.getByLabelText("Course"), { target: { value: "1" } });
  fireEvent.change(screen.getByLabelText("Section"), {
    target: { value: "9" },
  });
}

function saveButton() {
  return screen.getByRole("button", {
    name: "Save selection",
  }) as HTMLButtonElement;
}

const SAVED = "Your course and section have been saved for evaluation.";

test("selects a taught section and observation window, saves them, and restores them after reopening", async () => {
  await openSignup();
  await chooseCourse();
  expect(screen.getByText("Science Hall 206")).toBeDefined();
  const through = screen.getByLabelText("Through") as HTMLInputElement;
  expect(through.value).toBe("2026-11-15");
  fireEvent.change(through, { target: { value: "2026-11-10" } });
  fireEvent.click(saveButton());
  await screen.findByText(SAVED);
  expect(saved?.sectionId).toBe(9);
  expect(savedEnd).toBe("2026-11-10");

  cleanup();
  await openSignup();
  chooseSemester();
  await screen.findByText("Your saved course selection has been loaded.");
  expect((screen.getByLabelText("Section") as HTMLSelectElement).value).toBe(
    "9",
  );
});

test("shows save failures and keeps the selection available for retry", async () => {
  saveFailure = true;
  await openSignup();
  await chooseCourse();
  fireEvent.click(saveButton());
  expect((await screen.findByRole("alert")).textContent).toContain(
    "You can only select a section you teach.",
  );
  expect((screen.getByLabelText("Section") as HTMLSelectElement).value).toBe(
    "9",
  );

  saveFailure = false;
  fireEvent.click(saveButton());
  await screen.findByText(SAVED);
});

test("closed signups explain the deadline and cannot be saved", async () => {
  signupOpen = false;
  await openSignup();
  await chooseCourse();
  await screen.findByText("Signup closed on 2026-10-31.");
  expect(saveButton().disabled).toBe(true);
});

test("keeps matched signups read-only", async () => {
  saved = {
    signupId: 1,
    observeeId: 1,
    sectionId: 9,
    signupDate: "2026-09-01",
    status: "MATCHED",
  };
  await openSignup();
  chooseSemester();
  await screen.findByText(/This signup is matched/);
  expect((screen.getByLabelText("Course") as HTMLSelectElement).disabled).toBe(
    true,
  );
  expect(saveButton().disabled).toBe(true);
});
