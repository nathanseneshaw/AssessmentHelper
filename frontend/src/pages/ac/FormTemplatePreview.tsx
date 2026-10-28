import type { FormTemplate } from "../../api/formTemplate";

const details = ["Observation Date", "Observer", "Time & Location", "Status"];
const blankField =
  "mt-1 block h-7 w-full min-w-0 border-0 bg-transparent text-xs outline-none";

/** Empty observation form using the active template's selected criteria and notes settings. */
export function FormTemplatePreview({ template }: { template: FormTemplate }) {
  const criteria = template.criteria.filter((criterion) => criterion.selected);
  return (
    <div className="px-5 pb-6 pt-4 sm:px-7">
      <div className="grid gap-3 sm:grid-cols-2">
        {["Semester", "Course", "Section", "Observee"].map((field) => (
          <label key={field} className="text-xs font-medium">
            {field}
            <input
              aria-label={field}
              readOnly
              value=""
              className="mt-1 block h-8 w-full rounded-md border border-line bg-white px-2"
            />
          </label>
        ))}
      </div>
      <div className="mt-5 grid gap-2 sm:max-w-xl sm:grid-cols-2">
        {details.map((field) => (
          <label
            key={field}
            className="rounded-lg border border-ink/50 bg-neutral-50 px-3 py-2.5 text-xs font-medium"
          >
            {field}
            <input
              aria-label={field}
              readOnly
              value=""
              className={blankField}
            />
          </label>
        ))}
      </div>

      <section
        aria-labelledby="preview-criteria-title"
        className="mt-5 border-t border-ink/50 pt-3"
      >
        <h3 id="preview-criteria-title" className="mb-2 text-sm font-semibold">
          Observation Criteria
        </h3>
        <ul className="space-y-2">
          {criteria.map((criterion) => (
            <li
              key={criterion.title}
              className="flex flex-col justify-between gap-3 rounded-lg border border-ink/40 px-3 py-3 sm:flex-row sm:items-start"
            >
              <div className="min-w-0 flex-1">
                <h4 className="break-words text-sm font-semibold">
                  {criterion.title}
                </h4>
                {criterion.notes ? (
                  <label className="mt-2 block text-xs text-muted">
                    Notes (optional)
                    <textarea
                      aria-label={`Notes for ${criterion.title}`}
                      readOnly
                      value=""
                      rows={2}
                      className="mt-1 block w-full resize-none rounded-md border border-line bg-white p-2"
                    />
                  </label>
                ) : (
                  <p className="mt-2 text-xs leading-5 text-muted">
                    Notes disabled
                  </p>
                )}
              </div>
              <div className="shrink-0 self-end sm:self-center">
                <p className="mb-1.5 text-right text-xs font-semibold text-muted">
                  Score: ___/5
                </p>
                <div
                  role="img"
                  aria-label={`Unrated: ${criterion.title}`}
                  className="flex justify-end gap-1"
                >
                  {[1, 2, 3, 4, 5].map((rating) => (
                    <span
                      key={rating}
                      className="size-4 border border-neutral-400 bg-white sm:size-5"
                    />
                  ))}
                </div>
              </div>
            </li>
          ))}
        </ul>
      </section>

      <section
        aria-labelledby="preview-comments-title"
        className="mt-5 border-t border-ink/50 pt-4"
      >
        <div className="rounded-lg border border-ink/50 bg-mist/40 p-3">
          <h3 id="preview-comments-title" className="text-xs font-medium">
            Observee Comments:
          </h3>
          <textarea
            aria-label="Observee Comments"
            readOnly
            value=""
            rows={3}
            className="mt-2 block w-full resize-none border-0 bg-transparent text-xs"
          />
        </div>
      </section>

      <section
        aria-labelledby="preview-completed-title"
        className="mt-4 border-t border-ink/50 pt-4"
      >
        <div className="rounded-lg border border-line bg-mist/30 p-3">
          <h3
            id="preview-completed-title"
            className="text-sm font-semibold text-brand-green"
          >
            Observation Completion
          </h3>
          <div className="mt-3 grid gap-4 sm:grid-cols-2">
            {["Submitted By", "Completed On"].map((field) => (
              <label key={field} className="text-xs text-muted">
                {field}
                <input
                  aria-label={field}
                  readOnly
                  value=""
                  className={`${blankField} border-b border-line`}
                />
              </label>
            ))}
          </div>
          <div className="mt-3 grid gap-4 border-t border-line pt-3 sm:grid-cols-2">
            {["Observee", "Observer"].map((role) => (
              <div key={role} className="text-xs">
                <label className="text-muted">
                  {role} Signature
                  <input
                    aria-label={`${role} Signature`}
                    readOnly
                    value=""
                    className={`${blankField} border-b border-line`}
                  />
                </label>
                <label className="mt-3 block text-muted">
                  Signed On
                  <input
                    aria-label={`${role} Signed On`}
                    readOnly
                    value=""
                    className={`${blankField} border-b border-line`}
                  />
                </label>
              </div>
            ))}
          </div>
        </div>
      </section>
      <p className="mt-4 text-center text-xs font-semibold">End Record</p>
    </div>
  );
}
