import { useState } from "react";
import {
  type EmailPreview,
  type EmailTemplate,
  previewEmailTemplate,
  saveEmailTemplate,
  type TemplateCatalog,
  type TemplateDraft,
} from "../../api/emailTemplates";
import { useApiResource } from "../../api/observations";
import { PageHeader } from "../../components/PageHeader";

const empty: TemplateDraft = {
  name: "",
  subject: "",
  body: "",
  activeStatus: true,
  version: null,
};
const button =
  "rounded-lg bg-brand-green px-4 py-2 text-white disabled:opacity-50";
const field = "rounded-lg border border-line p-3";

export function AcEmailTemplates() {
  const { data, error, reload, setData } = useApiResource<TemplateCatalog>(
    "/api/email-templates",
  );
  const [key, setKey] = useState("");
  const [draft, setDraft] = useState<TemplateDraft>(empty);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [failure, setFailure] = useState("");
  const [preview, setPreview] = useState<EmailPreview | null>(null);

  function select(template?: EmailTemplate) {
    setKey(template?.templateType ?? "");
    setDraft(template ? { ...template } : { ...empty });
    setEditing(true);
    setPreview(null);
    setFailure("");
    setMessage("");
  }
  function change(update: Partial<TemplateDraft>) {
    setDraft({ ...draft, ...update });
    setPreview(null);
    setMessage("");
  }
  async function perform(save: boolean) {
    if (busy) return;
    setBusy(true);
    setFailure("");
    setMessage("");
    setPreview(null);
    try {
      if (save) {
        const template = await saveEmailTemplate(key, draft);
        setDraft({ ...template });
        setData(
          data
            ? {
                ...data,
                templates: [
                  ...data.templates.filter((t) => t.templateType !== key),
                  template,
                ].sort((a, b) => a.templateType.localeCompare(b.templateType)),
              }
            : null,
        );
        setMessage("Email template saved.");
      } else setPreview(await previewEmailTemplate(draft));
    } catch (cause) {
      setFailure(
        cause instanceof Error
          ? cause.message
          : "Unable to update email template.",
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <div>
      <PageHeader
        title="Email Templates"
        subtitle="Manage reusable email text. Notifications are simulated and do not deliver mail."
      />
      {error && (
        <p role="alert" className="mt-4">
          {error}{" "}
          <button type="button" onClick={reload}>
            Retry
          </button>
        </p>
      )}
      {!data && !error && <p role="status">Loading email templates...</p>}
      {data && (
        <>
          <div className="my-5 flex flex-wrap gap-2">
            {data.templates.map((t) => (
              <button
                type="button"
                disabled={busy}
                className="rounded-lg border border-line px-3 py-2"
                key={t.templateId}
                onClick={() => select(t)}
              >
                {t.name}
                {!t.activeStatus ? " (Inactive)" : ""}
              </button>
            ))}
            <button
              type="button"
              disabled={busy}
              className={button}
              onClick={() => select()}
            >
              New template
            </button>
          </div>
          {editing && (
            <form
              className="flex max-w-3xl flex-col gap-4"
              onSubmit={(e) => {
                e.preventDefault();
                void perform(true);
              }}
            >
              <fieldset disabled={busy} className="flex flex-col gap-4">
                <label className="flex flex-col gap-1">
                  Template key
                  <input
                    className={field}
                    required
                    maxLength={30}
                    pattern="[A-Z][A-Z0-9_]*"
                    readOnly={draft.version !== null}
                    value={key}
                    onChange={(e) => setKey(e.target.value)}
                  />
                </label>
                <label className="flex flex-col gap-1">
                  Name
                  <input
                    className={field}
                    required
                    maxLength={200}
                    value={draft.name}
                    onChange={(e) => change({ name: e.target.value })}
                  />
                </label>
                <label className="flex flex-col gap-1">
                  Subject
                  <input
                    className={field}
                    required
                    maxLength={255}
                    value={draft.subject}
                    onChange={(e) => change({ subject: e.target.value })}
                  />
                </label>
                <label className="flex flex-col gap-1">
                  Message
                  <textarea
                    className={field}
                    required
                    maxLength={16000}
                    rows={12}
                    value={draft.body}
                    onChange={(e) => change({ body: e.target.value })}
                  />
                </label>
                <label>
                  <input
                    type="checkbox"
                    checked={draft.activeStatus}
                    onChange={(e) => change({ activeStatus: e.target.checked })}
                  />{" "}
                  Active
                </label>
                <p className="text-sm text-muted">
                  Supported placeholders:{" "}
                  {data.placeholders.map((p) => `{{${p}}}`).join(", ")}. Each
                  notification must provide values for the placeholders it uses.
                </p>
                <div className="flex gap-3">
                  <button className={button} type="submit">
                    Save template
                  </button>
                  <button
                    className={button}
                    type="button"
                    onClick={() => void perform(false)}
                  >
                    Preview with sample data
                  </button>
                  <button
                    className="underline"
                    type="button"
                    onClick={() => setEditing(false)}
                  >
                    Cancel
                  </button>
                </div>
              </fieldset>
            </form>
          )}
        </>
      )}
      {failure && (
        <p role="alert" className="mt-4 text-brand-red">
          {failure}{" "}
          <button
            type="button"
            disabled={busy}
            className="underline"
            onClick={() => {
              setEditing(false);
              setFailure("");
              reload();
            }}
          >
            Reload templates
          </button>
        </p>
      )}
      {message && (
        <p role="status" className="mt-4">
          {message}
        </p>
      )}
      {preview && (
        <section
          aria-label="Email preview"
          className="mt-5 rounded-lg border border-line p-5"
        >
          <h2 className="font-semibold">Preview — sample data</h2>
          <p className="my-3 font-semibold">{preview.subject}</p>
          <p className="whitespace-pre-wrap">{preview.body}</p>
        </section>
      )}
    </div>
  );
}
