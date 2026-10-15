import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { BREADCRUMB, NAV_ITEMS } from "../navConfig";

export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  if (!user) {
    return null;
  }

  const tabs = NAV_ITEMS[user.role];

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <div className="min-h-screen">
      <div className="flex items-center justify-between border-line border-b px-7 py-3 text-sm text-ink/70">
        <span>{BREADCRUMB[user.role]}</span>
        <div className="flex items-center gap-4">
          <span>{user.name}</span>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-md border border-line bg-white px-3 py-1 text-ink hover:bg-mist"
          >
            Log out
          </button>
        </div>
      </div>

      {tabs.length > 0 && (
        <nav
          aria-label="Main"
          className="flex flex-wrap gap-x-6 border-line border-b px-7 pt-4"
        >
          {tabs.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              end={tab.end}
              className={({ isActive }) =>
                `-mb-px border-b-2 pb-2 text-sm ${
                  isActive
                    ? "border-brand-orange font-semibold text-brand-orange"
                    : "border-transparent text-ink/70 hover:text-ink"
                }`
              }
            >
              {tab.label}
            </NavLink>
          ))}
        </nav>
      )}

      <main className="px-7 py-6">
        <Outlet />
      </main>
    </div>
  );
}
