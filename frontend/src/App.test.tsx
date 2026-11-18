import { afterEach, beforeEach, describe, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { App } from "./App";
import { NAV_ITEMS, type Role } from "./navConfig";
import { dashboard } from "./test/facultyDashboardFixtures";
import {
  fakeBackend,
  resetBackend,
  sessionUser,
  signInAs,
} from "./test/fakeBackend";
import { ac, faculty, signup } from "./test/observerFixtures";

const EMPTY_QUEUE = () =>
  Response.json({ cycleId: 1, cycleLabel: "Fall 2026", count: 0, faculty: [] });

function renderAt(path: string, role?: Role) {
  signInAs(role ?? null);
  window.history.pushState({}, "", path);
  render(<App />);
}

function currentPath() {
  return window.location.pathname;
}

async function submitLogin(email: string, password: string) {
  fireEvent.change(await screen.findByLabelText("Email"), {
    target: { value: email },
  });
  fireEvent.change(screen.getByLabelText("Password"), {
    target: { value: password },
  });
  fireEvent.click(screen.getByRole("button", { name: "Sign In" }));
}

beforeEach(() => {
  fakeBackend({
    "/api/due-evaluations": EMPTY_QUEUE,
    "/api/overdue-evaluations": EMPTY_QUEUE,
    "/api/observer-eligibility/ac": () => Response.json(ac),
    "/api/observer-eligibility/faculty/2": () => Response.json(faculty),
    "/api/professors/2/observations": () => Response.json([]),
    "/api/professors/2/dashboard": () => Response.json(dashboard),
  });
});

afterEach(() => {
  cleanup();
  resetBackend();
});

describe("signed out", () => {
  test.each(["/ac", "/faculty/signup"])(
    "%s redirects to the login page",
    async (path) => {
      renderAt(path);
      await screen.findByRole("button", { name: "Sign In" });
      expect(currentPath()).toBe("/login");
    },
  );

  test("unknown URLs show the not found page", async () => {
    renderAt("/no-such-page");
    await screen.findByRole("heading", { name: "Page not found" });
  });
});

describe("signing in", () => {
  test("faculty land on the faculty dashboard", async () => {
    renderAt("/login");
    await submitLogin("bob@utdallas.edu", "Faculty123!");
    await screen.findByRole("heading", { name: "My Evaluation Dashboard" });
    expect(currentPath()).toBe("/faculty");
  });

  test("assessment committee members land on the AC overview", async () => {
    renderAt("/login");
    await submitLogin("alice@utdallas.edu", "Committee123!");
    await screen.findByRole("heading", {
      name: "Assessment Committee Dashboard",
    });
    expect(currentPath()).toBe("/ac");
  });
});

describe("assessment committee", () => {
  test("every nav tab opens a page", async () => {
    renderAt("/ac", "ac");
    await screen.findByRole("navigation", { name: "Main" });

    for (const item of NAV_ITEMS.ac) {
      // Each page renders its own tabs, so look them up again after every click.
      const nav = screen.getByRole("navigation", { name: "Main" });
      fireEvent.click(within(nav).getByRole("link", { name: item.label }));
      expect(currentPath()).toBe(item.to);
      expect(screen.queryByText("Page not found")).toBeNull();
      expect(screen.getByRole("heading", { level: 1 })).toBeDefined();
    }
  });

  test("faculty pages are off limits", async () => {
    renderAt("/faculty", "ac");
    await screen.findByRole("heading", { name: "No access" });
    expect(currentPath()).toBe("/forbidden");
  });

  test("logging out returns to the login page", async () => {
    renderAt("/ac/reports", "ac");
    fireEvent.click(await screen.findByRole("button", { name: "Log out" }));
    await screen.findByRole("button", { name: "Sign In" });
    expect(currentPath()).toBe("/login");
    expect(sessionUser()).toBeNull();
  });
});

describe("faculty", () => {
  test("dialogs open from the dashboard and close with Close or Escape", async () => {
    fakeBackend({
      "/api/observer-eligibility/faculty/2": () =>
        Response.json({ ...faculty, signup: { ...signup, released: true } }),
    });
    renderAt("/faculty", "professor");

    fireEvent.click(
      await screen.findByRole("link", { name: "Sign Up for Evaluation" }),
    );
    const dialog = await screen.findByRole("dialog", {
      name: "Sign Up for Evaluation",
    });
    expect(currentPath()).toBe("/faculty/signup");
    fireEvent.click(within(dialog).getByRole("link", { name: "Close" }));
    expect(currentPath()).toBe("/faculty");
    expect(screen.queryByRole("dialog")).toBeNull();

    fireEvent.click(
      screen.getByRole("link", { name: "View Generated Observers" }),
    );
    await screen.findByRole("dialog", { name: "Choose Observers" });
    fireEvent.keyDown(window, { key: "Escape" });
    expect(currentPath()).toBe("/faculty");
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  test("AC pages are off limits", async () => {
    renderAt("/ac", "professor");
    await screen.findByRole("heading", { name: "No access" });
    expect(currentPath()).toBe("/forbidden");
  });
});
