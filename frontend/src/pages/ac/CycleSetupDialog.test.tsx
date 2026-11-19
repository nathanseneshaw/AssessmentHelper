import { afterEach, expect, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { fakeBackend, resetBackend } from "../../test/fakeBackend";
import { CycleSetupDialog } from "./CycleSetupDialog";

afterEach(() => {
  cleanup();
  resetBackend();
});

test("prefills a semester and sends both existing CSV uploads with deadlines", async () => {
  let submitted: FormData | null = null;
  let created = 0;
  fakeBackend({
    "/api/ac/cycles/upcoming-semesters": () =>
      Response.json([
        {
          term: "Spring",
          year: 2027,
          startDate: "2027-01-19",
          endDate: "2027-05-14",
          estimated: true,
          hasCycle: false,
        },
      ]),
    "/api/ac/cycles": (init) => {
      submitted = init?.body as FormData;
      return Response.json({ cycleId: 42 });
    },
  });
  render(
    <CycleSetupDialog
      onClose={() => {}}
      onCreated={(id) => {
        created = id;
      }}
    />,
  );
  await screen.findByRole("option", { name: "Spring 2027" });
  fireEvent.change(screen.getByLabelText("Semester"), {
    target: { value: "Spring-2027" },
  });
  expect(
    (screen.getByLabelText("Semester starts") as HTMLInputElement).value,
  ).toBe("2027-01-19");
  expect(screen.getByText(/dates are estimates/i)).toBeTruthy();
  fireEvent.change(screen.getByLabelText("Signup deadline"), {
    target: { value: "2027-02-10" },
  });
  fireEvent.change(screen.getByLabelText("Observation deadline"), {
    target: { value: "2027-04-20" },
  });
  fireEvent.change(screen.getByLabelText("Feedback deadline"), {
    target: { value: "2027-05-10" },
  });
  fireEvent.change(screen.getByLabelText(/Professors CSV/), {
    target: { files: [new File(["professors"], "professors.csv")] },
  });
  fireEvent.change(screen.getByLabelText(/Class sections CSV/), {
    target: { files: [new File(["sections"], "sections.csv")] },
  });
  const form = screen
    .getByRole("button", { name: "Create cycle" })
    .closest("form");
  if (!form) throw new Error("Setup form was not found");
  fireEvent.submit(form);
  await waitFor(() => expect(created).toBe(42));
  expect((submitted as FormData | null)?.get("professors")).toBeInstanceOf(
    File,
  );
  expect((submitted as FormData | null)?.get("sections")).toBeInstanceOf(File);
});
