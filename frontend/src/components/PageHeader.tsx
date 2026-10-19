import type { ReactNode } from "react";
import { NavLink } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { NAV_ITEMS } from "../navConfig";

export function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  /** Shown at the top right, level with the title. */
  action?: ReactNode;
}) {
  const { user } = useAuth();
  const tabs = user ? NAV_ITEMS[user.role] : [];

  return (
    <header>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="font-semibold text-[38px] text-ink leading-tight">
            {title}
          </h1>
          {subtitle && <p className="text-muted">{subtitle}</p>}
        </div>
        {action}
      </div>

      {tabs.length > 0 && (
        <nav
          aria-label="Main"
          className="mt-6 flex flex-wrap gap-x-8 gap-y-2 border-line border-b pb-2.5 text-[15px]"
        >
          {tabs.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              end={tab.end}
              className={({ isActive }) =>
                isActive ? "text-brand-orange" : "text-muted hover:text-ink"
              }
            >
              {tab.label}
            </NavLink>
          ))}
        </nav>
      )}
    </header>
  );
}
