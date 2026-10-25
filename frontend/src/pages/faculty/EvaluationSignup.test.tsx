import { afterEach, beforeEach, expect, type Mock, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { App } from "../../App";
import type { EvaluationSignup } from "../../api/courseSelection";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";

let saved: EvaluationSignup | undefined;
let saveFailure = false;
let catalogFailure = false;
let emptySections = false;
let heldSections: Promise<Response> | undefined;
const courses = [
  {
    courseId: 1,
    courseCode: "CS",
    courseNumber: "101",
    courseName: "Intro to Programming",
  },
  {
    courseId: 2,
    courseCode: "CS",
    courseNumber: "3345",
    courseName: "Data Structures",
  },
];
const sections = [
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
  {
    sectionId: 11,
    courseId: 2,
    semesterId: 1,
    sectionNumber: "001",
    meetingDays: "MWF",
    startTime: null,
    endTime: null,
    location: null,
  },
];

function signupRoute(init?: RequestInit) {
  if (init?.method === "PUT") {
    if (saveFailure)
      return Response.json(
        { detail: "You can only select a section you teach." },
        { status: 400 },
      );
    saved = {
      signupId: 31,
      observeeId: 31,
      sectionId: JSON.parse(String(init.body)).sectionId,
      signupDate: "2026-10-21",
      status: "PENDING",
    };
  }
  return saved ? Response.json(saved) : new Response(null, { status: 204 });
}

beforeEach(() => {
  saved = undefined;
  saveFailure = false;
  catalogFailure = false;
  emptySections = false;
  heldSections = undefined;
  fakeBackend({
    "/api/semesters": () => {
      if (catalogFailure) throw new Error("Network unavailable");
      return Response.json([
        { semesterId: 1, term: "Fall", academicYear: 2026 },
        { semesterId: 10, term: "Spring", academicYear: 2028 },
      ]);
    },
    "/api/courses": () => Response.json(courses),
    "/api/sections": (_init, url) => {
      // A held response stands in for a slow reply for the first semester.
      if (heldSections)
        return url.searchParams.get("semesterId") === "1"
          ? heldSections
          : Response.json([]);
      return Response.json(emptySections ? [] : sections);
    },
    "/api/professors/3/evaluation-signups/1": signupRoute,
    "/api/professors/3/evaluation-signups/10": signupRoute,
  });
});

afterEach(() => {
  cleanup();
  resetBackend();
});

// Requests the page made, leaving out the sign-in checks.
function requests() {
  return (globalThis.fetch as unknown as Mock<typeof fetch>).mock.calls
    .map(([input, init]) => {
      const url = new URL(String(input));
      return { path: url.pathname + url.search, init };
    })
    .filter((request) => !request.path.startsWith("/api/auth/"));
}

async function openSignup(professorId: number | null = 3) {
  signInAs("professor", { professorId });
  window.history.pushState({}, "", "/faculty/signup");
  render(<App />);
  await screen.findByRole("option", { name: "Fall 2026" });
}

async function chooseCourse() {
  fireEvent.change(screen.getByLabelText("Semester"), {
    target: { value: "1" },
  });
  await screen.findByRole("option", { name: "CS 101 — Intro to Programming" });
  fireEvent.change(screen.getByLabelText("Course"), { target: { value: "1" } });
  fireEvent.change(screen.getByLabelText("Section"), {
    target: { value: "9" },
  });
}

test("selects a taught section, saves its id, and restores it after reopening", async () => {
  await openSignup();
  expect(
    (
      screen.getByRole("button", {
        name: "Save selection",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  await chooseCourse();
  expect(screen.getByText("Science Hall 206")).toBeDefined();
  expect(screen.getByText("09:00 – 10:15")).toBeDefined();
  fireEvent.click(screen.getByRole("button", { name: "Save selection" }));
  await screen.findByText(
    "Your course and section have been saved for evaluation.",
  );
  const writes = requests().filter((request) => request.init?.method === "PUT");
  expect(writes).toHaveLength(1);
  expect(writes[0]?.path).toBe("/api/professors/3/evaluation-signups/1");
  expect(JSON.parse(String(writes[0]?.init?.body))).toEqual({ sectionId: 9 });
  expect(
    requests().some(
      (request) => request.path === "/api/sections?professorId=3&semesterId=1",
    ),
  ).toBe(true);
  cleanup();
  await openSignup();
  fireEvent.change(screen.getByLabelText("Semester"), {
    target: { value: "1" },
  });
  await screen.findByText("Your saved course selection has been loaded.");
  expect((screen.getByLabelText("Section") as HTMLSelectElement).value).toBe(
    "9",
  );
});

test("course and semester changes clear the dependent selection", async () => {
  await openSignup();
  await chooseCourse();
  fireEvent.change(screen.getByLabelText("Course"), { target: { value: "2" } });
  expect((screen.getByLabelText("Section") as HTMLSelectElement).value).toBe(
    "",
  );
  expect(screen.queryByText("Science Hall 206")).toBeNull();
  expect(
    (
      screen.getByRole("button", {
        name: "Save selection",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  fireEvent.change(screen.getByLabelText("Section"), {
    target: { value: "11" },
  });
  fireEvent.change(screen.getByLabelText("Semester"), {
    target: { value: "" },
  });
  expect((screen.getByLabelText("Course") as HTMLSelectElement).value).toBe("");
  expect((screen.getByLabelText("Section") as HTMLSelectElement).value).toBe(
    "",
  );
});

test("shows save failures and keeps the selection available for retry", async () => {
  saveFailure = true;
  await openSignup();
  await chooseCourse();
  fireEvent.click(screen.getByRole("button", { name: "Save selection" }));
  await screen.findByRole("alert");
  expect(
    screen.getByText("You can only select a section you teach."),
  ).toBeDefined();
  expect((screen.getByLabelText("Section") as HTMLSelectElement).value).toBe(
    "9",
  );
  expect(
    screen.queryByText(
      "Your course and section have been saved for evaluation.",
    ),
  ).toBeNull();
  saveFailure = false;
  fireEvent.click(screen.getByRole("button", { name: "Save selection" }));
  await screen.findByText(
    "Your course and section have been saved for evaluation.",
  );
});

test("explains empty teaching sections and disables saving", async () => {
  emptySections = true;
  await openSignup();
  fireEvent.change(screen.getByLabelText("Semester"), {
    target: { value: "1" },
  });
  await screen.findByText("You have no teaching sections in this semester.");
  expect((screen.getByLabelText("Course") as HTMLSelectElement).disabled).toBe(
    true,
  );
  expect(
    (
      screen.getByRole("button", {
        name: "Save selection",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
});

test("retries failed catalog loading", async () => {
  catalogFailure = true;
  signInAs("professor", { professorId: 3 });
  window.history.pushState({}, "", "/faculty/signup");
  render(<App />);
  await screen.findByRole("alert");
  catalogFailure = false;
  fireEvent.click(screen.getByRole("button", { name: "Retry" }));
  await screen.findByRole("option", { name: "Fall 2026" });
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
  fireEvent.change(screen.getByLabelText("Semester"), {
    target: { value: "1" },
  });
  await screen.findByText(/This signup is matched/);
  expect((screen.getByLabelText("Course") as HTMLSelectElement).disabled).toBe(
    true,
  );
  expect(
    (
      screen.getByRole("button", {
        name: "Save selection",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
});

test("sessions without a faculty profile require sign-in again instead of inventing a professor id", async () => {
  signInAs("professor", { professorId: null });
  window.history.pushState({}, "", "/faculty/signup");
  render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: "Sign in again" }));
  await waitFor(() => expect(window.location.pathname).toBe("/login"));
  expect(requests()).toHaveLength(0);
});

test("a late response for the previous semester cannot replace the current options", async () => {
  let finishOldRequest: (response: Response) => void = () => {};
  const oldResponse = new Promise<Response>((resolve) => {
    finishOldRequest = resolve;
  });
  heldSections = oldResponse;
  await openSignup();
  fireEvent.change(screen.getByLabelText("Semester"), {
    target: { value: "1" },
  });
  await screen.findByText("Loading your sections…");
  fireEvent.change(screen.getByLabelText("Semester"), {
    target: { value: "10" },
  });
  await screen.findByText("You have no teaching sections in this semester.");
  finishOldRequest(Response.json(sections));
  await oldResponse;
  await waitFor(() => {
    expect((screen.getByLabelText("Semester") as HTMLSelectElement).value).toBe(
      "10",
    );
    expect(
      (screen.getByLabelText("Course") as HTMLSelectElement).disabled,
    ).toBe(true);
    expect(
      screen.queryByRole("option", { name: "CS 101 — Intro to Programming" }),
    ).toBeNull();
  });
});
