import { afterEach, beforeEach, expect, test } from "bun:test";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { App } from "../../App";
import type {
  ObserverRequest,
  RequestOptions,
} from "../../api/observerRequests";
import { dashboard } from "../../test/facultyDashboardFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { faculty } from "../../test/observerFixtures";

let options: RequestOptions[];
let writes: { signupId: number; matchId: number }[];
let failSend: boolean;
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
  failSend = false;
  fakeBackend({
    // The faculty dashboard behind the dialog loads its own status and observations.
    "/api/observer-eligibility/faculty/4": () => Response.json(faculty),
    "/api/professors/4/observations": () => Response.json([]),
    "/api/professors/4/dashboard": () => Response.json(dashboard),
    "/api/professors/4/observer-requests": (init) => {
      if (init?.method !== "POST") return Response.json(options);
      const body = JSON.parse(String(init.body));
      writes.push(body);
      if (failSend)
        return Response.json(
          { detail: "This match is no longer proposed." },
          { status: 409 },
        );
      const candidate = options[0]?.candidates.find(
        (item) => item.matchId === body.matchId,
      );
      const saved = {
        ...request,
        requestId: 50 + writes.length,
        observerId: candidate?.observerId ?? 1,
      };
      options[0]?.requests.push({
        request: saved,
        observerName: candidate?.observerName ?? "Alice Alice",
      });
      return Response.json(saved, { status: 201 });
    },
  });
});

afterEach(() => {
  cleanup();
  resetBackend();
});

function open() {
  signInAs("professor", { professorId: 4 });
  window.history.pushState({}, "", "/faculty/observers");
  return render(<App />);
}

async function choose() {
  fireEvent.click(await screen.findByRole("checkbox", { name: /Alice Alice/ }));
}

function send() {
  fireEvent.click(
    screen.getByRole("button", { name: "Send evaluation request" }),
  );
}

test("sends the selected match and restores its pending status after reopening", async () => {
  const view = open();
  await choose();
  send();
  await screen.findByText("Requests recorded for Alice Alice.");
  expect(writes).toEqual([{ signupId: 31, matchId: 2 }]);

  view.unmount();
  open();
  await screen.findByText("Pending");
  expect(screen.getByText(/1 of 5 requests pending/)).toBeDefined();
});

test("keeps the choice after a failed send and allows a retry", async () => {
  failSend = true;
  open();
  await choose();
  send();
  await screen.findByRole("alert");
  expect(screen.queryByText("Pending")).toBeNull();
  expect(
    (screen.getByRole("checkbox", { name: /Alice Alice/ }) as HTMLInputElement)
      .checked,
  ).toBe(true);

  failSend = false;
  send();
  await screen.findByText("Pending");
  expect(writes).toHaveLength(2);
});

test("an observer who declined cannot be invited again, but other candidates can", async () => {
  const first = options[0];
  if (!first) throw new Error("Missing signup");
  first.requests = [
    {
      observerName: "Alice Alice",
      request: { ...request, status: "DECLINED" },
    },
  ];
  first.candidates.push({
    matchId: 3,
    observerId: 2,
    observerName: "Bob Observer",
    department: "Computer Science",
  });
  open();
  expect(
    (
      (await screen.findByRole("checkbox", {
        name: /Alice Alice/,
      })) as HTMLInputElement
    ).disabled,
  ).toBe(true);
  expect(screen.getByText(/Declined — cannot invite again/)).toBeDefined();
  expect(
    (screen.getByRole("checkbox", { name: /Bob Observer/ }) as HTMLInputElement)
      .disabled,
  ).toBe(false);
});

test("up to five requests can be pending, counting ones already sent", async () => {
  const first = options[0];
  if (!first) throw new Error("Missing signup");
  first.candidates = Array.from({ length: 6 }, (_, index) => ({
    matchId: index + 2,
    observerId: index + 1,
    observerName: `Observer ${index + 1}`,
    department: "Computer Science",
  }));
  first.requests = [{ request: { ...request }, observerName: "Observer 1" }];
  open();
  const boxes = (await screen.findAllByRole("checkbox")) as HTMLInputElement[];
  // Observer 1 already has a pending request, so four more reach the limit.
  expect(boxes[0]?.disabled).toBe(true);
  for (const box of boxes.slice(1, 5)) fireEvent.click(box);
  expect(boxes[5]?.disabled).toBe(true);

  send();
  await screen.findByText(/Requests recorded for/);
  expect(writes.map((write) => write.matchId)).toEqual([3, 4, 5, 6]);
  expect(screen.getByText(/5 of 5 requests pending/)).toBeDefined();
});

test("shows confirmed and pending requests together without stopping pending responses", async () => {
  if (!options[0]) throw new Error("Missing fixture");
  options[0].requests = [
    {
      observerName: "Alice Alice",
      request: { ...request, status: "CONFIRMED" },
    },
    {
      observerName: "Observer 2",
      request: { ...request, requestId: 51, observerId: 2 },
    },
  ];
  open();
  await screen.findByText(/1 confirmed; 1 pending/);
  expect(screen.getByText("Confirmed")).toBeDefined();
  expect(screen.getByText("Pending")).toBeDefined();
  expect(screen.getByText(/Other observers can still respond/)).toBeDefined();
});
