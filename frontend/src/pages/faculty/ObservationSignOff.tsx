import { useEffect, useId, useRef, useState } from "react";
import {
  isConflict,
  type ObservationDetail,
  signObservation,
} from "../../api/observations";
import { observerChanged } from "../../api/observerEligibility";
import { CommentsBox } from "./ObservationForm";

const primaryButton =
  "rounded-lg bg-brand-green px-4 py-2 font-semibold text-sm text-white hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50";
const secondaryButton =
  "rounded-lg border border-line bg-white px-4 py-2 font-semibold text-sm text-ink hover:bg-mist disabled:cursor-not-allowed disabled:opacity-50";

export function ObservationSignOff({
  observation,
  onSigned,
  onDirtyChange,
  onStale,
}: {
  observation: ObservationDetail;
  onSigned: (observation: ObservationDetail) => void;
  onDirtyChange?: (dirty: boolean) => void;
  onStale?: () => void;
}) {
  const ids = useId();
  const [comments, setComments] = useState("");
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const confirmHeading = useRef<HTMLHeadingElement>(null);
  const signButton = useRef<HTMLButtonElement>(null);
  const returnFocus = useRef(false);
  const dirty = comments.trim() !== "";

  useEffect(() => {
    onDirtyChange?.(dirty);
    return () => onDirtyChange?.(false);
  }, [dirty, onDirtyChange]);

  useEffect(() => {
    if (confirming) {
      confirmHeading.current?.focus();
    } else if (returnFocus.current) {
      returnFocus.current = false;
      signButton.current?.focus();
    }
  }, [confirming]);

  async function sign() {
    setPending(true);
    setError("");
    try {
      const signed = await signObservation(
        observation.observationId,
        comments.trim() || null,
      );
      onSigned(signed);
      // The dashboard behind the dialog refreshes its observations.
      observerChanged();
    } catch (cause) {
      setError(
        `Your sign-off was not saved. ${cause instanceof Error ? cause.message : "Please try again."}`,
      );
      if (isConflict(cause)) onStale?.();
    } finally {
      setPending(false);
    }
  }

  return (
    <section
      aria-labelledby={`${ids}-heading`}
      className="mt-8 border-line border-t pt-6 text-ink"
    >
      <h2 id={`${ids}-heading`} className="font-semibold text-xl">
        Sign off
      </h2>
      <p className="mt-1 text-muted text-sm">
        Read {observation.observer.name}'s responses above. You can add your own
        comments to the record, then sign off to complete it.
      </p>
      <label
        htmlFor={`${ids}-comments`}
        className="mt-4 block font-medium text-sm"
      >
        Your comments <span className="font-normal text-muted">(optional)</span>
      </label>
      <CommentsBox
        id={`${ids}-comments`}
        value={comments}
        rows={4}
        onChange={setComments}
      />
      {error && (
        <p role="alert" className="mt-3 text-brand-red text-sm">
          {error} Your comments are still here.
        </p>
      )}
      {confirming ? (
        <section
          aria-labelledby={`${ids}-confirm`}
          className="mt-4 rounded-lg border border-line bg-mist px-4 py-4"
        >
          <h3
            id={`${ids}-confirm`}
            ref={confirmHeading}
            tabIndex={-1}
            className="font-semibold"
          >
            Sign off on this observation
          </h3>
          <p className="mt-1 text-sm">
            After you sign off, the record is locked. Nobody can change the
            responses or your comments.
          </p>
          <div className="mt-4 flex flex-wrap justify-end gap-3">
            <button
              type="button"
              className={secondaryButton}
              disabled={pending}
              onClick={() => {
                returnFocus.current = true;
                setConfirming(false);
              }}
            >
              Go back
            </button>
            <button
              type="button"
              className={primaryButton}
              disabled={pending}
              onClick={sign}
            >
              {pending ? "Signing off…" : "Sign off"}
            </button>
          </div>
        </section>
      ) : (
        <div className="mt-4 flex justify-end">
          <button
            ref={signButton}
            type="button"
            className={primaryButton}
            onClick={() => {
              setError("");
              setConfirming(true);
            }}
          >
            Sign off
          </button>
        </div>
      )}
    </section>
  );
}
