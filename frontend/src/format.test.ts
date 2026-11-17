import { afterEach, expect, test } from "bun:test";
import { clockTime, longDate, monthYear, shortDate } from "./format";

const originalZone = process.env.TZ;

afterEach(() => {
  // Assigning undefined would store the string "undefined", so an unset zone is deleted instead.
  if (originalZone === undefined) delete process.env.TZ;
  else process.env.TZ = originalZone;
});

// West of Greenwich a calendar date's UTC midnight is the evening before, and far east it is already morning.
test.each(["America/Los_Angeles", "Pacific/Kiritimati"])(
  "calendar dates keep their day when the time zone is %s",
  (zone) => {
    process.env.TZ = zone;
    expect(shortDate("2026-10-31")).toBe("Oct 31");
    expect(longDate("2026-10-31")).toBe("Oct 31, 2026");
    expect(monthYear("2026-10-31")).toBe("Oct 2026");
  },
);

test.each([
  ["00:05:00", "12:05 AM"],
  ["12:00:00", "12:00 PM"],
  ["14:15", "2:15 PM"],
])("%p shows as %p when formatted with clockTime", (time, expected) => {
  expect(clockTime(time)).toBe(expected);
});
