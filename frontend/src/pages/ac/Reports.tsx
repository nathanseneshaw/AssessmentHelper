import { ActionButton } from "../../components/ActionButton";
import { type Kpi, KpiCard } from "../../components/KpiCard";
import { PageHeader } from "../../components/PageHeader";

const kpis: Kpi[] = [
  {
    label: "Eligibility Accuracy",
    value: "96%",
    note: "Faculty evaluation KPI",
  },
  {
    label: "Assessment Participation Rate",
    value: "82%",
    note: "Assessment participation",
  },
  {
    label: "Observer Utilization Rate",
    value: "74%",
    note: "Assessment participation",
  },
  { label: "List Sufficiency", value: "91%", note: "Recommendation engine" },
  { label: "Survey Completion", value: "78%", note: "Completion KPI" },
];

const executiveMetrics = [
  { label: "Faculty due for evaluation", value: 18 },
  { label: "Missing observation count", value: 4 },
  { label: "Unmatched faculty report", value: 2 },
  { label: "Overdue evaluation count", value: 8 },
];

const cycleTitle = "Fall 2026 Cycle";

const cycleSummary = [
  "18 faculty due",
  "15 signed up",
  "13 observations scheduled",
  "2 unmatched faculty",
  "9 completed",
  "4 missing observations",
];

/** Saves text the page builds from its own data, so the buttons work without a backend. */
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

function exportReport() {
  const cell = (value: string | number) =>
    `"${String(value).replaceAll('"', '""')}"`;
  const rows = [
    ["Section", "Metric", "Value", "Note"],
    ...kpis.map((kpi) => ["KPI", kpi.label, kpi.value, kpi.note]),
    ...executiveMetrics.map((metric) => [
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

function downloadSummary() {
  downloadFile(
    "fall-2026-cycle-summary.txt",
    "text/plain",
    `${[cycleTitle, "", ...cycleSummary].join("\n")}\n`,
  );
}

export function AcReports() {
  return (
    <div>
      <PageHeader
        title="Reports & KPIs"
        subtitle="Review cycle performance and executive dashboard metrics."
        action={
          <ActionButton
            tone="orange"
            onClick={exportReport}
            className="min-w-52.5"
          >
            Export Report
          </ActionButton>
        }
      />

      {/*
        One row on wide screens, where the last card is a little wider. Below
        that the labels would wrap, so the cards sit 3 + 2 (or 2 + 2 + 1).
      */}
      <div className="mt-10.25 grid gap-4 sm:grid-cols-2 lg:grid-cols-6 2xl:grid-cols-[repeat(4,minmax(0,1fr))_minmax(0,1.32fr)]">
        {kpis.map((kpi, i) => (
          <div
            key={kpi.label}
            className={`grid 2xl:col-span-1 ${
              i < 3 ? "lg:col-span-2" : "lg:col-span-3"
            } ${i === kpis.length - 1 ? "sm:col-span-2" : ""}`}
          >
            <KpiCard kpi={kpi} />
          </div>
        ))}
      </div>

      <div className="mt-9.5 grid gap-10 lg:min-h-156.25 lg:grid-cols-[minmax(0,12fr)_minmax(0,7fr)]">
        <section className="rounded-lg border border-line bg-white px-6 pt-5 pb-10">
          <h2 className="font-medium text-[22px] leading-7">
            Executive Dashboard Metrics
          </h2>
          <dl className="mt-9.5 flex flex-col gap-11">
            {executiveMetrics.map((metric) => (
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
            {cycleTitle}
          </h2>
          <ul className="mt-8.5 text-lg leading-[1.19]">
            {cycleSummary.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
          <div className="mt-auto pt-10">
            <ActionButton
              tone="orange"
              onClick={downloadSummary}
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
