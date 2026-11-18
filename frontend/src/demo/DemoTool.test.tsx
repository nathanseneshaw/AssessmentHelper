import { afterEach, beforeEach, expect, test } from "bun:test";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
} from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { AuthProvider } from "../auth/AuthContext";
import {
  fakeBackend,
  resetBackend,
  sessionUser,
  signInAs,
} from "../test/fakeBackend";
import { DemoTool } from "./DemoTool";

const DEMO_ACCOUNTS = [
  { userId: 1, name: "Alice Alice", email: "alice@utdallas.edu" },
  { userId: 2, name: "Bob Bob", email: "bob@utdallas.edu" },
].map((account) => ({
  ...account,
  department: "Computer Science",
  committee: false,
}));

function Location() {
  return <p data-testid="path">{useLocation().pathname}</p>;
}

function renderTool() {
  render(
    <MemoryRouter initialEntries={["/ac"]}>
      <AuthProvider>
        <Routes>
          <Route
            path="*"
            element={
              <>
                <Location />
                <DemoTool />
              </>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => signInAs("ac"));

afterEach(() => {
  cleanup();
  resetBackend();
  localStorage.clear();
});

test("switches to another account and opens their home page", async () => {
  fakeBackend({
    "/api/demo/accounts": () => Response.json(DEMO_ACCOUNTS),
    "/api/demo/switch": () => {
      signInAs("professor");
      return Response.json(sessionUser());
    },
  });
  renderTool();

  fireEvent.click(await screen.findByRole("button", { name: "Demo" }));
  fireEvent.click(await screen.findByRole("button", { name: /Bob Bob/ }));

  expect(await screen.findByText("/faculty")).toBeDefined();
});

test("shows nothing when the backend has demo mode off", async () => {
  let asked = false;
  fakeBackend({
    "/api/demo/accounts": () => {
      asked = true;
      return Response.json({ detail: "Not found" }, { status: 404 });
    },
  });
  renderTool();

  await act(async () => {
    while (!asked) await new Promise((resolve) => setTimeout(resolve, 5));
  });

  expect(screen.queryByRole("button", { name: "Demo" })).toBeNull();
});
