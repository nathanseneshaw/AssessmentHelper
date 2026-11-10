import { afterEach, expect, test } from "bun:test";
import { act, cleanup, renderHook } from "@testing-library/react";
import { metrics } from "../test/acDashboardFixtures";
import {
  type DashboardMetrics,
  surveyCompletionRate,
  useDashboardRefresh,
} from "./acDashboard";
import { observerChanged } from "./observerEligibility";

afterEach(cleanup);

const survey = (
  count: number,
  total: number,
  surveyStatus: DashboardMetrics["surveyStatus"],
) =>
  surveyCompletionRate({
    ...metrics,
    surveyCompletion: { count, total },
    surveyStatus,
  });

test("survey completion shows a percent, or why it is empty before the survey opens", () => {
  expect(survey(4, 9, "AVAILABLE")).toEqual({
    value: "44%",
    note: "4 of 9 observees submitted feedback",
  });
  expect(survey(0, 9, "NOT_OPEN")).toEqual({
    value: "—",
    note: "Survey opens after the observation deadline",
  });
});

function refreshWith(matching: unknown) {
  return renderHook(
    ({ matching }: { matching: unknown }) => useDashboardRefresh(matching),
    { initialProps: { matching } },
  );
}

test("the figures refresh when the matching data changes, but not for its first load or an identical answer", () => {
  const { result, rerender } = refreshWith({ unmatched: 1 });
  rerender({ matching: { unmatched: 1 } });
  expect(result.current.version).toBe(0);
  rerender({ matching: { unmatched: 2 } });
  expect(result.current.version).toBe(1);
  // A reload empties the matching data before the answer arrives.
  rerender({ matching: null });
  rerender({ matching: { unmatched: 2 } });
  expect(result.current.version).toBe(1);
  rerender({ matching: null });
  rerender({ matching: { unmatched: 0 } });
  expect(result.current.version).toBe(2);
});

test("the figures refresh after an announced change, on focus and on retry", () => {
  const { result } = refreshWith(null);
  act(() => observerChanged());
  act(() => {
    window.dispatchEvent(new Event("focus"));
  });
  act(() => result.current.refresh());
  expect(result.current.version).toBe(3);
});
