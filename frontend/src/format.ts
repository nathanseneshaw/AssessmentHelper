// Calendar dates such as "2026-10-31" parse as UTC midnight.
// Formatting them in UTC keeps the day from slipping back west of Greenwich.
export function shortDate(isoDate: string) {
  return new Date(isoDate).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  });
}

/** A calendar date with its year, such as "Oct 31, 2026". */
export function longDate(isoDate: string) {
  return new Date(isoDate).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  });
}

/** A calendar date as its month and year, such as "Mar 2027". */
export function monthYear(isoDate: string) {
  return new Date(isoDate).toLocaleDateString("en-US", {
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });
}

/** A time of day such as "13:05:00" as "1:05 PM". */
export function clockTime(time: string) {
  const [hours = 0, minutes = 0] = time.split(":").map(Number);
  return `${hours % 12 || 12}:${String(minutes).padStart(2, "0")} ${hours < 12 ? "AM" : "PM"}`;
}
