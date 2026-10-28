import { afterEach, beforeEach, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { App } from "../../App";
import type { FormTemplate } from "../../api/formTemplate";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";

let template: FormTemplate;
let writes: FormTemplate[];
let failSave: boolean;
let failLoad: boolean;
let versions: FormTemplate[];
let restores: number;
beforeEach(() => {
  template = {
    templateId: 1,
    version: 1,
    criteria: [
      { title: "Student Engagement", selected: true, notes: true },
      { title: "Clarity", selected: false, notes: false },
    ],
  };
  versions = [structuredClone(template)];
  restores = 0;
  writes = [];
  failSave = false;
  failLoad = false;
  fakeBackend({
    "/api/form-template/versions": () => Response.json(versions),
    "/api/form-template/versions/1/restore": () => {
      restores++;
      template = {
        ...(versions[versions.length - 1] as FormTemplate),
        templateId: 1,
        version: 1,
      };
      return Response.json(template);
    },
    "/api/form-template": (init) => {
      if (init?.method === "PUT") {
        const update = JSON.parse(String(init.body));
        writes.push(update);
        if (failSave)
          return Response.json(
            { detail: "Another committee member updated this template." },
            { status: 409 },
          );
        template = {
          ...update,
          templateId: template.templateId + 1,
          version: template.version + 1,
        };
        return Response.json(template);
      }
      return failLoad
        ? Response.json({ detail: "Could not load template." }, { status: 503 })
        : Response.json(template);
    },
  });
  signInAs("ac");
  window.history.pushState({}, "", "/ac/form-template");
});
afterEach(() => {
  cleanup();
  resetBackend();
});
async function edit() {
  render(<App />);
  fireEvent.click(
    await screen.findByRole("button", { name: "Add/Edit Criterion" }),
  );
  return screen.getByRole("dialog", { name: "Edit Observation Form Criteria" });
}
test("edits, adds, deletes, and saves selected criteria and notes", async () => {
  const dialog = await edit();
  const ui = within(dialog);
  fireEvent.change(ui.getByLabelText("Criterion 1 name"), {
    target: { value: "Updated engagement" },
  });
  fireEvent.click(ui.getByLabelText("Optional notes for criterion 1"));
  fireEvent.click(ui.getByLabelText("Delete criterion 2"));
  fireEvent.click(
    within(screen.getByRole("dialog", { name: "Remove Criterion" })).getByRole(
      "button",
      { name: "Remove Criterion" },
    ),
  );
  fireEvent.click(ui.getByRole("button", { name: "+ Add New Criterion" }));
  fireEvent.change(ui.getByLabelText("Criterion 2 name"), {
    target: { value: "New criterion" },
  });
  fireEvent.click(ui.getByLabelText("Include criterion 2"));
  fireEvent.click(ui.getByRole("button", { name: "Save Changes" }));
  await screen.findByText(/Changes saved/);
  expect(writes[0]?.criteria).toEqual([
    { title: "Updated engagement", selected: true, notes: false },
    { title: "New criterion", selected: false, notes: false },
  ]);
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(screen.getByText("Version 2")).toBeDefined();
});
test("cancel discards additions and deletes without writing to the server", async () => {
  const dialog = await edit();
  fireEvent.click(within(dialog).getByLabelText("Delete criterion 1"));
  fireEvent.click(
    within(screen.getByRole("dialog", { name: "Remove Criterion" })).getByRole(
      "button",
      { name: "Remove Criterion" },
    ),
  );
  fireEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
  expect(screen.getByText("Student Engagement")).toBeDefined();
  expect(writes).toHaveLength(0);
  fireEvent.click(screen.getByRole("button", { name: "Add/Edit Criterion" }));
  expect(
    (screen.getByLabelText("Criterion 1 name") as HTMLInputElement).value,
  ).toBe("Student Engagement");
});
test("keeps unsaved edits when another committee member has saved first", async () => {
  failSave = true;
  const dialog = await edit();
  fireEvent.change(within(dialog).getByLabelText("Criterion 1 name"), {
    target: { value: "My edit" },
  });
  fireEvent.click(within(dialog).getByRole("button", { name: "Save Changes" }));
  await screen.findByRole("alert");
  expect(
    (screen.getByLabelText("Criterion 1 name") as HTMLInputElement).value,
  ).toBe("My edit");
  expect(screen.queryByText(/Changes saved/)).toBeNull();
});
test("refreshes shared state on focus without overwriting the open draft", async () => {
  const dialog = await edit();
  fireEvent.change(within(dialog).getByLabelText("Criterion 1 name"), {
    target: { value: "My draft" },
  });
  template = {
    ...template,
    templateId: 2,
    version: 2,
    criteria: [{ title: "Other committee edit", selected: true, notes: false }],
  };
  fireEvent.focus(window);
  await screen.findByText("Other committee edit");
  expect(
    (screen.getByLabelText("Criterion 1 name") as HTMLInputElement).value,
  ).toBe("My draft");
  fireEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
  fireEvent.click(screen.getByRole("button", { name: "Add/Edit Criterion" }));
  expect(
    (screen.getByLabelText("Criterion 1 name") as HTMLInputElement).value,
  ).toBe("Other committee edit");
});
test("rejects an empty selection and duplicate names before saving", async () => {
  const dialog = await edit();
  const ui = within(dialog);
  fireEvent.click(ui.getByLabelText("Include criterion 1"));
  fireEvent.click(ui.getByRole("button", { name: "Save Changes" }));
  await screen.findByText(/Select at least one criterion/);
  fireEvent.click(ui.getByLabelText("Include criterion 1"));
  fireEvent.change(ui.getByLabelText("Criterion 2 name"), {
    target: { value: "student engagement" },
  });
  fireEvent.click(ui.getByRole("button", { name: "Save Changes" }));
  await screen.findByText("Criterion names must be unique.");
  expect(writes).toHaveLength(0);
});
test("load failures support retry and preview includes only selected criteria", async () => {
  failLoad = true;
  render(<App />);
  await screen.findByRole("alert");
  failLoad = false;
  fireEvent.click(screen.getByRole("button", { name: "Retry" }));
  fireEvent.click(await screen.findByRole("button", { name: "Preview Form" }));
  const preview = screen.getByRole("dialog");
  expect(within(preview).getByText("Student Engagement")).toBeDefined();
  expect(within(preview).queryByText("Clarity")).toBeNull();
  fireEvent.click(within(preview).getByRole("button", { name: "Close" }));
  await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
});

test("main screen shows only included criteria and keeps a single editor entry point", async () => {
  render(<App />);
  await screen.findByText("Student Engagement");
  expect(screen.queryByText("Clarity")).toBeNull();
  expect(screen.queryByRole("button", { name: "Edit Criteria" })).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Add/Edit Criterion" }));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText("Remove")).toBeDefined();
  expect(
    (within(dialog).getByLabelText("Criterion 2 name") as HTMLInputElement)
      .value,
  ).toBe("Clarity");
  const notes = within(dialog).getByRole("checkbox", {
    name: "Optional notes for criterion 2",
  }) as HTMLInputElement;
  expect(notes.className).toContain("rounded-full");
  expect(notes.checked).toBe(false);
  fireEvent.click(notes);
  expect(notes.checked).toBe(true);
  fireEvent.click(notes);
  expect(notes.checked).toBe(false);
});

test("removal requires confirmation and cancel keeps the criterion", async () => {
  const editor = await edit();
  fireEvent.click(within(editor).getByLabelText("Delete criterion 2"));
  const confirmation = screen.getByRole("dialog", {
    name: "Remove Criterion",
  });
  expect(within(confirmation).getByText("Clarity")).toBeDefined();
  expect(
    (within(editor).getByLabelText("Criterion 2 name") as HTMLInputElement)
      .value,
  ).toBe("Clarity");
  expect(writes).toHaveLength(0);
  fireEvent.click(within(confirmation).getByRole("button", { name: "Cancel" }));
  expect(screen.queryByRole("dialog", { name: "Remove Criterion" })).toBeNull();
  expect(within(editor).getByLabelText("Criterion 2 name")).toBeDefined();
  fireEvent.click(within(editor).getByLabelText("Delete criterion 2"));
  fireEvent.click(
    within(screen.getByRole("dialog", { name: "Remove Criterion" })).getByRole(
      "button",
      { name: "Remove Criterion" },
    ),
  );
  expect(within(editor).queryByLabelText("Criterion 2 name")).toBeNull();
  expect(writes).toHaveLength(0);
});

test("views an old version and restores it only after confirmation", async () => {
  const original = structuredClone(template);
  template = {
    templateId: 2,
    version: 2,
    criteria: [{ title: "Updated rubric", selected: true, notes: false }],
  };
  versions = [structuredClone(template), original];
  render(<App />);
  fireEvent.click(
    await screen.findByRole("button", { name: "Version History" }),
  );
  const history = screen.getByRole("dialog", {
    name: "Template Version History",
  });
  const select = await within(history).findByRole("combobox", {
    name: "Version",
  });
  expect(
    (
      within(history).getByRole("button", {
        name: "Restore This Version",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  fireEvent.change(select, { target: { value: "1" } });
  expect(within(history).getByText("Student Engagement")).toBeDefined();
  expect(within(history).getByText("Clarity")).toBeDefined();
  fireEvent.click(
    within(history).getByRole("button", { name: "Restore This Version" }),
  );
  const confirmation = screen.getByRole("dialog", {
    name: "Restore version 1?",
  });
  expect(restores).toBe(0);
  fireEvent.click(within(confirmation).getByRole("button", { name: "Cancel" }));
  expect(restores).toBe(0);
  fireEvent.click(
    within(history).getByRole("button", { name: "Restore This Version" }),
  );
  fireEvent.click(
    within(
      screen.getByRole("dialog", { name: "Restore version 1?" }),
    ).getByRole("button", { name: "Confirm Restore" }),
  );
  await screen.findByText(/Version 1 is now current/);
  expect(restores).toBe(1);
  expect(screen.getByText("Student Engagement")).toBeDefined();
  expect(screen.queryByText("Updated rubric")).toBeNull();
  expect(screen.queryByText("Included")).toBeNull();
});

test("shared polling can move the active template back to an older version", async () => {
  const old = structuredClone(template);
  template = {
    templateId: 3,
    version: 3,
    criteria: [{ title: "Newest rubric", selected: true, notes: false }],
  };
  render(<App />);
  await screen.findByText("Version 3");
  template = old;
  fireEvent.focus(window);
  await screen.findByText("Version 1");
  expect(screen.getByText("Student Engagement")).toBeDefined();
  expect(screen.queryByText("Newest rubric")).toBeNull();
});

test("record preview follows current version criteria and optional notes settings", async () => {
  render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: "Preview Form" }));
  const dialog = screen.getByRole("dialog");
  expect(within(dialog).getByText("Observation Criteria")).toBeDefined();
  expect(
    within(dialog).getByRole("img", { name: "Unrated: Student Engagement" }),
  ).toBeDefined();
  expect(within(dialog).getByText("Observee Comments:")).toBeDefined();
  expect(within(dialog).getByText("Observee Signature")).toBeDefined();
  expect(within(dialog).getByText("Observer Signature")).toBeDefined();
  expect(within(dialog).queryByText("Notes disabled")).toBeNull();
  for (const field of within(dialog).getAllByRole("textbox")) {
    expect((field as HTMLInputElement | HTMLTextAreaElement).value).toBe("");
  }
  expect(
    within(dialog).queryByText(/Professor 3|CS 4348|Signed!|Example/),
  ).toBeNull();
  template = {
    templateId: 4,
    version: 4,
    criteria: [
      { title: "Current selected criterion", selected: true, notes: false },
      { title: "Hidden criterion", selected: false, notes: true },
    ],
  };
  fireEvent.focus(window);
  await within(dialog).findByText("Current selected criterion");
  expect(within(dialog).getByText("Notes disabled")).toBeDefined();
  expect(within(dialog).queryByText("Hidden criterion")).toBeNull();
  expect(within(dialog).queryByText("Student Engagement")).toBeNull();
  expect(within(dialog).getByText(/Current version 4/)).toBeDefined();
});
