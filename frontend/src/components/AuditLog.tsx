import {
  type AuditEntry,
  type AuditEvent,
  formatTimestamp,
} from "../api/observations";

const EVENT_LABELS: Record<AuditEvent, string> = {
  STARTED: "Observation started",
  EDITED: "Draft saved",
  SUBMITTED: "Submitted and signed by observer",
  OBSERVEE_COMMENTED: "Observee added comments",
  OBSERVEE_SIGNED: "Signed by observee",
  LOCKED: "Record locked",
  VIEWED: "Record viewed",
  FORWARDED: "Record forwarded",
};

function people({ actorName, recipientName }: AuditEntry) {
  if (!actorName) return null;
  return recipientName
    ? `By ${actorName} to ${recipientName}`
    : `By ${actorName}`;
}

export function AuditLog({ entries }: { entries: AuditEntry[] }) {
  if (entries.length === 0)
    return <p className="text-muted text-sm">Nothing has been recorded yet.</p>;
  return (
    <ol className="divide-y divide-line border-line border-y text-sm">
      {entries.map((entry, index) => {
        const who = people(entry);
        return (
          // biome-ignore lint/suspicious/noArrayIndexKey: The log is append-only, so an entry's position never changes.
          <li key={index} className="py-3">
            <p className="font-semibold">{EVENT_LABELS[entry.event]}</p>
            <p className="text-muted">
              {who && `${who} · `}
              <time dateTime={entry.occurredAt}>
                {formatTimestamp(entry.occurredAt)}
              </time>
            </p>
          </li>
        );
      })}
    </ol>
  );
}
