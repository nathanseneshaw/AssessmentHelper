import { afterEach, beforeEach, expect, type Mock, test } from "bun:test";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { App } from "../../App";
import type {
  ObserverRequest,
  RequestOptions,
} from "../../api/observerRequests";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";

let options: RequestOptions[];
let writes: { signupId: number; matchId: number }[];
let failLoad: boolean;
let failSend: boolean;
let delaySend: Promise<void> | undefined;
const request: ObserverRequest = {
  requestId: 50,
  signupId: 31,
  observerId: 1,
  requestDate: "2026-10-21T10:00:00",
  expirationDate: "2026-10-28T10:00:00",
  responseDate: null,
  status: "PENDING",
};

beforeEach(() => {
  options = [
    {
      signupId: 31,
      courseLabel: "CS 101.001",
      semesterLabel: "Fall 2026",
      signupStatus: "PENDING",
      unavailableReason: null,
      candidates: [
        {
          matchId: 2,
          observerId: 1,
          observerName: "Alice Alice",
          department: "Computer Science",
        },
      ],
      requests: [],
    },
  ];
  writes = [];
  failLoad = false;
  failSend = false;
  delaySend = undefined;
  fakeBackend({
    "/api/professors/4/observer-requests": async (init) => {
      if (init?.method === "POST") {
        writes.push(JSON.parse(String(init.body)));
        if (delaySend) await delaySend;
        if (failSend)
          return Response.json(
            { detail: "This match is no longer proposed." },
            { status: 409 },
          );
        options[0]?.requests.push({
          request: { ...request },
          observerName: "Alice Alice",
        });
        return Response.json(request, { status: 201 });
      }
      if (failLoad) throw new Error("Network unavailable");
      return Response.json(options);
    },
  });
});

afterEach(() => {
  cleanup();
  resetBackend();
});

function open(professorId: number | null = 4) {
  signInAs("professor", { professorId });
  window.history.pushState({}, "", "/faculty/observers");
  return render(<App />);
}

async function choose() {
  fireEvent.click(await screen.findByRole("radio", { name: /Alice Alice/ }));
}

test("sends the selected match and restores its pending status after reopening", async () => {
  const view = open();
  await screen.findByRole("radio", { name: /Alice Alice/ });
  expect(
    (
      screen.getByRole("button", {
        name: "Send evaluation request",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  await choose();
  fireEvent.click(
    screen.getByRole("button", { name: "Send evaluation request" }),
  );
  await screen.findByText("Request recorded for Alice Alice. Status: Pending.");
  expect(writes).toEqual([{ signupId: 31, matchId: 2 }]);
  expect(screen.getByText("2026-10-28 10:00")).toBeDefined();
  expect(
    screen.queryByRole("button", { name: "Send evaluation request" }),
  ).toBeNull();
  view.unmount();
  open();
  await screen.findByText("Pending");
  expect(screen.getByText(/Waiting for Alice Alice/)).toBeDefined();
  expect(writes).toHaveLength(1);
});

test("suppresses duplicate clicks while the request is in flight", async () => {
  let release: (() => void) | undefined;
  delaySend = new Promise<void>((resolve) => {
    release = resolve;
  });
  open();
  await choose();
  const send = screen.getByRole("button", { name: "Send evaluation request" });
  fireEvent.click(send);
  fireEvent.click(send);
  expect((screen.getByRole("combobox") as HTMLSelectElement).disabled).toBe(
    true,
  );
  // The request is sent after the CSRF token is fetched.
  await waitFor(() => expect(writes).toHaveLength(1));
  await act(async () => {
    release?.();
  });
  await screen.findByText("Pending");
  expect(writes).toHaveLength(1);
});

test("keeps the choice after a failed send and allows a retry", async () => {
  failSend = true;
  open();
  await choose();
  fireEvent.click(
    screen.getByRole("button", { name: "Send evaluation request" }),
  );
  await screen.findByRole("alert");
  expect(screen.queryByText("Pending")).toBeNull();
  expect(
    (screen.getByRole("radio", { name: /Alice Alice/ }) as HTMLInputElement)
      .checked,
  ).toBe(true);
  failSend = false;
  fireEvent.click(
    screen.getByRole("button", { name: "Send evaluation request" }),
  );
  await screen.findByText("Pending");
  expect(writes).toHaveLength(2);
});

test("retries loading and refreshes confirmed response details", async () => {
  failLoad = true;
  open();
  await screen.findByText("Network unavailable");
  failLoad = false;
  fireEvent.click(screen.getByRole("button", { name: "Retry loading" }));
  await choose();
  if (options[0])
    options[0].requests = [
      {
        observerName: "Alice Alice",
        request: {
          ...request,
          status: "CONFIRMED",
          responseDate: "2026-10-22T09:15:00",
        },
      },
    ];
  fireEvent.click(screen.getByRole("button", { name: "Refresh status" }));
  await screen.findByText("Confirmed");
  expect(screen.getByText("2026-10-22 09:15")).toBeDefined();
  expect(screen.queryByRole("radio")).toBeNull();
});

test("shows expired history while permitting another request", async () => {
  if (options[0])
    options[0].requests = [
      {
        observerName: "Alice Alice",
        request: { ...request, status: "EXPIRED" },
      },
    ];
  open();
  await screen.findByText("Expired");
  await choose();
  expect(
    (
      screen.getByRole("button", {
        name: "Send evaluation request",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(false);
});

test("resets the selected candidate when the signup changes and explains unavailable signups", async () => {
  if (!options[0]) throw new Error("Missing fixture");
  options.push({
    ...options[0],
    signupId: 32,
    semesterLabel: "Spring 2025",
    unavailableReason: "This evaluation cycle is closed.",
  });
  open();
  await choose();
  fireEvent.change(screen.getByRole("combobox"), { target: { value: "32" } });
  expect(screen.getByText("This evaluation cycle is closed.")).toBeDefined();
  expect(
    screen.queryByRole("button", { name: "Send evaluation request" }),
  ).toBeNull();
  fireEvent.change(screen.getByRole("combobox"), { target: { value: "31" } });
  expect(
    (screen.getByRole("radio", { name: /Alice Alice/ }) as HTMLInputElement)
      .checked,
  ).toBe(false);
});

test("explains when signups or proposed candidates are missing", async () => {
  if (options[0]) options[0].candidates = [];
  const view = open();
  await screen.findByText(/No proposed observers yet/);
  expect(
    (
      screen.getByRole("button", {
        name: "Send evaluation request",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  view.unmount();
  options = [];
  open();
  await screen.findByText(/No evaluation signups yet/);
  expect(
    screen
      .getByRole("link", { name: "Sign up for an evaluation" })
      .getAttribute("href"),
  ).toBe("/faculty/signup");
});

test("requires a faculty profile instead of guessing an identity", async () => {
  open(null);
  await screen.findByText(/Your session has no faculty profile/);
  // Only the sign-in check may reach the backend.
  const calls = (globalThis.fetch as unknown as Mock<typeof fetch>).mock.calls;
  expect(
    calls.filter(([input]) => !String(input).includes("/api/auth/")),
  ).toHaveLength(0);
});
