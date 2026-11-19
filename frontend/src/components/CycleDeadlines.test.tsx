import { afterEach, beforeEach, expect, setSystemTime, test } from "bun:test";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import type { Cycle } from "../api/observerEligibility";
import { fakeBackend, resetBackend } from "../test/fakeBackend";
import { cycle as fixture } from "../test/observerFixtures";
import { CycleDeadlines } from "./CycleDeadlines";

let cycle: Cycle;
let sent: unknown[];
let failure: boolean;
let saved: number;

beforeEach(() => {
  setSystemTime(new Date("2026-10-21T15:00:00Z"));
  cycle = { ...fixture, feedbackDeadline: "2026-12-01" };
  sent = [];
  failure = false;
  saved = 0;
  fakeBackend({
    "/api/observer-eligibility/cycles/1/deadlines": (init) => {
      expect(init?.method).toBe("PUT");
      sent.push(JSON.parse(String(init?.body)));
      return failure
        ? Response.json(
            {
              detail:
                "These deadlines would exclude an existing observation date.",
            },
            { status: 409 },
          )
        : Response.json(cycle);
    },
  });
});

afterEach(() => {
  setSystemTime();
  cleanup();
  resetBackend();
});

function open() {
  render(
    <CycleDeadlines
      cycle={cycle}
      onSaved={() => {
        saved++;
      }}
    />,
  );
  fireEvent.click(screen.getByRole("button", { name: "Edit Deadlines" }));
}

test("edits all three deadlines and refreshes the dashboard after saving", async () => {
  open();
  fireEvent.change(screen.getByLabelText("Observation Deadline"), {
    target: { value: "2026-11-20" },
  });
  fireEvent.change(screen.getByLabelText("Feedback/Survey Deadline"), {
    target: { value: "2026-12-05" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save Deadlines" }));
  await screen.findByRole("status");
  expect(saved).toBe(1);
  expect(sent).toEqual([
    {
      signupDeadline: "2026-10-31",
      observationDeadline: "2026-11-20",
      feedbackDeadline: "2026-12-05",
    },
  ]);
  expect(screen.queryByRole("button", { name: "Save Deadlines" })).toBeNull();
});

test("failed changes keep the dates editable and show the server explanation", async () => {
  failure = true;
  open();
  fireEvent.change(screen.getByLabelText("Observation Deadline"), {
    target: { value: "2026-11-20" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save Deadlines" }));
  expect((await screen.findByRole("alert")).textContent).toContain(
    "existing observation date",
  );
  expect(
    (screen.getByLabelText("Observation Deadline") as HTMLInputElement).value,
  ).toBe("2026-11-20");
  expect(saved).toBe(0);
});

test("locks only passed deadlines and sets the current date as the minimum", () => {
  setSystemTime(new Date("2026-11-01T15:00:00Z"));
  open();
  expect(
    (screen.getByLabelText(/Sign-up Deadline/) as HTMLInputElement).disabled,
  ).toBe(true);
  const observation = screen.getByLabelText(
    "Observation Deadline",
  ) as HTMLInputElement;
  expect(observation.disabled).toBe(false);
  expect(observation.min).toBe("2026-11-15");
  expect(screen.getByText("Deadline passed; changes are locked.")).toBeTruthy();
});

test("cancel discards draft dates without saving", () => {
  open();
  fireEvent.change(screen.getByLabelText("Observation Deadline"), {
    target: { value: "2026-11-20" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  fireEvent.click(screen.getByRole("button", { name: "Edit Deadlines" }));
  expect(
    (screen.getByLabelText("Observation Deadline") as HTMLInputElement).value,
  ).toBe("2026-11-15");
  expect(sent).toHaveLength(0);
});
