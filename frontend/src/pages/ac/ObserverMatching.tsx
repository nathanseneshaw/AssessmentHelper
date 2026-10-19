import { useState } from "react";
import { ActionButton } from "../../components/ActionButton";
import { PageHeader } from "../../components/PageHeader";

interface Observer {
  name: string;
  department: string;
  level: string;
}

interface Observee {
  name: string;
  section: string;
  meets: string;
  rank: string;
  /** System-generated candidates: at most 5, and empty when none qualify. */
  eligible: Observer[];
}

const observees: [Observee, ...Observee[]] = [
  {
    name: "Professor 1",
    section: "CS 3345.002",
    meets: "TR 1:00–2:15",
    rank: "Assistant Professor",
    eligible: ["Professor 3", "Professor 11", "Professor 12"].map((name) => ({
      name,
      department: "CS",
      level: "3000-level",
    })),
  },
  {
    name: "Professor 2",
    section: "CS 4348.501",
    meets: "MW 10:00–11:15",
    rank: "Associate Professor",
    eligible: [
      "Professor 6",
      "Professor 7",
      "Professor 8",
      "Professor 9",
      "Professor 10",
    ].map((name) => ({ name, department: "CS", level: "4000-level" })),
  },
  {
    name: "Professor 4",
    section: "CS 4337.001",
    meets: "TR 4:00–5:15",
    rank: "Associate Professor",
    eligible: [],
  },
];

// The mockup opens on Professor 2.
const FIRST_OBSERVEE = 1;

const rules = [
  "Same department",
  "Same course level",
  "Available at preferred time",
  "Observee cannot match themself",
];

const ruleNotes = [
  "If 5+ qualify, return 5 candidates.",
  "If fewer than 5 qualify, display all.",
  "If none qualify, alert the AC.",
];

const ARROW_PATHS = {
  previous: "M37 14.5H3M16 1.5l-13 13 13 13",
  next: "M0 14.5h34M21 1.5l13 13-13 13",
};

function ArrowButton({
  direction,
  onClick,
}: {
  direction: keyof typeof ARROW_PATHS;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={`${direction === "previous" ? "Previous" : "Next"} observee`}
      className="grid size-12 shrink-0 place-items-center rounded-lg text-brand-green hover:bg-mist"
    >
      <svg
        aria-hidden="true"
        width="37"
        height="29"
        viewBox="0 0 37 29"
        fill="none"
        stroke="currentColor"
        strokeWidth="3.5"
      >
        <path d={ARROW_PATHS[direction]} />
      </svg>
    </button>
  );
}

function ObserverRow({ observer }: { observer: Observer }) {
  return (
    // Two lines in a narrow card; the mockup's three columns once it is wide enough.
    <li className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 @2xl:grid-cols-[minmax(0,236px)_minmax(0,359px)_auto]">
      <span className="font-semibold">{observer.name}</span>
      <span className="text-[15px] text-muted">
        {observer.department} • {observer.level}
      </span>
      {/* Only available faculty are eligible, so every candidate is available. */}
      <span className="col-start-2 row-start-1 font-semibold text-brand-green text-sm @2xl:col-start-3">
        Available
      </span>
    </li>
  );
}

export function AcObserverMatching() {
  const [index, setIndex] = useState(FIRST_OBSERVEE);
  const observee = observees[index] ?? observees[0];
  const count = observee.eligible.length;

  // The arrows wrap around at either end of the list.
  const step = (by: number) =>
    setIndex((i) => (i + by + observees.length) % observees.length);

  return (
    <div>
      <PageHeader
        title="Observer Matching"
        subtitle="Review system-generated eligible observers and resolve insufficient matches."
        action={
          <ActionButton tone="orange" className="min-w-52.5">
            Generate Matches
          </ActionButton>
        }
      />

      <div className="mt-7">
        <div className="flex items-center gap-3 sm:gap-5">
          <ArrowButton direction="previous" onClick={() => step(-1)} />
          <h2 className="font-medium text-xl sm:text-[26px]">
            Observee: {observee.name}
          </h2>
          <ArrowButton direction="next" onClick={() => step(1)} />
        </div>
        {/* One bulleted line from sm up, otherwise one detail per line. */}
        <p className="flex flex-col text-muted sm:flex-row sm:flex-wrap sm:gap-x-2.5">
          <span>{observee.section}</span>
          <span aria-hidden="true" className="hidden sm:inline">
            •
          </span>
          <span>{observee.meets}</span>
          <span aria-hidden="true" className="hidden sm:inline">
            •
          </span>
          <span>{observee.rank}</span>
        </p>
        {/* Tells screen reader users what changed after an arrow press. */}
        <p role="status" className="sr-only">
          {`Observee ${index + 1} of ${observees.length}: ${observee.name}, ${
            count === 0 ? "no" : count
          } eligible ${count === 1 ? "observer" : "observers"}.`}
        </p>
      </div>

      {/* Figma gives both panels the same fixed height. */}
      <div className="mt-8.5 grid gap-6 lg:min-h-177.75 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] lg:gap-10">
        <section className="@container rounded-lg border border-line bg-white px-5.5 pt-4.5 pb-8">
          <h2 className="font-medium text-xl">Eligible observers</h2>
          <p className="mt-1.5 text-muted text-sm">
            Same department + same course level + available at requested time
          </p>
          {count > 0 ? (
            <ul className="mt-10.25 flex flex-col gap-8 lg:gap-19.5">
              {observee.eligible.map((observer) => (
                <ObserverRow key={observer.name} observer={observer} />
              ))}
            </ul>
          ) : (
            <p className="mt-10.25 font-semibold text-brand-red">
              No eligible observers. Match this observee manually.
            </p>
          )}
        </section>

        <section className="flex flex-col rounded-lg border border-line bg-mist px-6 pt-5.5 pb-6 lg:pb-18.5">
          <h2 className="font-semibold text-brand-green text-xl">
            Eligibility rules
          </h2>
          <ol className="mt-7.25 list-inside list-decimal space-y-6">
            {rules.map((rule) => (
              <li key={rule}>{rule}</li>
            ))}
          </ol>
          <ul className="mt-11 text-[15px] text-muted leading-4.75">
            {ruleNotes.map((note) => (
              <li key={note}>{note}</li>
            ))}
          </ul>
          <div className="mt-auto pt-10">
            <ActionButton tone="orange" className="min-w-62.5">
              Send Candidate List
            </ActionButton>
          </div>
        </section>
      </div>
    </div>
  );
}
