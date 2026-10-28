import {
  type MouseEvent,
  type ReactNode,
  useEffect,
  useId,
  useRef,
  useState,
} from "react";
import { Link, useNavigate } from "react-router-dom";

export function Modal({
  title,
  subtitle,
  wide = false,
  leaveWarning,
  children,
}: {
  title: string;
  subtitle?: string;
  /** A wider dialog, for long forms and records. */
  wide?: boolean;
  /**
   * While set, Escape, Close or a click outside first asks, with this message, whether to leave. For example
   * while a form has unsaved changes.
   */
  leaveWarning?: string;
  children?: ReactNode;
}) {
  const navigate = useNavigate();
  const ids = useId();
  const [asking, setAsking] = useState(false);
  const stay = useRef<HTMLButtonElement>(null);
  /** Where focus was before the question, so Keep editing puts it back. */
  const returnFocus = useRef<HTMLElement | null>(null);
  // The question goes away once there is nothing left to lose, for example when a save finishes behind it.
  if (asking && leaveWarning === undefined) setAsking(false);

  // Focus can only go back once the dialog behind the question is no longer inert.
  useEffect(() => {
    if (asking) {
      stay.current?.focus();
    } else if (returnFocus.current) {
      returnFocus.current.focus();
      returnFocus.current = null;
    }
  }, [asking]);

  const keepOpen = () => setAsking(false);

  /** Asks first while there is a warning, and says whether the dialog may close now. */
  function mayClose() {
    if (leaveWarning === undefined) return true;
    if (!asking && document.activeElement instanceof HTMLElement) {
      returnFocus.current = document.activeElement;
    }
    setAsking(true);
    return false;
  }

  // Subscribed again after every render, so the handler sees the current question and warning.
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      // While an input method is composing text, Escape cancels the composition, not the dialog.
      if (e.key !== "Escape" || e.isComposing) return;
      // Escape answers the question the way Keep editing does.
      if (asking) keepOpen();
      else if (mayClose()) navigate("/faculty");
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  });

  // A link only navigates when its click is not prevented.
  const onCloseClick = (e: MouseEvent) => {
    if (!mayClose()) e.preventDefault();
  };

  return (
    <div className="fixed inset-0 z-20 flex items-center justify-center p-4">
      <Link
        to="/faculty"
        aria-label="Close"
        onClick={onCloseClick}
        className="absolute inset-0 bg-ink/40"
      />
      <dialog
        open
        aria-labelledby="modal-title"
        // While the question is up, the dialog behind it can't be used or reached with Tab.
        inert={asking}
        className={`relative m-0 max-h-[calc(100dvh-2rem)] w-full ${wide ? "max-w-3xl" : "max-w-xl"} overflow-y-auto rounded-xl border border-line bg-white p-6 text-ink shadow-xl`}
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="modal-title" className="font-bold text-xl">
              {title}
            </h2>
            {subtitle && <p className="mt-1 text-ink/60 text-sm">{subtitle}</p>}
          </div>
          <Link
            to="/faculty"
            onClick={onCloseClick}
            className="rounded-md border border-line px-3 py-1 text-sm hover:bg-mist"
          >
            Close
          </Link>
        </div>
        <div className="mt-5">{children}</div>
      </dialog>
      {asking && (
        <div className="absolute inset-0 flex items-center justify-center bg-ink/30 p-4">
          <div
            role="alertdialog"
            aria-modal="true"
            aria-labelledby={`${ids}-leave`}
            aria-describedby={`${ids}-leave-detail`}
            className="w-full max-w-sm rounded-xl border border-line bg-white p-6 text-ink shadow-xl"
          >
            <h3 id={`${ids}-leave`} className="font-bold text-lg">
              Leave without saving?
            </h3>
            <p id={`${ids}-leave-detail`} className="mt-2 text-sm">
              {leaveWarning}
            </p>
            <div className="mt-5 flex flex-wrap justify-end gap-3">
              <button
                ref={stay}
                type="button"
                onClick={keepOpen}
                className="rounded-lg border border-line bg-white px-4 py-2 font-semibold text-sm text-ink hover:bg-mist"
              >
                Keep editing
              </button>
              <button
                type="button"
                onClick={() => navigate("/faculty")}
                className="rounded-lg bg-brand-red px-4 py-2 font-semibold text-sm text-white hover:opacity-90"
              >
                Leave without saving
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
