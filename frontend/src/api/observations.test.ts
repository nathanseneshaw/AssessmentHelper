import { afterEach, expect, mock, spyOn, test } from "bun:test";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import {
  formatDate,
  formatTime,
  formatTimestamp,
  formatWhen,
  useApiResource,
} from "./observations";

afterEach(() => {
  cleanup();
  mock.restore();
});

test("dates keep the day they were written with", () => {
  expect(formatDate("2026-11-04")).toBe("Nov 4, 2026");
  expect(formatDate("2026-01-01")).toBe("Jan 1, 2026");
  expect(formatDate("2026-12-31")).toBe("Dec 31, 2026");
});

test("times use a 12-hour clock", () => {
  expect(formatTime("08:30:00")).toBe("8:30 AM");
  expect(formatTime("00:05:00")).toBe("12:05 AM");
  expect(formatTime("12:00")).toBe("12:00 PM");
  expect(formatTime("13:45:00")).toBe("1:45 PM");
});

test("timestamps are shown in Central time as written", () => {
  expect(formatTimestamp("2026-11-04T09:15:00")).toBe(
    "Nov 4, 2026, 9:15 AM CT",
  );
  expect(formatTimestamp("2026-11-04T23:59:59.5")).toBe(
    "Nov 4, 2026, 11:59 PM CT",
  );
});

test("unexpected values are shown unchanged", () => {
  expect(formatDate("soon")).toBe("soon");
  expect(formatTime("noon")).toBe("noon");
});

test("an observation's time is its date and start time, and null without a date", () => {
  expect(formatWhen("2026-11-04", "08:30:00")).toBe("Nov 4, 2026, 8:30 AM");
  expect(formatWhen("2026-11-04", null)).toBe("Nov 4, 2026");
  expect(formatWhen(null, "08:30:00")).toBeNull();
  expect(formatWhen(null, null)).toBeNull();
});

/** Holds every request until the test answers it, oldest first. */
function heldRequests() {
  const waiting: ((response: Response) => void)[] = [];
  spyOn(globalThis, "fetch").mockImplementation(
    (() =>
      new Promise<Response>((resolve) => {
        waiting.push(resolve);
      })) as unknown as typeof fetch,
  );
  return {
    count: () => waiting.length,
    answer: (response: Response) => waiting.shift()?.(response),
  };
}

function loadThings(path: string | null = "/api/things") {
  return renderHook(
    ({ path }: { path: string | null }) => useApiResource<string[]>(path),
    { initialProps: { path } },
  );
}

test("a reload keeps showing the current data until the new data arrives", async () => {
  const requests = heldRequests();
  const { result } = loadThings();
  expect(result.current.data).toBeNull();
  requests.answer(Response.json(["first"]));
  await waitFor(() => expect(result.current.data).toEqual(["first"]));

  act(() => result.current.reload());
  await waitFor(() => expect(requests.count()).toBe(1));
  expect(result.current.data).toEqual(["first"]);

  requests.answer(Response.json(["second"]));
  await waitFor(() => expect(result.current.data).toEqual(["second"]));
});

test("a failed reload keeps the data, and the error stays until a reload succeeds", async () => {
  const requests = heldRequests();
  const { result } = loadThings();
  requests.answer(Response.json(["first"]));
  await waitFor(() => expect(result.current.data).toEqual(["first"]));

  act(() => result.current.reload());
  await waitFor(() => expect(requests.count()).toBe(1));
  requests.answer(
    Response.json({ detail: "Things are unavailable." }, { status: 503 }),
  );
  await waitFor(() =>
    expect(result.current.error).toBe("Things are unavailable."),
  );
  expect(result.current.data).toEqual(["first"]);

  act(() => result.current.reload());
  await waitFor(() => expect(requests.count()).toBe(1));
  expect(result.current.error).toBe("Things are unavailable.");
  requests.answer(Response.json(["second"]));
  await waitFor(() => expect(result.current.data).toEqual(["second"]));
  expect(result.current.error).toBe("");
});

test("a different path starts empty", async () => {
  const requests = heldRequests();
  const { result, rerender } = loadThings();
  requests.answer(Response.json(["first"]));
  await waitFor(() => expect(result.current.data).toEqual(["first"]));

  rerender({ path: "/api/other-things" });
  expect(result.current.data).toBeNull();
  requests.answer(
    Response.json({ detail: "Other things are unavailable." }, { status: 503 }),
  );
  await waitFor(() =>
    expect(result.current.error).toBe("Other things are unavailable."),
  );

  rerender({ path: null });
  expect(result.current.data).toBeNull();
  expect(result.current.error).toBe("");
});
