import { ActionButton } from "../../components/ActionButton";
import { PageHeader } from "../../components/PageHeader";

const criteria = [
  { title: "Student Engagement", scale: "1–5", notes: true },
  { title: "Preparedness & Organization", scale: "1–5", notes: true },
  { title: "Content Presentation", scale: "1–5", notes: false },
  { title: "Rigor & Depth", scale: "1–5", notes: false },
  { title: "Pace & Time for Questions", scale: "1–5", notes: true },
];

const settings = [
  { label: "Rating scale", value: "1–5" },
  { label: "Notes", value: "Optional" },
];

function CriterionCard({
  title,
  scale,
  notes,
}: {
  title: string;
  scale: string;
  notes: boolean;
}) {
  return (
    <li className="rounded-lg border border-line bg-white px-4 pt-3.5 pb-5">
      <h3 className="font-semibold">{title}</h3>
      <p className="mt-2.5 flex flex-wrap gap-x-2 text-muted text-sm">
        <span>Rating scale: {scale}</span>
        <span aria-hidden="true">•</span>
        <span>Notes {notes ? "enabled" : "disabled"}</span>
      </p>
    </li>
  );
}

export function AcFormTemplate() {
  return (
    <div>
      <PageHeader
        title="Observation Form Template"
        subtitle="Configure evaluation criteria, rating ranges, question types, and weights."
        action={
          <ActionButton tone="orange" className="min-w-52.5">
            Save Template
          </ActionButton>
        }
      />

      <div className="mt-10 grid gap-10 xl:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <section className="flex flex-col rounded-lg border border-line bg-white px-6 pt-5 pb-6">
          <h2 className="font-semibold text-[21px] leading-7">
            Observation criteria
          </h2>
          <p className="mt-1.5 text-muted text-sm">
            AC members can update the template used for future observations.
          </p>
          {/* The Figma cards stop 40px short of the panel's right edge. */}
          <ul className="mt-10 flex flex-col gap-7 sm:mr-4">
            {criteria.map((criterion) => (
              <CriterionCard key={criterion.title} {...criterion} />
            ))}
          </ul>
          <div className="mt-15">
            <ActionButton tone="green" className="min-w-50">
              Add/Edit Criterion
            </ActionButton>
          </div>
        </section>

        <section className="flex flex-col rounded-lg border border-line bg-mist px-6 pt-5 pb-9">
          <h2 className="font-semibold text-brand-green text-xl">
            Template settings
          </h2>
          <dl className="mt-9 grid grid-cols-[auto_1fr] gap-x-6 gap-y-7">
            {settings.map((setting) => (
              <div key={setting.label} className="contents">
                <dt>{setting.label}</dt>
                <dd>{setting.value}</dd>
              </div>
            ))}
          </dl>
          <p className="mt-5 text-[15px] text-muted leading-4.5">
            Signed observations are locked
            <br />
            and cannot be edited after completion.
          </p>
          {/* Pinned to the bottom when the panel sits beside the criteria. */}
          <div className="mt-auto pt-10">
            <ActionButton tone="green" className="min-w-50">
              Preview Form
            </ActionButton>
          </div>
        </section>
      </div>
    </div>
  );
}
