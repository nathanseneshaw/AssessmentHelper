import { apiFetch } from "./client";
export interface Criterion {
  title: string;
  selected: boolean;
  notes: boolean;
}
export interface FormTemplate {
  templateId: number;
  version: number;
  criteria: Criterion[];
}
export const getFormTemplate = (signal?: AbortSignal) =>
  apiFetch<FormTemplate>("/api/form-template", { signal });
export const saveFormTemplate = (template: FormTemplate) =>
  apiFetch<FormTemplate>("/api/form-template", {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      templateId: template.templateId,
      criteria: template.criteria,
    }),
  });

export const getFormTemplateHistory = (signal?: AbortSignal) =>
  apiFetch<FormTemplate[]>("/api/form-template/versions", { signal });
export const restoreFormTemplate = (sourceId: number, currentId: number) =>
  apiFetch<FormTemplate>(`/api/form-template/versions/${sourceId}/restore`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ templateId: currentId }),
  });
