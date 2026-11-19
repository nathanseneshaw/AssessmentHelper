import { afterEach, expect, test } from "bun:test";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { ScreenshotMarks } from "./ScreenshotMarks";

afterEach(cleanup);

function page() {
  return (
    <>
      <section data-testid="card">
        <h2>Sign-offs</h2>
        <p>Not signed yet</p>
      </section>
      <button type="button" id="sign">
        Sign off
      </button>
      <ScreenshotMarks />
    </>
  );
}

const labels = () =>
  [...document.querySelectorAll("[data-mark-label]")].map(
    (el) => el.textContent,
  );

test("marks elements found by text or selector and clears them", async () => {
  render(page());
  act(() =>
    window.screenshotMarks?.show([
      { text: "Sign-offs", closest: "section", label: "New: sign-offs" },
      { selector: "#sign", label: "New: sign off" },
    ]),
  );
  act(() =>
    window.screenshotMarks?.add({
      area: { left: 10, top: 20, width: 100, height: 50 },
      label: "Drawn",
    }),
  );

  await waitFor(() =>
    expect(labels()).toEqual(["New: sign-offs", "New: sign off", "Drawn"]),
  );
  const marks = document.querySelector("[data-screenshot-marks]");
  expect(marks?.getAttribute("aria-hidden")).toBe("true");
  expect(screen.getByTestId("card").contains(marks)).toBe(false);

  act(() => window.screenshotMarks?.clear());
  await waitFor(() =>
    expect(document.querySelector("[data-screenshot-marks]") === null).toBe(
      true,
    ),
  );
});

test("a mark whose element is missing draws nothing", async () => {
  render(page());
  act(() =>
    window.screenshotMarks?.show([
      { text: "Not on this page", label: "Missing" },
      { text: "Not signed yet" },
    ]),
  );

  await waitFor(() =>
    expect(
      document.querySelectorAll("[data-screenshot-marks] > div"),
    ).toHaveLength(1),
  );
  expect(labels()).toEqual([]);
});

test("the console API goes away when the overlay unmounts", () => {
  const { unmount } = render(page());
  expect(window.screenshotMarks).toBeDefined();
  unmount();
  expect(window.screenshotMarks).toBeUndefined();
});
