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

test("dates and times use a 12-hour clock when formatted", () => {
  expect(formatDate("2026-11-04")).toBe("Nov 4, 2026");
  expect(formatTime("00:05:00")).toBe("12:05 AM");
  expect(formatTime("13:45:00")).toBe("1:45 PM");
  expect(formatTimestamp("2026-11-04T09:15:00")).toBe(
    "Nov 4, 2026, 9:15 AM CT",
  );
  expect(formatWhen("2026-11-04", "08:30:00")).toBe("Nov 4, 2026, 8:30 AM");
  expect(formatWhen(null, "08:30:00")).toBeNull();
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

test("current data and error stay on screen when a reload has not answered yet", async () => {
  const requests = heldRequests();
  const { result } = loadThings();
  requests.answer(Response.json(["first"]));
  await waitFor(() => expect(result.current.data).toEqual(["first"]));

  act(() => result.current.reload());
  await waitFor(() => expect(requests.count()).toBe(1));
  expect(result.current.data).toEqual(["first"]);
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

test("data starts empty when the path changes", async () => {
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
