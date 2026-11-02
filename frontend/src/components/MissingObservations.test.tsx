import { afterEach, expect, test } from "bun:test";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { observerChanged } from "../api/observerEligibility";
import { fakeBackend, resetBackend } from "../test/fakeBackend";
import {
  type MissingObservationAlert,
  MissingObservations,
} from "./MissingObservations";

afterEach(() => {
  cleanup();
  resetBackend();
});

const late: MissingObservationAlert = {
  kind: "LATE_SUBMISSION",
  observationId: 42,
  dueDate: "2026-11-04",
  message: "Observation for Leander is late.",
};
const signup: MissingObservationAlert = {
  kind: "MISSING_SIGNUP",
  observationId: null,
  dueDate: "2026-10-31",
  message: "Your required evaluation has no signup.",
};
function show(observerOnly = false) {
  return render(
    <MemoryRouter>
      <MissingObservations professorId={2} observerOnly={observerOnly} />
    </MemoryRouter>,
  );
}

test("dashboard displays missing signup and links the observer to their late form", async () => {
  fakeBackend({
    "/api/professors/2/missing-observations": () =>
      Response.json([late, signup]),
  });
  show();
  expect(await screen.findByText(late.message)).toBeTruthy();
  expect(screen.getByText(signup.message)).toBeTruthy();
  expect(
    screen
      .getByRole("link", { name: "Complete observation" })
      .getAttribute("href"),
  ).toBe("/faculty/observations/42");
});

test("observer list only displays observer obligations", async () => {
  fakeBackend({
    "/api/professors/2/missing-observations": () =>
      Response.json([late, signup]),
  });
  show(true);
  await screen.findByText(late.message);
  expect(screen.queryByText(signup.message)).toBeNull();
});

test("submitted observation disappears after a workflow change", async () => {
  let alerts = [late];
  fakeBackend({
    "/api/professors/2/missing-observations": () => Response.json(alerts),
  });
  show();
  await screen.findByText(late.message);
  alerts = [];
  act(() => observerChanged());
  await waitFor(() =>
    expect(
      screen.queryByRole("region", { name: "Missing observations" }),
    ).toBeNull(),
  );
});

test("failed checks show a retry that recovers", async () => {
  let failed = true;
  fakeBackend({
    "/api/professors/2/missing-observations": () =>
      failed
        ? Response.json({ detail: "Unavailable" }, { status: 503 })
        : Response.json([late]),
  });
  show();
  await screen.findByRole("alert");
  failed = false;
  fireEvent.click(screen.getByRole("button", { name: "Retry" }));
  await screen.findByText(late.message);
  expect(screen.queryByRole("alert")).toBeNull();
});
