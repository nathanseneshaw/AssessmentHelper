import { Link } from "react-router-dom";

export interface Kpi {
  label: string;
  value: string;
  note: string;
  columns?: { label: string; value: string }[];
  /** Shows the value in orange, for numbers that need the committee's attention. */
  alert?: boolean;
  action?: {
    label: string;
    ariaLabel: string;
    to?: string;
    onClick?: () => void;
    disabled?: boolean;
  };
}

export function KpiCard({ kpi }: { kpi: Kpi }) {
  return (
    <div className="flex flex-col rounded-lg border border-line bg-white px-5 pt-4 pb-3.5">
      <p className="text-muted text-sm">{kpi.label}</p>
      {kpi.columns ? (
        <div className="mt-4 grid grid-cols-2 divide-x divide-line">
          {kpi.columns.map((column) => (
            <div key={column.label} className="px-2 first:pl-0 last:pr-0">
              <p className="font-semibold text-4xl tracking-tight text-brand-green">
                {column.value}
              </p>
              <p className="mt-1 text-[12.5px] text-muted">{column.label}</p>
            </div>
          ))}
        </div>
      ) : (
        <p
          className={`mt-4 font-semibold text-4xl tracking-tight ${
            kpi.alert ? "text-brand-orange" : "text-brand-green"
          }`}
        >
          {kpi.value}
        </p>
      )}
      <div className="mt-0.5 flex min-h-7.5 items-center justify-between gap-x-2">
        <p className="min-w-0 text-[12.5px] text-muted">{kpi.note}</p>
        {kpi.action?.to ? (
          <Link
            to={kpi.action.to}
            aria-label={kpi.action.ariaLabel}
            className="shrink-0 rounded-md bg-brand-orange px-4 py-1.25 text-sm text-white hover:opacity-90"
          >
            {kpi.action.label}
          </Link>
        ) : kpi.action ? (
          <button
            type="button"
            onClick={kpi.action.onClick}
            disabled={kpi.action.disabled}
            aria-label={kpi.action.ariaLabel}
            className="shrink-0 rounded-md bg-brand-orange px-4 py-1.25 text-sm text-white hover:opacity-90 disabled:opacity-50"
          >
            {kpi.action.label}
          </button>
        ) : null}
      </div>
    </div>
  );
}
