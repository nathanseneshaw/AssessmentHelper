import { afterEach, beforeEach, describe, expect, mock, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { App } from "./App";
import { STORAGE_KEY } from "./auth/AuthContext";
import { NAV_ITEMS, type Role } from "./navConfig";

const originalFetch = globalThis.fetch;
beforeEach(() => {
  globalThis.fetch = mock(async () =>
    Response.json({
      cycleId: 1,
      cycleLabel: "Fall 2026",
      count: 0,
      faculty: [],
    }),
  ) as unknown as typeof fetch;
});
afterEach(() => {
  globalThis.fetch = originalFetch;
});

const AC_PAGES = [
  ["/ac", "Assessment Committee Dashboard"],
  ["/ac/evaluations", "Faculty Evaluations"],
  ["/ac/signups", "Evaluation Signups"],
  ["/ac/matching", "Observer Matching"],
  ["/ac/observations", "Observation Tracking"],
  ["/ac/form-template", "Observation Form Template"],
  ["/ac/reports", "Reports & KPIs"],
] as const;

// Faculty pages open as a dialog on top of the faculty dashboard.
const FACULTY_DIALOGS = [
  ["/faculty/signup", "Sign Up for Evaluation"],
  ["/faculty/observers", "Choose Observers"],
  ["/faculty/history", "Evaluation History"],
  ["/faculty/observations/me", "Observation Records for Me"],
  ["/faculty/observations/others", "Observation Records for Others"],
  ["/faculty/observations/42", "Observation Details"],
  ["/faculty/feedback", "Process Feedback Survey"],
] as const;

const PROTECTED_PATHS = [
  ...AC_PAGES.map(([path]) => path),
  "/faculty",
  ...FACULTY_DIALOGS.map(([path]) => path),
];

// mockLogin waits 600ms before answering.
const SIGN_IN_WAIT = { timeout: 3000 };

function renderAt(path: string, role?: Role) {
  if (role) {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ role, name: "Test User" }),
    );
  }
  window.history.pushState({}, "", path);
  render(<App />);
}

function currentPath() {
  return window.location.pathname;
}

function submitLogin(email: string, password: string) {
  fireEvent.change(screen.getByLabelText("Email"), {
    target: { value: email },
  });
  fireEvent.change(screen.getByLabelText("Password"), {
    target: { value: password },
  });
  fireEvent.click(screen.getByRole("button", { name: "Sign In" }));
}

afterEach(() => {
  cleanup();
  localStorage.clear();
});

describe("signed out", () => {
  test("/ redirects to the login page", async () => {
    renderAt("/");
    await screen.findByRole("button", { name: "Sign In" });
    expect(currentPath()).toBe("/login");
  });

  test.each(PROTECTED_PATHS)("%s redirects to the login page", async (path) => {
    renderAt(path);
    await screen.findByRole("button", { name: "Sign In" });
    expect(currentPath()).toBe("/login");
  });

  test("unknown URLs show the not found page", async () => {
    renderAt("/no-such-page");
    await screen.findByRole("heading", { name: "Page not found" });
  });
});

describe("signing in", () => {
  test("faculty land on the faculty dashboard", async () => {
    renderAt("/login");
    submitLogin("faculty@utdallas.edu", "Faculty123!");
    await screen.findByRole(
      "heading",
      { name: "My Evaluation Dashboard" },
      SIGN_IN_WAIT,
    );
    expect(currentPath()).toBe("/faculty");
    expect(screen.getByText("Demo Faculty")).toBeDefined();
  });

  test("assessment committee members land on the AC overview", async () => {
    renderAt("/login");
    submitLogin("ac@utdallas.edu", "Committee123!");
    await screen.findByRole(
      "heading",
      { name: "Assessment Committee Dashboard" },
      SIGN_IN_WAIT,
    );
    expect(currentPath()).toBe("/ac");
    expect(screen.getByText("Demo AC Member")).toBeDefined();
  });

  test("a wrong password stays on the login page", async () => {
    renderAt("/login");
    submitLogin("faculty@utdallas.edu", "wrong");
    await screen.findByText("Incorrect email or password.", {}, SIGN_IN_WAIT);
    expect(currentPath()).toBe("/login");
  });

  test("signed-in users who open /login are sent to their home page", async () => {
    renderAt("/login", "ac");
    await screen.findByRole("heading", {
      name: "Assessment Committee Dashboard",
    });
    expect(currentPath()).toBe("/ac");
  });
});

describe("assessment committee", () => {
  test.each(AC_PAGES)("%s shows %s", async (path, heading) => {
    renderAt(path, "ac");
    await screen.findByRole("heading", { level: 1, name: heading });
    expect(currentPath()).toBe(path);
  });

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

  test.each([
    ["View all faculty due for evaluation", "/ac/evaluations"],
    ["Resolve Matches", "/ac/matching"],
  ])("the overview's %s link opens %s", async (name, path) => {
    renderAt("/ac", "ac");
    fireEvent.click(await screen.findByRole("link", { name }));
    expect(currentPath()).toBe(path);
  });

  test("faculty pages are off limits", async () => {
    renderAt("/faculty", "ac");
    await screen.findByRole("heading", { name: "No access" });
    expect(currentPath()).toBe("/forbidden");
  });

  test("unknown AC pages show the not found page", async () => {
    renderAt("/ac/no-such-page", "ac");
    await screen.findByRole("heading", { name: "Page not found" });
  });

  test("logging out returns to the login page", async () => {
    renderAt("/ac/reports", "ac");
    fireEvent.click(await screen.findByRole("button", { name: "Log out" }));
    await screen.findByRole("button", { name: "Sign In" });
    expect(currentPath()).toBe("/login");
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });
});

describe("faculty", () => {
  test("/faculty shows the dashboard with no dialog open", async () => {
    renderAt("/faculty", "professor");
    await screen.findByRole("heading", {
      level: 1,
      name: "My Evaluation Dashboard",
    });
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  test.each(FACULTY_DIALOGS)(
    "%s opens the %s dialog over the dashboard",
    async (path, title) => {
      renderAt(path, "professor");
      await screen.findByRole("dialog", { name: title });
      expect(
        screen.getByRole("heading", {
          level: 1,
          name: "My Evaluation Dashboard",
        }),
      ).toBeDefined();
      expect(currentPath()).toBe(path);
    },
  );

  test("every link on the dashboard opens a dialog", async () => {
    renderAt("/faculty", "professor");
    await screen.findByRole("heading", {
      level: 1,
      name: "My Evaluation Dashboard",
    });
    const targets = new Set(
      screen
        .getAllByRole("link")
        .map((link) => link.getAttribute("href") ?? "")
        .filter((href) => href.startsWith("/faculty/")),
    );
    expect(targets.size).toBeGreaterThan(0);

    for (const href of targets) {
      cleanup();
      renderAt(href, "professor");
      await screen.findByRole("dialog").catch(() => {
        throw new Error(`${href} did not open a dialog`);
      });
    }
  });

  test("dialogs open from the dashboard and close with Close or Escape", async () => {
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

  test("unknown faculty pages show the not found page", async () => {
    renderAt("/faculty/no-such-page", "professor");
    await screen.findByRole("heading", { name: "Page not found" });
  });
});
