// Calendar dates such as "2026-10-31" parse as UTC midnight.
// Formatting them in UTC keeps the day from slipping back west of Greenwich.
export function shortDate(isoDate: string) {
  return new Date(isoDate).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    timeZone: "UTC",
  });
}

/** A time of day such as "13:05:00" as "1:05 PM". */
export function clockTime(time: string) {
  const [hours = 0, minutes = 0] = time.split(":").map(Number);
  return `${hours % 12 || 12}:${String(minutes).padStart(2, "0")} ${hours < 12 ? "AM" : "PM"}`;
}
