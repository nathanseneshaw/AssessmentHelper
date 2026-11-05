import { useEffect, useId, useRef } from "react";
import { Link } from "react-router-dom";
import {
  type AcState,
  matchingReason,
  unmatchedSignups,
} from "../../api/observerEligibility";

export function UnmatchedFaculty({
  data,
  error,
  onRetry,
  onClose,
}: {
  data: AcState | null;
  error: string;
  onRetry: () => void;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const faculty = data ? unmatchedSignups(data) : [];
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    dialog.current?.showModal();
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      ref={dialog}
      aria-labelledby={titleId}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="fixed inset-0 m-auto max-h-[90vh] w-[calc(100%-2rem)] max-w-3xl overflow-y-auto rounded-xl border border-line bg-white p-6 text-ink shadow-xl backdrop:bg-ink/40"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 id={titleId} className="font-bold text-xl">
            Unmatched Faculty
          </h2>
          <p className="mt-1 text-sm text-muted">
            Select an observee to review and resolve their matching case.
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="cursor-pointer rounded-md border border-line px-3 py-1 text-sm hover:bg-mist"
        >
          Close
        </button>
      </div>
      {error ? (
        <p role="alert" className="mt-5 text-brand-red">
          {error}{" "}
          <button type="button" onClick={onRetry} className="underline">
            Retry
          </button>
        </p>
      ) : !data ? (
        <p className="mt-5">Loading unmatched faculty...</p>
      ) : faculty.length === 0 ? (
        <p className="mt-5">No unmatched faculty.</p>
      ) : (
        <div className="mt-5 max-h-[65vh] overflow-auto">
          <table className="w-full text-left text-sm">
            <thead className="sticky top-0 bg-white">
              <tr>
                <th className="p-3">Faculty</th>
                <th className="p-3">Course</th>
                <th className="p-3">Reason</th>
                <th className="p-3">
                  <span className="sr-only">Action</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {faculty.map((s) => (
                <tr key={s.signupId} className="border-t border-line">
                  <td className="p-3">
                    <p className="font-semibold">{s.name}</p>
                    <p className="text-xs text-muted">{s.department}</p>
                  </td>
                  <td className="p-3">{s.section.label}</td>
                  <td className="p-3">
                    {matchingReason(
                      data.matchingCases?.find(
                        (c) => c.signupId === s.signupId,
                      ),
                    )}
                  </td>
                  <td className="p-3">
                    <Link
                      to={`/ac/matching?signup=${s.signupId}`}
                      aria-label={`Resolve ${s.name}`}
                      className="inline-block rounded-md bg-brand-green px-3 py-2 text-xs font-semibold text-white hover:opacity-90"
                    >
                      Resolve
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </dialog>
  );
}
