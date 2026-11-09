import { apiFetch } from "./client";
export interface EmailTemplate {
  templateId: number;
  templateType: string;
  name: string;
  subject: string;
  body: string;
  activeStatus: boolean;
  version: number;
}
export interface TemplateDraft {
  name: string;
  subject: string;
  body: string;
  activeStatus: boolean;
  version: number | null;
}
export interface TemplateCatalog {
  templates: EmailTemplate[];
  placeholders: string[];
}
export interface EmailPreview {
  subject: string;
  body: string;
}
export const saveEmailTemplate = (key: string, draft: TemplateDraft) =>
  apiFetch<EmailTemplate>(`/api/email-templates/${encodeURIComponent(key)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(draft),
  });
export const previewEmailTemplate = (draft: TemplateDraft) =>
  apiFetch<EmailPreview>("/api/email-templates/preview", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(draft),
  });
