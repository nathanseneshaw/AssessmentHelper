import { useEffect, useState } from "react";
import {
  type DashboardMetrics,
  getDashboardMetrics,
  getObservations,
  type ObservationTracking,
  type Ratio,
} from "../../api/acDashboard";
import {
  getOverdueQueue,
  type OverdueQueue,
} from "../../api/evaluationEligibility";
import { type AcState, useObserverData } from "../../api/observerEligibility";
import { ActionButton } from "../../components/ActionButton";
import { type Kpi, KpiCard } from "../../components/KpiCard";
import { PageHeader } from "../../components/PageHeader";

// Process feedback is not saved anywhere yet, so there is nothing to measure.
const surveyCompletion: Kpi = {
  label: "Survey Completion",
  value: "—",
  note: "Not tracked yet",
};

interface ReportData {
  metrics: DashboardMetrics;
  overdue: OverdueQueue;
  matching: AcState;
  tracking: ObservationTracking;
}

interface Report {
  cycleLabel: string;
  kpis: Kpi[];
  executiveMetrics: { label: string; value: number }[];
  cycleSummary: string[];
}

function rate(
  { count, total }: Ratio,
  counted: string,
  empty: string,
): Pick<Kpi, "value" | "note"> {
  return total === 0
    ? { value: "—", note: empty }
    : {
        value: `${Math.round((count / total) * 100)}%`,
        note: `${count} of ${total} ${counted}`,
      };
}

const plural = (n: number, one: string, many: string) =>
  `${n} ${n === 1 ? one : many}`;

function buildReport({
  metrics,
  overdue,
  matching,
  tracking,
}: ReportData): Report {
  const unmatched = matching.signups.filter(
    (s) => s.candidates.length === 0,
  ).length;
  // Every observation with an observer has been scheduled, whether or not it has happened yet.
  const scheduled = tracking.observations.filter(
    (o) => o.status !== "AWAITING_OBSERVER",
  ).length;
  const completed = tracking.observations.filter(
    (o) => o.status === "COMPLETED",
  ).length;
  const missing = tracking.observations.filter(
    (o) => o.status === "MISSING",
  ).length;
  return {
    cycleLabel: metrics.cycleLabel,
    kpis: [
      {
        label: "Assessment Participation Rate",
        ...rate(
          metrics.participation,
          "due faculty signed up",
          "No faculty are due this cycle",
        ),
      },
      {
        label: "Observer Utilization Rate",
        ...rate(
          metrics.observerUtilization,
          "signups have an observer",
          "No signups this cycle",
        ),
      },
      {
        label: "List Sufficiency",
        ...rate(
          metrics.listSufficiency,
          `signups have a full list of ${metrics.listSize}`,
          "No signups this cycle",
        ),
      },
      surveyCompletion,
    ],
    executiveMetrics: [
      { label: "Faculty due for evaluation", value: metrics.facultyDue },
      { label: "Missing observation count", value: missing },
      { label: "Unmatched faculty report", value: unmatched },
      { label: "Overdue evaluation count", value: overdue.count },
    ],
    cycleSummary: [
      `${metrics.facultyDue} faculty due`,
      `${metrics.participation.count} signed up`,
      `${plural(scheduled, "observation", "observations")} scheduled`,
      `${unmatched} unmatched faculty`,
      `${completed} completed`,
      plural(missing, "missing observation", "missing observations"),
    ],
  };
}

/** Saves text the page builds from the loaded report, so the files match what is on screen. */
function downloadFile(filename: string, type: string, content: string) {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

function exportReport(report: Report) {
  const cell = (value: string | number) =>
    `"${String(value).replaceAll('"', '""')}"`;
  const rows = [
    ["Section", "Metric", "Value", "Note"],
    ["Cycle", "Evaluation cycle", report.cycleLabel, ""],
    ...report.kpis.map((kpi) => ["KPI", kpi.label, kpi.value, kpi.note]),
    ...report.executiveMetrics.map((metric) => [
      "Executive dashboard",
      metric.label,
      metric.value,
      "",
    ]),
  ];
  downloadFile(
    "reports-and-kpis.csv",
    "text/csv",
    `${rows.map((row) => row.map(cell).join(",")).join("\n")}\n`,
  );
}

function downloadSummary(report: Report) {
  downloadFile(
    `${report.cycleLabel.toLowerCase().replaceAll(" ", "-")}-cycle-summary.txt`,
    "text/plain",
    `${[`${report.cycleLabel} Cycle`, "", ...report.cycleSummary].join("\n")}\n`,
  );
}

const executiveLabels = [
  "Faculty due for evaluation",
  "Missing observation count",
  "Unmatched faculty report",
  "Overdue evaluation count",
];

export function AcReports() {
  const { data: matching, error: matchingError } =
    useObserverData<AcState>("/ac");
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [overdue, setOverdue] = useState<OverdueQueue | null>(null);
  const [tracking, setTracking] = useState<ObservationTracking | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    const fail = () => {
      if (!controller.signal.aborted) setFailed(true);
    };
    getDashboardMetrics(controller.signal).then(setMetrics).catch(fail);
    getOverdueQueue(controller.signal).then(setOverdue).catch(fail);
    getObservations(controller.signal).then(setTracking).catch(fail);
    return () => controller.abort();
  }, []);
  // The report is one snapshot: it shows only once every source has loaded, so an export is never half filled.
  const unavailable = failed || Boolean(matchingError);
  const report =
    !unavailable && metrics && overdue && matching && tracking
      ? buildReport({ metrics, overdue, matching, tracking })
      : null;
  const placeholder = unavailable
    ? { value: "Unavailable", note: "Report data could not be loaded" }
    : { value: "—", note: "Loading…" };
  const kpis: Kpi[] =
    report?.kpis ??
    [
      "Assessment Participation Rate",
      "Observer Utilization Rate",
      "List Sufficiency",
    ]
      .map((label): Kpi => ({ label, ...placeholder }))
      .concat(surveyCompletion);

  return (
    <div>
      <PageHeader
        title="Reports & KPIs"
        subtitle="Review cycle performance and executive dashboard metrics."
        action={
          <ActionButton
            tone="orange"
            disabled={!report}
            onClick={() => report && exportReport(report)}
            className="min-w-52.5"
          >
            Export Report
          </ActionButton>
        }
      />

      <div className="mt-10.25 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {kpis.map((kpi) => (
          <KpiCard key={kpi.label} kpi={kpi} />
        ))}
      </div>

      <div className="mt-9.5 grid gap-10 lg:min-h-156.25 lg:grid-cols-[minmax(0,12fr)_minmax(0,7fr)]">
        <section className="rounded-lg border border-line bg-white px-6 pt-5 pb-10">
          <h2 className="font-medium text-[22px] leading-7">
            Executive Dashboard Metrics
          </h2>
          <dl className="mt-9.5 flex flex-col gap-11">
            {(
              report?.executiveMetrics ??
              executiveLabels.map((label) => ({ label, value: "—" }))
            ).map((metric) => (
              <div
                key={metric.label}
                className="grid grid-cols-[minmax(0,1fr)_5.5rem] items-center gap-4"
              >
                <dt>{metric.label}</dt>
                <dd className="font-medium text-3xl text-brand-orange">
                  {metric.value}
                </dd>
              </div>
            ))}
          </dl>
        </section>

        <section className="flex flex-col rounded-lg border border-line bg-mist px-6 pt-6 pb-10 lg:pb-18.5">
          <h2 className="font-medium text-[22px] text-brand-green leading-7">
            {report ? `${report.cycleLabel} Cycle` : "Current Cycle"}
          </h2>
          <ul className="mt-8.5 text-lg leading-[1.19]">
            {report ? (
              report.cycleSummary.map((item) => <li key={item}>{item}</li>)
            ) : (
              <li className="text-muted">
                {unavailable
                  ? "The cycle summary could not be loaded."
                  : "Loading cycle summary…"}
              </li>
            )}
          </ul>
          <div className="mt-auto pt-10">
            <ActionButton
              tone="orange"
              disabled={!report}
              onClick={() => report && downloadSummary(report)}
              className="min-w-59.25"
            >
              Download Summary
            </ActionButton>
          </div>
        </section>
      </div>
    </div>
  );
}
