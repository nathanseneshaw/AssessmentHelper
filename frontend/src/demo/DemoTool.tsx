import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { type DemoAccount, getDemoAccounts, resetDemo } from "../api/demo";
import { useAuth } from "../auth/AuthContext";
import { AccountsTab } from "./AccountsTab";
import { AddDataTab } from "./AddDataTab";
import { DateTab } from "./DateTab";
import { EvaluationsTab } from "./EvaluationsTab";
import { NotesTab } from "./NotesTab";
import { attempt, type Outcome, OutcomeText } from "./outcome";

const HIDDEN_KEY = "demoToolHidden";

const TABS = [
  { id: "accounts", label: "Accounts" },
  { id: "data", label: "Add data" },
  { id: "evaluations", label: "Evaluations" },
  { id: "date", label: "Date" },
  { id: "notes", label: "Notes" },
] as const;

type Tab = (typeof TABS)[number]["id"];

function rememberHidden(hidden: boolean) {
  sessionStorage.setItem(HIDDEN_KEY, String(hidden));
}

function isToggleShortcut(e: KeyboardEvent) {
  return (
    e.altKey && e.shiftKey && !e.ctrlKey && !e.metaKey && e.code === "KeyD"
  );
}

export function DemoTool({
  onDataChanged = () => undefined,
}: {
  onDataChanged?: () => void;
}) {
  const { user, logout } = useAuth();
  const [accounts, setAccounts] = useState<DemoAccount[] | null>(null);
  const [hidden, setHidden] = useState(
    () => sessionStorage.getItem(HIDDEN_KEY) === "true",
  );
  const [open, setOpen] = useState(false);
  const [tab, setTab] = useState<Tab>("accounts");
  const [confirmingReset, setConfirmingReset] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [notice, setNotice] = useState<Outcome | null>(null);
  const tabs = user ? TABS : TABS.filter(({ id }) => id === "accounts");
  const { pathname } = useLocation();

  // biome-ignore lint/correctness/useExhaustiveDependencies: boxes drawn on the Notes tab belong to one page and account.
  useEffect(() => {
    window.screenshotMarks?.clearAreas();
  }, [pathname, user?.userId]);

  // The backend only serves the demo endpoints when its own demo setting is on.
  useEffect(() => {
    const controller = new AbortController();
    getDemoAccounts(controller.signal).then(setAccounts, () =>
      setAccounts(null),
    );
    return () => controller.abort();
  }, []);

  useEffect(() => {
    if (!accounts) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (!isToggleShortcut(e)) return;
      e.preventDefault();
      rememberHidden(!hidden);
      setHidden(!hidden);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [accounts, hidden]);

  if (!accounts || hidden) return null;

  async function reset() {
    setResetting(true);
    const outcome = await attempt(async () => {
      const { signedOut } = await resetDemo();
      setAccounts(await getDemoAccounts());
      if (signedOut) await logout();
      else onDataChanged();
      return "Back to the starting data and today's date.";
    }, "Could not reset the demo.");
    setResetting(false);
    setConfirmingReset(false);
    setNotice(outcome);
  }

  const hide = () => {
    rememberHidden(true);
    setHidden(true);
  };

  return (
    <div className="font-inter text-ink">
      {open && (
        <section
          aria-label="Demo tool"
          className="fixed right-4 bottom-18 z-40 flex max-h-[calc(100dvh-9rem)] w-[min(24rem,calc(100vw-2rem))] flex-col rounded-xl border border-line bg-white shadow-xl"
        >
          <header className="flex items-center justify-between gap-3 border-line border-b px-4 py-3">
            {confirmingReset ? (
              <>
                <h2 className="font-semibold text-sm">
                  Undo every demo change?
                </h2>
                <span className="flex shrink-0 gap-2">
                  <button
                    type="button"
                    disabled={resetting}
                    onClick={reset}
                    className="rounded-md bg-brand-red px-2.5 py-1 font-semibold text-sm text-white hover:opacity-90 disabled:opacity-50"
                  >
                    {resetting ? "Resetting…" : "Reset"}
                  </button>
                  <button
                    type="button"
                    disabled={resetting}
                    onClick={() => setConfirmingReset(false)}
                    className="rounded-md border border-line px-2.5 py-1 text-sm hover:bg-mist"
                  >
                    Cancel
                  </button>
                </span>
              </>
            ) : (
              <>
                <h2 className="font-bold">Demo tool</h2>
                <span className="flex shrink-0 gap-2">
                  {user && (
                    <button
                      type="button"
                      onClick={() => setConfirmingReset(true)}
                      className="rounded-md border border-line px-2.5 py-1 text-sm hover:bg-mist"
                    >
                      Reset
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={hide}
                    title="Alt+Shift+D brings it back"
                    className="rounded-md border border-line px-2.5 py-1 text-sm hover:bg-mist"
                  >
                    Hide
                  </button>
                </span>
              </>
            )}
          </header>
          {notice && (
            <div className="border-line border-b px-4 pb-2">
              <OutcomeText outcome={notice} />
            </div>
          )}
          <div
            role="tablist"
            className="flex gap-1 overflow-x-auto border-line border-b px-2"
          >
            {tabs.map(({ id, label }) => (
              <button
                key={id}
                type="button"
                role="tab"
                aria-selected={tab === id}
                onClick={() => {
                  setTab(id);
                  setNotice(null);
                }}
                className="whitespace-nowrap border-transparent border-b-2 px-1.5 py-2 text-muted text-sm aria-selected:border-brand-green aria-selected:font-semibold aria-selected:text-ink"
              >
                {label}
              </button>
            ))}
          </div>
          <div role="tabpanel" className="min-h-0 flex-1 overflow-y-auto p-4">
            {tab === "accounts" && (
              <AccountsTab
                accounts={accounts}
                onSwitched={() => setOpen(false)}
              />
            )}
            {tab === "data" && (
              <AddDataTab
                accounts={accounts}
                onAccountAdded={(account) =>
                  setAccounts([...accounts, account])
                }
                onDataChanged={onDataChanged}
              />
            )}
            {tab === "evaluations" && (
              <EvaluationsTab onDataChanged={onDataChanged} />
            )}
            {tab === "date" && <DateTab onDataChanged={onDataChanged} />}
            {tab === "notes" && <NotesTab />}
          </div>
        </section>
      )}
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
        className="fixed right-4 bottom-4 z-40 rounded-full bg-ink px-5 py-2.5 font-semibold text-sm text-white shadow-lg hover:opacity-90"
      >
        Demo
      </button>
    </div>
  );
}
