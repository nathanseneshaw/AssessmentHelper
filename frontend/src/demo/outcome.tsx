export interface Outcome {
  error: boolean;
  text: string;
}

export async function attempt(
  action: () => Promise<string>,
  fallback: string,
): Promise<Outcome> {
  try {
    return { error: false, text: await action() };
  } catch (e) {
    return { error: true, text: e instanceof Error ? e.message : fallback };
  }
}

export function OutcomeText({ outcome }: { outcome: Outcome | null }) {
  if (!outcome) return null;
  return (
    <p
      role={outcome.error ? "alert" : "status"}
      className={`mt-2 text-sm ${outcome.error ? "text-brand-red" : "text-brand-green"}`}
    >
      {outcome.text}
    </p>
  );
}
