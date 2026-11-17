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
import {
  type FeedbackSurvey,
  feedbackPath,
  feedbackQuestions,
} from "../../api/feedback";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";

let survey: FeedbackSurvey;
let sent: { ratings: number[]; comments: string } | null;
let fail: boolean;

beforeEach(() => {
  sent = null;
  fail = false;
  survey = {
    cycleId: 1,
    label: "Fall 2026",
    observationDeadline: "2026-11-01",
    feedbackDeadline: "2026-11-10",
    status: "AVAILABLE",
  };
  fakeBackend({
    [feedbackPath]: () => Response.json([survey]),
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

function submitButton() {
  return screen.getByRole("button", { name: "Submit" }) as HTMLButtonElement;
}

test("one survey is submitted when all six ratings are given", async () => {
  await open();
  expect(submitButton().disabled).toBe(true);
  answerAll();
  fireEvent.change(screen.getByLabelText(/Additional Comments/), {
    target: { value: "Helpful process" },
  });
  fireEvent.click(submitButton());
  await screen.findByRole("heading", { name: "Feedback Survey Complete" });
  expect(sent).toEqual({
    ratings: [1, 2, 3, 4, 5, 1],
    comments: "Helpful process",
  });

  // Reopened, the survey shows the submitted answers read-only.
  cleanup();
  await open();
  const dialog = within(screen.getByRole("dialog"));
  expect(dialog.getByText("Helpful process")).toBeDefined();
  expect(dialog.queryByRole("button", { name: "Submit" })).toBeNull();
});

test("answers are kept for retry when submitting fails", async () => {
  fail = true;
  await open();
  answerAll();
  fireEvent.click(submitButton());
  await within(screen.getByRole("dialog")).findByRole("alert");
  await waitFor(() => expect(submitButton().disabled).toBe(false));
  expect(
    screen.getAllByRole("radio").filter((r) => (r as HTMLInputElement).checked),
  ).toHaveLength(6);

  fail = false;
  fireEvent.click(submitButton());
  await screen.findByRole("heading", { name: "Feedback Survey Complete" });
});

test.each([
  ["NOT_OPEN", /becomes available after/],
  ["CLOSED", /Submissions are closed/],
] as const)(
  "Submit is hidden when the survey is %s",
  async (status, message) => {
    survey.status = status;
    await open();
    expect(screen.getByText(message)).toBeDefined();
    expect(screen.queryByRole("button", { name: "Submit" })).toBeNull();
  },
);
