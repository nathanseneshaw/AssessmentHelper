import {
  type ReactNode,
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";
import { apiFetch } from "../api/client";
import { setSelectedCycle } from "../api/cycleSelection";
import {
  type CycleDates,
  CycleDatesDialog,
} from "../pages/ac/CycleDatesDialog";
import { CycleFilesDialog } from "../pages/ac/CycleFilesDialog";
import { CycleSetupDialog } from "../pages/ac/CycleSetupDialog";
import { CycleReadOnlyContext } from "./CycleAccess";

interface CycleOption extends CycleDates {
  period: "PAST" | "CURRENT" | "UPCOMING" | "UNAVAILABLE";
}

export function AcCycleScope({ children }: { children: ReactNode }) {
  const [cycles, setCycles] = useState<CycleOption[] | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [setupOpen, setSetupOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [filesOpen, setFilesOpen] = useState(false);
  const [revision, setRevision] = useState(0);
  const menu = useRef<HTMLDetailsElement>(null);
  const [readiness, setReadiness] = useState<{
    cycleId: number;
    period: CycleOption["period"];
    state: string;
    reason: string | null;
  } | null>(null);

  const load = useCallback(async (preferred?: number) => {
    try {
      const result = await apiFetch<CycleOption[]>("/api/ac/cycles");
      setCycles(result);
      setSelectedId(
        preferred && result.some((cycle) => cycle.cycleId === preferred)
          ? preferred
          : (result.find((cycle) => cycle.period === "CURRENT")?.cycleId ??
              null),
      );
      setError("");
    } catch (cause) {
      setError(
        cause instanceof Error
          ? cause.message
          : "Could not load evaluation cycles.",
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
    return () => setSelectedCycle(null);
  }, [load]);

  useEffect(() => {
    const refresh = () => void load(selectedId ?? undefined);
    window.addEventListener("focus", refresh);
    const timer = window.setInterval(refresh, 60000);
    return () => {
      window.removeEventListener("focus", refresh);
      window.clearInterval(timer);
    };
  }, [selectedId, load]);

  const selected =
    cycles?.find((cycle) => cycle.cycleId === selectedId) ?? null;
  const selectedPeriod = selected?.period;
  // Revision reloads readiness after editing the selected cycle.
  // biome-ignore lint/correctness/useExhaustiveDependencies: revision triggers a refresh.
  useEffect(() => {
    setReadiness(null);
    if (selectedId === null || !selectedPeriod) return;
    const controller = new AbortController();
    apiFetch<{ state: string; reason: string | null }>(
      `/api/ac/cycles/${selectedId}/readiness`,
      { signal: controller.signal },
    )
      .then((result) =>
        setReadiness({
          cycleId: selectedId,
          period: selectedPeriod,
          ...result,
        }),
      )
      .catch((cause: unknown) => {
        if (!controller.signal.aborted)
          setReadiness({
            cycleId: selectedId,
            period: selectedPeriod,
            state: "ERROR",
            reason:
              cause instanceof Error
                ? cause.message
                : "Could not check cycle readiness.",
          });
      });
    return () => controller.abort();
  }, [selectedId, revision, selectedPeriod]);

  const checked =
    readiness?.cycleId === selectedId && readiness?.period === selected?.period
      ? readiness
      : null;
  setSelectedCycle(
    selected
      ? {
          cycleId: selected.cycleId,
          period: checked?.state === "FAILED" ? "PAST" : selected.period,
        }
      : null,
  );

  if (loading) return <p role="status">Loading evaluation cycles…</p>;
  return (
    <>
      <div className="mb-6 flex flex-wrap items-end gap-3">
        <details ref={menu} className="relative min-w-72 text-sm">
          <summary
            aria-label="Evaluation cycle"
            className="cursor-pointer rounded border border-line bg-white p-2"
          >
            {selected
              ? `${selected.term} ${selected.year} Evaluation Cycle${selected.period === "CURRENT" ? " (CURRENT)" : selected.period === "UPCOMING" ? " (UPCOMING)" : selected.period === "UNAVAILABLE" ? " (UNAVAILABLE)" : ""}`
              : "No current evaluation cycle"}
          </summary>
          <div className="absolute z-20 mt-1 w-full rounded border border-line bg-white p-1 shadow-lg">
            {cycles?.map((cycle) => (
              <button
                key={cycle.cycleId}
                type="button"
                aria-pressed={selectedId === cycle.cycleId}
                className="block w-full rounded px-3 py-2 text-left hover:bg-mist"
                onClick={() => {
                  setSelectedId(cycle.cycleId);
                  if (menu.current) menu.current.open = false;
                }}
              >
                {cycle.term} {cycle.year} Evaluation Cycle
                {cycle.period === "CURRENT"
                  ? " (CURRENT)"
                  : cycle.period === "UPCOMING"
                    ? " (UPCOMING)"
                    : cycle.period === "UNAVAILABLE"
                      ? " (UNAVAILABLE)"
                      : ""}
              </button>
            ))}
            <button
              type="button"
              className="block w-full border-line border-t px-3 py-2 text-left text-brand-green hover:bg-mist"
              onClick={() => {
                if (menu.current) menu.current.open = false;
                setSetupOpen(true);
              }}
            >
              + Create New Upcoming Cycle
            </button>
          </div>
        </details>
        {selected?.period === "UPCOMING" && (
          <button
            type="button"
            className="rounded border border-line bg-white px-4 py-2 text-sm"
            onClick={() => setEditOpen(true)}
          >
            Edit Cycle Dates
          </button>
        )}
        {selected?.period === "UPCOMING" && (
          <button
            type="button"
            className="rounded border border-line bg-white px-4 py-2 text-sm"
            onClick={() => setFilesOpen(true)}
          >
            Replace Cycle Files
          </button>
        )}
      </div>
      {error && (
        <p role="alert" className="mb-4 text-brand-red">
          {error}
        </p>
      )}
      {selected?.period === "PAST" && (
        <p className="mb-4 text-sm text-muted">This past cycle is read-only.</p>
      )}
      {selected?.period === "UPCOMING" && (
        <p className="mb-4 text-sm text-muted">
          This cycle begins in a future semester.
        </p>
      )}
      {checked?.reason && (
        <p role="alert" className="mb-4 text-sm text-brand-red">
          {checked.state === "FAILED"
            ? "This cycle failed readiness and cannot open. "
            : "Cycle needs attention: "}
          {checked.reason}
        </p>
      )}
      {selected && !checked ? (
        <p role="status">Checking cycle readiness…</p>
      ) : selected &&
        checked &&
        !["FAILED", "ERROR"].includes(checked.state) ? (
        <CycleReadOnlyContext.Provider value={selected.period !== "CURRENT"}>
          <div key={`${selected.cycleId}-${revision}`}>{children}</div>
        </CycleReadOnlyContext.Provider>
      ) : selected ? null : (
        <p>
          No current evaluation cycle. Select an existing cycle or create an
          upcoming one.
        </p>
      )}
      {setupOpen && (
        <CycleSetupDialog
          onClose={() => setSetupOpen(false)}
          onCreated={(id) => {
            setSetupOpen(false);
            void load(id);
          }}
        />
      )}
      {editOpen && selected && (
        <CycleDatesDialog
          cycle={selected}
          onClose={() => setEditOpen(false)}
          onSaved={() => {
            setEditOpen(false);
            setRevision((value) => value + 1);
            void load(selected.cycleId);
          }}
        />
      )}
      {filesOpen && selected && (
        <CycleFilesDialog
          cycleId={selected.cycleId}
          label={`${selected.term} ${selected.year}`}
          onClose={() => setFilesOpen(false)}
          onSaved={() => {
            setFilesOpen(false);
            setRevision((value) => value + 1);
            void load(selected.cycleId);
          }}
        />
      )}
    </>
  );
}
