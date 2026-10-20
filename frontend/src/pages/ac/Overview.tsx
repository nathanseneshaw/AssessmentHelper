import type { ReactNode } from "react";
import { ActionButton } from "../../components/ActionButton";
import { type Kpi, KpiCard } from "../../components/KpiCard";
import { PageHeader } from "../../components/PageHeader";

const kpiGroups: { title: string; grid: string; kpis: Kpi[] }[] = [
  {
    title: "Faculty evaluation",
    grid: "sm:grid-cols-2",
    kpis: [
      {
        label: "Evaluation Eligibility Accuracy",
        value: "96%",
        note: "Faculty correctly identified",
      },
      {
        label: "Overdue Evaluations",
        value: "8",
        note: "Requires committee attention",
        alert: true,
        action: {
          label: "View All",
          ariaLabel: "View all overdue evaluations",
          to: "/ac/evaluations",
        },
      },
    ],
  },
  {
    title: "Assessment participation",
    grid: "sm:grid-cols-2 xl:max-w-160",
    kpis: [
      {
        label: "Participation Rate",
        value: "82%",
        note: "# prof signed up / # of prof due",
      },
      {
        label: "Observer Utilization",
        value: "74%",
        note: "# of observers / # of prof signed up",
      },
    ],
  },
  {
    title: "Recommendation engine",
    grid: "sm:grid-cols-2",
    kpis: [
      {
        label: "List Sufficiency Rate",
        value: "91%",
        note: "Avg # of observer matches / # sign-ups",
      },
      {
        label: "Unmatched Faculty",
        value: "2",
        note: "Need manual attention",
        alert: true,
      },
    ],
  },
  {
    title: "Survey & observation completion",
    grid: "sm:grid-cols-[6fr_7fr]",
    kpis: [
      {
        label: "Survey Completion Rate",
        value: "78%",
        note: "Process feedback received",
      },
      {
        label: "Missing Observations",
        value: "4",
        note: "Approved observations that are not completed",
        alert: true,
      },
    ],
  },
];

const facultyDue = [
  {
    name: "Professor 1",
    rank: "Assistant Professor",
    term: "Fall 2026",
    dueNow: true,
  },
  { name: "Professor 2", rank: "Associate Professor", term: "Spring 2027" },
  { name: "Professor 3", rank: "Full Professor", term: "Spring 2027" },
];

const unmatchedFaculty = [
  { name: "Professor 4", department: "Computer Science", candidates: 0 },
  { name: "Professor 5", department: "Computer Science", candidates: 0 },
];

const deadlines = [
  "Sign-up Deadline: Sep 18",
  "Observer Selection Deadline: Sep 25",
  "Observation Cycle Deadline: Nov 13",
  "Feedback/Survey Deadline: Dec 1",
];

function ListPanel({
  title,
  subtitle,
  action,
  children,
}: {
  title: string;
  subtitle: string;
  action: ReactNode;
  children: ReactNode;
}) {
  return (
    <section className="flex flex-col rounded-lg border border-line bg-white px-5.5 pt-5 pb-6">
      <h2 className="font-medium text-xl">{title}</h2>
      <p className="mt-1.5 text-muted text-sm">{subtitle}</p>
      <ul className="mt-8 flex flex-col gap-8">{children}</ul>
      <div className="mt-auto flex justify-end pt-10">{action}</div>
    </section>
  );
}

function ListRow({ cells, status }: { cells: string[]; status: ReactNode }) {
  return (
    <li className="grid grid-cols-[minmax(0,1fr)_144px] items-center gap-4">
      <span className="flex flex-wrap gap-x-6">
        {cells.map((cell) => (
          <span key={cell}>{cell}</span>
        ))}
      </span>
      {status}
    </li>
  );
}

export function AcOverview() {
  return (
    <div>
      <PageHeader title="Assessment Committee Dashboard" subtitle="Fall 2026" />

      <div className="mt-7.5 grid gap-x-10 gap-y-8.5 xl:grid-cols-[minmax(0,40rem)_minmax(0,1fr)]">
        {kpiGroups.map((group) => (
          <section key={group.title}>
            <h2 className="mb-1.5 font-semibold text-[15px] text-brand-green">
              {group.title}
            </h2>
            <div className={`grid gap-4 ${group.grid}`}>
              {group.kpis.map((kpi) => (
                <KpiCard key={kpi.label} kpi={kpi} />
              ))}
            </div>
          </section>
        ))}
      </div>

      <div className="mt-10 grid gap-5 lg:grid-cols-2">
        <ListPanel
          title="Faculty Due for Evaluation"
          subtitle="Upcoming evaluation deadlines"
          action={
            <ActionButton
              tone="green"
              to="/ac/evaluations"
              ariaLabel="View all faculty due for evaluation"
              className="min-w-39.5"
            >
              View All
            </ActionButton>
          }
        >
          {facultyDue.map((item) => (
            <ListRow
              key={item.name}
              cells={[item.name, item.rank]}
              status={
                <span
                  className={
                    item.dueNow ? "text-brand-orange" : "text-brand-green"
                  }
                >
                  {item.term}
                </span>
              }
            />
          ))}
        </ListPanel>

        <ListPanel
          title="Unmatched Faculty"
          subtitle="No eligible observers or insufficient candidates"
          action={
            <ActionButton tone="orange" to="/ac/matching" className="min-w-47">
              Resolve Matches
            </ActionButton>
          }
        >
          {unmatchedFaculty.map((item) => (
            <ListRow
              key={item.name}
              cells={[item.name, item.department]}
              status={
                <span className="font-semibold text-brand-red text-sm">
                  {item.candidates}{" "}
                  {item.candidates === 1 ? "candidate" : "candidates"}
                </span>
              }
            />
          ))}
        </ListPanel>
      </div>

      <section className="mt-7 rounded-lg border border-line bg-mist px-5.5 pt-5 pb-14">
        <h2 className="font-semibold text-brand-green">Fall 2026 deadlines</h2>
        {/* One bulleted line when it fits, otherwise one deadline per line. */}
        <ul className="mt-4 flex flex-col gap-x-6 gap-y-2 min-[1360px]:flex-row">
          {deadlines.map((deadline, i) => (
            <li key={deadline} className="flex gap-x-6">
              {i > 0 && (
                <span aria-hidden="true" className="hidden min-[1360px]:inline">
                  •
                </span>
              )}
              {deadline}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
