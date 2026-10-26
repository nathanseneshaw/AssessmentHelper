import { Fragment } from "react";
import { Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { BREADCRUMB } from "../navConfig";

export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  if (!user) {
    return null;
  }

  const handleLogout = async () => {
    await logout();
    navigate("/login", { replace: true });
  };

  return (
    <div className="min-h-screen bg-cream font-inter text-ink">
      <div className="flex h-16 items-center justify-between border-line border-b px-5 text-[15px] text-muted sm:px-10">
        <span className="flex gap-3">
          {BREADCRUMB[user.role].map((crumb, i) => (
            <Fragment key={crumb}>
              {i > 0 && <span aria-hidden="true">/</span>}
              <span>{crumb}</span>
            </Fragment>
          ))}
        </span>
        <div className="flex items-center gap-4">
          <span>{user.name}</span>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-md border border-line bg-white px-3 py-1 text-ink text-sm hover:bg-mist"
          >
            Log out
          </button>
        </div>
      </div>

      {/* AC pages render their own tabs under the title, via PageHeader. */}
      <main className="px-5 pt-9 pb-10 sm:px-10">
        <Outlet />
      </main>
    </div>
  );
}
