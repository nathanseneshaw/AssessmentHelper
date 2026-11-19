import { apiFetch, apiFetchBlob } from "./client";

export type ImportKind = "professors" | "sections";

export interface ImportError {
  row: number | null;
  column: string | null;
  message: string;
}

export interface ImportResult {
  saved: boolean;
  rowCount: number;
  errors: ImportError[];
}

export function uploadImport(
  kind: ImportKind,
  file: File,
): Promise<ImportResult> {
  const body = new FormData();
  body.append("file", file);
  return apiFetch<ImportResult>(`/api/imports/${kind}`, {
    method: "POST",
    body,
  });
}

export async function downloadImportTemplate(kind: ImportKind): Promise<void> {
  const blob = await apiFetchBlob(`/api/imports/${kind}/template`);
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${kind}-template.csv`;
  document.body.append(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
