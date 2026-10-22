import {
  type AcState,
  schedule,
  useObserverData,
} from "../../api/observerEligibility";
import { ActionButton } from "../../components/ActionButton";
import { type Kpi, KpiCard } from "../../components/KpiCard";
import { PageHeader } from "../../components/PageHeader";

const kpis: Kpi[] = [
  { label: "Due Faculty", value: "18", note: "Current cycle" },
  { label: "Signed Up", value: "15", note: "83% participation" },
  {
    label: "Not Yet Signed Up",
    value: "3",
    note: "Reminder recommended",
    alert: true,
    action: {
      label: "View All",
      ariaLabel: "View all faculty not yet signed up",
      to: "/ac/evaluations",
    },
  },
  {
    label: "Signup Deadline",
    value: "Sep 18",
    note: "Fall 2026 cycle",
    alert: true,
  },
];

const columns = ["Observee", "Course / Section", "Meeting Time", "Status"];

export function AcSignups() {
  const { data, error } = useObserverData<AcState>("/ac");
  const signupRequests = (data?.signups ?? []).map((s) => ({
    signupId: s.signupId,
    observee: s.name,
    section: s.section.label,
    meetingTime: schedule(s.section),
    status: s.released ? "List sent" : "Awaiting review",
  }));
  return (
    <div>
      <PageHeader
        title="Evaluation Signups"
        subtitle="Review course selections and preferred observation times submitted by faculty."
      />

      {/* Cards keep the Overview's KPI width rather than stretching across the page. */}
      {error && (
        <p role="alert" className="mt-4 text-brand-red">
          {error}
        </p>
      )}
      <div className="mt-10.25 grid gap-4 sm:grid-cols-2 xl:max-w-324 xl:grid-cols-4">
        {kpis.map((kpi) => (
          <KpiCard
            key={kpi.label}
            kpi={
              kpi.label === "Signed Up"
                ? {
                    ...kpi,
                    value: data ? String(signupRequests.length) : "Loading",
                    note: "Current cycle",
                  }
                : kpi.label === "Signup Deadline"
                  ? { ...kpi, value: data?.cycle?.signupDeadline ?? "Loading" }
                  : kpi
            }
          />
        ))}
      </div>

      <section className="mt-9.5 rounded-lg border border-line bg-white px-5.5 pt-5 pb-5">
        <h2 className="font-semibold text-[21px]/7">Signup Requests</h2>
        {!data && !error && <p>Loading signups...</p>}
        {data && signupRequests.length === 0 && <p>No signups yet.</p>}
        {/* On narrow screens the table scrolls inside the card. `relative` keeps
            the sr-only header inside the scroller so the page does not widen. */}
        <div className="relative mt-6.75 overflow-x-auto">
          <table className="w-full min-w-200">
            <thead>
              <tr className="text-muted text-sm">
                {columns.map((column) => (
                  <th
                    key={column}
                    scope="col"
                    className="w-21/100 pb-7.5 text-left font-medium"
                  >
                    {column}
                  </th>
                ))}
                <th scope="col">
                  <span className="sr-only">Action</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {signupRequests.map((request) => (
                // From lg up the rows sit 131px apart, as in the mockup.
                <tr key={request.signupId} className="*:pb-8 lg:*:pb-20.75">
                  <th scope="row" className="text-left font-semibold">
                    {request.observee}
                  </th>
                  <td>{request.section}</td>
                  <td>{request.meetingTime}</td>
                  <td>{request.status}</td>
                  <td>
                    <ActionButton
                      tone="green"
                      ariaLabel={`Review signup for ${request.observee}`}
                      to={`/ac/matching?signup=${request.signupId}`}
                      className="min-w-40.5"
                    >
                      Review
                    </ActionButton>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
