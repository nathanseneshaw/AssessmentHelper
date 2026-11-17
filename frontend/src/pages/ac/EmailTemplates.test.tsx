import { afterEach, beforeEach, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import type { EmailTemplate } from "../../api/emailTemplates";
import { AuthProvider } from "../../auth/AuthContext";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { AcEmailTemplates } from "./EmailTemplates";

beforeEach(() => signInAs("ac"));
afterEach(() => {
  cleanup();
  resetBackend();
});
const initial: EmailTemplate = {
  templateId: 1,
  templateType: "EVALUATION_DUE",
  name: "Evaluation due",
  subject: "Hello {{lastName}}",
  body: "Register for {{cycleLabel}}.",
  activeStatus: true,
  version: 0,
};
function install(save: (init: RequestInit | undefined) => Response) {
  fakeBackend({
    "/api/email-templates": () =>
      Response.json({
        templates: [initial],
        placeholders: ["lastName", "cycleLabel"],
      }),
    "/api/email-templates/EVALUATION_DUE": save,
    "/api/email-templates/preview": () =>
      Response.json({
        subject: "Hello Nguyen",
        body: "Register for Fall 2026.",
      }),
  });
}
async function edit() {
  render(
    <MemoryRouter>
      <AuthProvider>
        <AcEmailTemplates />
      </AuthProvider>
    </MemoryRouter>,
  );
  fireEvent.click(
    await screen.findByRole("button", { name: "Evaluation due" }),
  );
}

test("the preview fills in sample data when a template is edited", async () => {
  let saved: Record<string, unknown> | undefined;
  install((init) => {
    saved = JSON.parse(String(init?.body));
    return Response.json({ ...initial, subject: saved?.subject, version: 1 });
  });
  await edit();
  fireEvent.click(
    screen.getByRole("button", { name: "Preview with sample data" }),
  );
  await screen.findByRole("region", { name: "Email preview" });
  expect(screen.getByText("Hello Nguyen")).toBeTruthy();
  fireEvent.change(screen.getByLabelText("Subject"), {
    target: { value: "Updated {{lastName}}" },
  });
  expect(screen.queryByRole("region", { name: "Email preview" })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Save template" }));
  await screen.findByText("Email template saved.");
  expect(saved?.subject).toBe("Updated {{lastName}}");
  expect(saved?.version).toBe(0);
});

test("a new template is created when a new scenario key is entered", async () => {
  let body: Record<string, unknown> | undefined;
  fakeBackend({
    "/api/email-templates": () =>
      Response.json({ templates: [], placeholders: ["lastName"] }),
    "/api/email-templates/OBSERVATION_REMINDER": (init) => {
      body = JSON.parse(String(init?.body));
      return Response.json({
        ...body,
        templateId: 5,
        templateType: "OBSERVATION_REMINDER",
        version: 0,
      });
    },
  });
  render(
    <MemoryRouter>
      <AuthProvider>
        <AcEmailTemplates />
      </AuthProvider>
    </MemoryRouter>,
  );
  fireEvent.click(await screen.findByRole("button", { name: "New template" }));
  for (const [label, value] of [
    ["Template key", "OBSERVATION_REMINDER"],
    ["Name", "Observation reminder"],
    ["Subject", "Reminder"],
    ["Message", "Hello {{lastName}}"],
  ] as const) {
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
  }
  fireEvent.click(screen.getByRole("button", { name: "Save template" }));
  await screen.findByText("Email template saved.");
  expect(body?.version).toBeNull();
  expect(body?.activeStatus).toBe(true);
  expect(
    (screen.getByLabelText("Template key") as HTMLInputElement).readOnly,
  ).toBe(true);
});

test("the draft is kept and can be reloaded when the save conflicts", async () => {
  install(() =>
    Response.json(
      { detail: "This template changed. Reload it before saving again." },
      { status: 409 },
    ),
  );
  await edit();
  fireEvent.change(screen.getByLabelText("Message"), {
    target: { value: "My unsaved draft" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save template" }));
  expect((await screen.findByRole("alert")).textContent).toContain(
    "This template changed",
  );
  expect((screen.getByLabelText("Message") as HTMLTextAreaElement).value).toBe(
    "My unsaved draft",
  );
  fireEvent.click(screen.getByRole("button", { name: "Reload templates" }));
  await waitFor(() => expect(screen.queryByLabelText("Message")).toBeNull());
  expect(
    await screen.findByRole("button", { name: "Evaluation due" }),
  ).toBeTruthy();
});

test("edits are not saved when Cancel is clicked", async () => {
  let writes = 0;
  install(() => {
    writes++;
    return Response.json(initial);
  });
  await edit();
  fireEvent.change(screen.getByLabelText("Subject"), {
    target: { value: "Unsaved" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  fireEvent.click(screen.getByRole("button", { name: "Evaluation due" }));
  expect((screen.getByLabelText("Subject") as HTMLInputElement).value).toBe(
    initial.subject,
  );
  expect(writes).toBe(0);
});
