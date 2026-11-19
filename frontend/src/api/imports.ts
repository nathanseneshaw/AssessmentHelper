import { API_URL, apiFetch } from "./client";

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

export function importTemplateUrl(kind: ImportKind): string {
  return `${API_URL}/api/imports/${kind}/template`;
}
