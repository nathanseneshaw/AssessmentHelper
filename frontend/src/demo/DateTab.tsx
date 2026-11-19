import { useEffect, useId, useState } from "react";
import { type DemoDate, getDemoDate, moveDemoDate } from "../api/demo";
import { formatDate } from "../api/observations";
import { attempt, type Outcome, OutcomeText } from "./outcome";

const DAY = 24 * 60 * 60 * 1000;

function addDays(date: string, days: number) {
  return new Date(Date.parse(`${date}T00:00:00Z`) + days * DAY)
    .toISOString()
    .slice(0, 10);
}

function plural(count: number, word: string) {
  return `${count} ${word}${count === 1 ? "" : "s"}`;
}

function distance(daysAhead: number) {
  if (daysAhead === 0) return "This is today's real date.";
  const days = plural(Math.abs(daysAhead), "day");
  return daysAhead > 0
    ? `${days} ahead of the real date.`
    : `${days} behind the real date.`;
}

export function DateTab({ onDataChanged }: { onDataChanged: () => void }) {
  const dateId = useId();
  const [current, setCurrent] = useState<DemoDate | null>(null);
  const [date, setDate] = useState("");
  const [moving, setMoving] = useState(false);
  const [outcome, setOutcome] = useState<Outcome | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    getDemoDate(controller.signal).then(
      (loaded) => {
        setCurrent(loaded);
        setDate(loaded.today);
      },
      (e) => {
        if (!controller.signal.aborted)
          setOutcome({
            error: true,
            text: e instanceof Error ? e.message : "Could not load the date.",
          });
      },
    );
    return () => controller.abort();
  }, []);

  async function move(target: string) {
    setMoving(true);
    setOutcome(
      await attempt(async () => {
        const moved = await moveDemoDate(target);
        setCurrent(moved);
        setDate(moved.today);
        onDataChanged();
        const expired = moved.expiredRequests
          ? ` ${plural(moved.expiredRequests, "pending request")} expired.`
          : "";
        return `Moved to ${formatDate(moved.today)}.${expired}`;
      }, "Could not change the date."),
    );
    setMoving(false);
  }

  return (
    <div>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          move(date);
        }}
      >
        <label htmlFor={dateId} className="block font-semibold text-sm">
          App date
        </label>
        <div className="mt-1 flex gap-2">
          <input
            id={dateId}
            type="date"
            required
            value={date}
            min={current?.earliest ?? undefined}
            max={current?.latest ?? undefined}
            onChange={(e) => setDate(e.target.value)}
            className="min-w-0 flex-1 rounded-lg border border-line px-3 py-2 text-sm"
          />
          <button
            type="submit"
            disabled={!current || moving || date === current.today}
            className="rounded-lg bg-brand-green px-4 py-2 font-semibold text-sm text-white hover:opacity-90 disabled:opacity-50"
          >
            Move
          </button>
        </div>
      </form>
      {current && (
        <div className="mt-2 flex items-center justify-between gap-3">
          <p className="text-muted text-xs">{distance(current.daysAhead)}</p>
          {current.daysAhead !== 0 && (
            <button
              type="button"
              disabled={moving}
              onClick={() => move(addDays(current.today, -current.daysAhead))}
              className="shrink-0 rounded-lg border border-line px-3 py-1.5 text-sm hover:bg-mist disabled:opacity-50"
            >
              Back to today
            </button>
          )}
        </div>
      )}
      <OutcomeText outcome={outcome} />
    </div>
  );
}
