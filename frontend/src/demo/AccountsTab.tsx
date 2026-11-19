import { useId, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { DemoAccount } from "../api/demo";
import { useAuth } from "../auth/AuthContext";
import { HOME_PATH } from "../navConfig";

export function AccountsTab({
  accounts,
  onSwitched,
}: {
  accounts: DemoAccount[];
  onSwitched: () => void;
}) {
  const { user, switchAccount } = useAuth();
  const navigate = useNavigate();
  const searchId = useId();
  const [query, setQuery] = useState("");
  const [switching, setSwitching] = useState<number | null>(null);
  const [error, setError] = useState("");

  const search = query.trim().toLowerCase();
  const shown = accounts
    .filter((account) =>
      `${account.name} ${account.email} ${account.department}`
        .toLowerCase()
        .includes(search),
    )
    .sort((a, b) => Number(b.committee) - Number(a.committee));

  async function choose(account: DemoAccount) {
    setSwitching(account.userId);
    setError("");
    try {
      const next = await switchAccount(account.userId);
      navigate(HOME_PATH[next.role], { replace: true });
      onSwitched();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not switch accounts.");
    } finally {
      setSwitching(null);
    }
  }

  return (
    <div>
      <label htmlFor={searchId} className="block font-semibold text-sm">
        {user ? "Switch to" : "Sign in as"}
      </label>
      <input
        id={searchId}
        type="search"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Name, email or department"
        className="mt-1 w-full rounded-lg border border-line px-3 py-2 text-sm"
      />
      {error && (
        <p role="alert" className="mt-2 text-brand-red text-sm">
          {error}
        </p>
      )}
      <ul className="mt-2 divide-y divide-line">
        {shown.map((account) => {
          const current = account.userId === user?.userId;
          return (
            <li key={account.userId}>
              <button
                type="button"
                disabled={current || switching !== null}
                onClick={() => choose(account)}
                className="flex w-full items-center justify-between gap-3 rounded-md px-2 py-2 text-left enabled:hover:bg-mist"
              >
                <span className="min-w-0">
                  <span className="block font-semibold text-sm">
                    {account.name}
                  </span>
                  <span className="block truncate text-muted text-xs">
                    {account.department} · {account.email}
                  </span>
                </span>
                {current ? (
                  <span className="shrink-0 text-muted text-xs">Signed in</span>
                ) : (
                  account.committee && (
                    <span className="shrink-0 rounded-full bg-mist px-2 py-0.5 font-semibold text-brand-green text-xs">
                      AC
                    </span>
                  )
                )}
              </button>
            </li>
          );
        })}
      </ul>
      {shown.length === 0 && (
        <p className="mt-2 text-muted text-sm">No accounts match.</p>
      )}
    </div>
  );
}
