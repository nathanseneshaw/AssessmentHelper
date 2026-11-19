import { afterEach, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { fakeBackend, resetBackend } from "../../test/fakeBackend";
import { CycleFilesDialog } from "./CycleFilesDialog";

afterEach(() => {
  cleanup();
  resetBackend();
});

test("replaces both files for the selected future cycle", async () => {
  let body: FormData | null = null;
  let saved = false;
  fakeBackend({
    "/api/ac/cycles/42/files": (init) => {
      body = init?.body as FormData;
      expect(init?.method).toBe("PUT");
      return Response.json({ cycleId: 42 });
    },
  });
  render(
    <CycleFilesDialog
      cycleId={42}
      label="Spring 2027"
      onClose={() => {}}
      onSaved={() => {
        saved = true;
      }}
    />,
  );
  fireEvent.change(screen.getByLabelText(/Professors CSV/), {
    target: { files: [new File(["p"], "professors.csv")] },
  });
  fireEvent.change(screen.getByLabelText(/Class sections CSV/), {
    target: { files: [new File(["s"], "sections.csv")] },
  });
  const form = screen
    .getByRole("button", { name: "Replace files" })
    .closest("form");
  if (!form) throw new Error("Replacement form was not found");
  fireEvent.submit(form);
  await waitFor(() => expect(saved).toBe(true));
  expect((body as FormData | null)?.get("professors")).toBeInstanceOf(File);
  expect((body as FormData | null)?.get("sections")).toBeInstanceOf(File);
});
