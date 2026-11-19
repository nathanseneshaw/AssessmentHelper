import { useState } from "react";
import { downloadImportTemplate, type ImportKind } from "../../api/imports";

export function TemplateDownload({
  kind,
  className,
}: {
  kind: ImportKind;
  className?: string;
}) {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const label = kind === "professors" ? "professors" : "class sections";
  return (
    <span>
      <button
        type="button"
        className={className}
        disabled={busy}
        aria-label={`Download template for ${label}`}
        onClick={(event) => {
          event.preventDefault();
          event.stopPropagation();
          setError("");
          setBusy(true);
          void downloadImportTemplate(kind)
            .catch((cause: unknown) => {
              setError(
                cause instanceof Error
                  ? cause.message
                  : "Could not download template.",
              );
            })
            .finally(() => setBusy(false));
        }}
      >
        {busy ? "Downloading…" : "Download template"}
      </button>
      {error && (
        <span role="alert" className="ml-2 text-brand-red">
          {error}
        </span>
      )}
    </span>
  );
}
