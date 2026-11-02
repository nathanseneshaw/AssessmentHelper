import { afterEach, describe, expect, test } from "bun:test";
import { clockTime, longDate, monthYear, shortDate } from "./format";

const originalZone = process.env.TZ;

afterEach(() => {
  // Assigning undefined would store the string "undefined", so an unset zone is deleted instead.
  if (originalZone === undefined) delete process.env.TZ;
  else process.env.TZ = originalZone;
});

// West of Greenwich a calendar date's UTC midnight is the evening before, and far east it is already morning.
const ZONES = ["America/Chicago", "America/Los_Angeles", "Pacific/Kiritimati"];

describe.each(ZONES)("in %s", (zone) => {
  test("calendar dates keep their day", () => {
    process.env.TZ = zone;
    expect(shortDate("2026-10-31")).toBe("Oct 31");
    expect(longDate("2026-10-31")).toBe("Oct 31, 2026");
    expect(monthYear("2026-10-31")).toBe("Oct 2026");
  });

  test("the first and last days of a year stay in that year", () => {
    process.env.TZ = zone;
    expect(longDate("2027-01-01")).toBe("Jan 1, 2027");
    expect(monthYear("2027-01-01")).toBe("Jan 2027");
    expect(longDate("2026-12-31")).toBe("Dec 31, 2026");
    expect(monthYear("2026-12-31")).toBe("Dec 2026");
  });
});

test("the guard is needed: Los Angeles reads the same date as the day before", () => {
  process.env.TZ = "America/Los_Angeles";
  expect(new Date("2026-10-31").getDate()).toBe(30);
});

test("longDate writes single-digit days without padding", () => {
  expect(longDate("2026-03-04")).toBe("Mar 4, 2026");
});

test.each([
  ["00:00:00", "12:00 AM"],
  ["00:05:00", "12:05 AM"],
  ["08:30:00", "8:30 AM"],
  ["11:59:00", "11:59 AM"],
  ["12:00:00", "12:00 PM"],
  ["13:05:00", "1:05 PM"],
  ["23:45:00", "11:45 PM"],
  ["14:15", "2:15 PM"],
])("clockTime(%p) is %p", (time, expected) => {
  expect(clockTime(time)).toBe(expected);
});
