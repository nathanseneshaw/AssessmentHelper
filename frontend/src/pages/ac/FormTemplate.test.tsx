import { afterEach, beforeEach, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { App } from "../../App";
import type { FormTemplate } from "../../api/formTemplate";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";

let template: FormTemplate;
let writes: FormTemplate[];
let failSave: boolean;
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
      return Response.json(template);
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

test("only included criteria are shown when the template loads", async () => {
  render(<App />);
  await screen.findByText("Student Engagement");
  expect(screen.queryByText("Clarity")).toBeNull();
  expect(screen.getByText("Version 1")).toBeDefined();
});

test("edits, additions and deletions are saved when Save Changes is clicked", async () => {
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

test("additions and deletions are discarded when Cancel is clicked", async () => {
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

test("the criterion is kept when its removal is cancelled", async () => {
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

test("nothing is saved when no criterion is selected", async () => {
  const dialog = await edit();
  const ui = within(dialog);
  fireEvent.click(ui.getByLabelText("Include criterion 1"));
  fireEvent.click(ui.getByRole("button", { name: "Save Changes" }));
  await screen.findByText(/Select at least one criterion/);
  expect(writes).toHaveLength(0);
});

test("the preview shows the selected criteria and updates when another member saves", async () => {
  render(<App />);
  fireEvent.click(await screen.findByRole("button", { name: "Preview Form" }));
  const dialog = screen.getByRole("dialog");
  expect(
    within(dialog).getByRole("img", { name: "Unrated: Student Engagement" }),
  ).toBeDefined();
  expect(within(dialog).queryByText("Clarity")).toBeNull();

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
  expect(within(dialog).queryByText("Hidden criterion")).toBeNull();
  expect(within(dialog).queryByText("Student Engagement")).toBeNull();
});

test("an old version is restored only when the restore is confirmed", async () => {
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
  fireEvent.change(
    await within(history).findByRole("combobox", { name: "Version" }),
    { target: { value: "1" } },
  );
  expect(within(history).getByText("Student Engagement")).toBeDefined();
  fireEvent.click(
    within(history).getByRole("button", { name: "Restore This Version" }),
  );
  expect(restores).toBe(0);
  fireEvent.click(
    within(
      screen.getByRole("dialog", { name: "Restore version 1?" }),
    ).getByRole("button", { name: "Confirm Restore" }),
  );
  await screen.findByText(/Version 1 is now current/);
  expect(restores).toBe(1);
  expect(screen.getByText("Student Engagement")).toBeDefined();
  expect(screen.queryByText("Updated rubric")).toBeNull();
});
