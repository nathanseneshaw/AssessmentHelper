import { afterEach, beforeEach, expect, spyOn, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { App } from "../../App";
import {
  type FeedbackSurvey,
  feedbackPath,
  feedbackQuestions,
} from "../../api/feedback";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";

let survey: FeedbackSurvey;
/** Surveys for the professor's other cycles, after the one under test. */
let others: FeedbackSurvey[];
let sent: { ratings: number[]; comments: string } | null;
let fail: boolean;

beforeEach(() => {
  sent = null;
  fail = false;
  others = [];
  survey = {
    cycleId: 1,
    label: "Fall 2026",
    observationDeadline: "2026-11-01",
    feedbackDeadline: "2026-11-10",
    status: "AVAILABLE",
  };
  fakeBackend({
    [feedbackPath]: () => Response.json([survey, ...others]),
    [`${feedbackPath}/1`]: (init) => {
      sent = JSON.parse(String(init?.body));
      if (fail)
        return Response.json(
          { detail: "Could not save feedback." },
          { status: 500 },
        );
      survey = {
        ...survey,
        ...sent,
        submittedDate: "2026-11-02",
        status: "SUBMITTED",
      };
      return Response.json(survey);
    },
  });
  signInAs("professor");
});

afterEach(() => {
  cleanup();
  resetBackend();
});

async function open() {
  window.history.pushState({}, "", "/faculty/feedback?cycle=1");
  render(<App />);
  await within(await screen.findByRole("dialog")).findByText("Fall 2026");
}

function answerAll() {
  feedbackQuestions.forEach((question, i) => {
    const group = screen.getByRole("group", { name: question });
    fireEvent.click(
      within(group).getByRole("radio", { name: String((i % 5) + 1) }),
    );
  });
}

test("requires six ratings and submits one survey for the cycle", async () => {
  await open();
  expect(
    (screen.getByRole("button", { name: "Submit" }) as HTMLButtonElement)
      .disabled,
  ).toBe(true);
  answerAll();
  fireEvent.change(screen.getByLabelText(/Additional Comments/), {
    target: { value: "Helpful process" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Submit" }));
  await screen.findByRole("heading", { name: "Feedback Survey Complete" });
  expect(sent).toEqual({
    ratings: [1, 2, 3, 4, 5, 1],
    comments: "Helpful process",
  });
  fireEvent.click(
    screen.getByRole("button", { name: "View Submitted Survey" }),
  );
  const dialog = within(screen.getByRole("dialog"));
  expect(dialog.getByText("Helpful process")).toBeTruthy();
  expect(dialog.getAllByText("1 / 5")).toHaveLength(2);
  expect(dialog.getByText("Submitted Nov 2, 2026")).toBeTruthy();
  expect(dialog.queryByRole("button", { name: "Submit" })).toBeNull();
  cleanup();
  await open();
  expect(
    within(screen.getByRole("dialog")).getByText("Helpful process"),
  ).toBeTruthy();
  expect(screen.queryByRole("textbox")).toBeNull();
});

test("unfinished responses warn before closing or reloading", async () => {
  await open();
  answerAll();
  const unload = new Event("beforeunload", { cancelable: true });
  window.dispatchEvent(unload);
  expect(unload.defaultPrevented).toBe(true);
  fireEvent.click(
    within(screen.getByRole("dialog")).getByRole("link", { name: "Close" }),
  );
  await screen.findByRole("alertdialog");
  fireEvent.click(screen.getByRole("button", { name: "Keep editing" }));
  expect(
    screen.getAllByRole("radio").filter((r) => (r as HTMLInputElement).checked),
  ).toHaveLength(6);
});

test("cancel does not submit feedback", async () => {
  await open();
  answerAll();
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  await screen.findByRole("heading", { name: "Feedback Survey Canceled" });
  expect(sent).toBeNull();
});

test("failed submissions preserve answers for retry", async () => {
  fail = true;
  await open();
  answerAll();
  fireEvent.click(screen.getByRole("button", { name: "Submit" }));
  await within(screen.getByRole("dialog")).findByRole("alert");
  await waitFor(() =>
    expect(
      (screen.getByRole("button", { name: "Submit" }) as HTMLButtonElement)
        .disabled,
    ).toBe(false),
  );
  expect(
    screen.getAllByRole("radio").filter((r) => (r as HTMLInputElement).checked),
  ).toHaveLength(6);
  fail = false;
  fireEvent.click(screen.getByRole("button", { name: "Submit" }));
  await screen.findByRole("heading", { name: "Feedback Survey Complete" });
});

test.each([
  ["NOT_OPEN", /becomes available after/],
  ["CLOSED", /Submissions are closed/],
  ["SUBMITTED", /already submitted feedback/],
  ["UNCONFIGURED", /not been configured correctly/],
] as const)("%s surveys cannot be submitted", async (status, message) => {
  survey.status = status;
  await open();
  expect(screen.getByText(message)).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Submit" })).toBeNull();
});

test("switching cycles asks before discarding unsent answers", async () => {
  others = [
    {
      cycleId: 2,
      label: "Fall 2025",
      observationDeadline: "2025-11-01",
      feedbackDeadline: "2025-11-10",
      status: "CLOSED",
    },
  ];
  const confirm = spyOn(window, "confirm").mockReturnValue(false);
  // Not open(): "Fall 2026" is both the subtitle and an option here.
  window.history.pushState({}, "", "/faculty/feedback?cycle=1");
  render(<App />);
  const cycle = (await screen.findByLabelText(
    "Evaluation cycle",
  )) as HTMLSelectElement;
  const checked = () =>
    screen.getAllByRole("radio").filter((r) => (r as HTMLInputElement).checked);
  fireEvent.click(
    within(screen.getByRole("group", { name: feedbackQuestions[0] })).getByRole(
      "radio",
      { name: "4" },
    ),
  );

  fireEvent.change(cycle, { target: { value: "2" } });
  expect(confirm).toHaveBeenCalledTimes(1);
  expect(cycle.value).toBe("1");
  expect(checked()).toHaveLength(1);

  confirm.mockReturnValue(true);
  fireEvent.change(cycle, { target: { value: "2" } });
  await screen.findByText(/Submissions are closed/);
  expect(cycle.value).toBe("2");

  // Nothing is unsent now, so switching back does not ask.
  fireEvent.change(cycle, { target: { value: "1" } });
  expect(confirm).toHaveBeenCalledTimes(2);
  await waitFor(() => expect(checked()).toHaveLength(0));
  expect(screen.getAllByRole("radio").length).toBeGreaterThan(0);
});
