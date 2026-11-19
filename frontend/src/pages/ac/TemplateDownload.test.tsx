import { afterEach, expect, spyOn, test } from "bun:test";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { setDemoSession } from "../../demo/tabSession";
import { fakeBackend, resetBackend } from "../../test/fakeBackend";
import { TemplateDownload } from "./TemplateDownload";

afterEach(() => {
  cleanup();
  resetBackend();
  sessionStorage.clear();
});

test("downloads each template with the demo session and a CSV filename", async () => {
  const requests: string[] = [];
  fakeBackend(
    Object.fromEntries(
      ["professors", "sections"].map((kind) => [
        `/api/imports/${kind}/template`,
        (init: RequestInit | undefined) => {
          expect(new Headers(init?.headers).get("X-Demo-Session")).toBe(
            "demo-token",
          );
          expect(init?.credentials).toBe("include");
          requests.push(kind);
          return new Response("header\r\n", {
            headers: { "Content-Type": "text/csv" },
          });
        },
      ]),
    ),
  );
  setDemoSession("demo-token");
  const create = spyOn(URL, "createObjectURL").mockReturnValue("blob:test");
  const revoke = spyOn(URL, "revokeObjectURL").mockImplementation(() => {});
  const names: string[] = [];
  const click = spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(
    function (this: HTMLAnchorElement) {
      names.push(this.download);
    },
  );
  try {
    render(
      <>
        <TemplateDownload kind="professors" />
        <TemplateDownload kind="sections" />
      </>,
    );
    fireEvent.click(
      screen.getByRole("button", { name: "Download template for professors" }),
    );
    fireEvent.click(
      screen.getByRole("button", {
        name: "Download template for class sections",
      }),
    );
    await waitFor(() =>
      expect(names).toEqual([
        "professors-template.csv",
        "sections-template.csv",
      ]),
    );
    expect(requests).toEqual(["professors", "sections"]);
    expect(revoke).toHaveBeenCalledTimes(2);
  } finally {
    create.mockRestore();
    revoke.mockRestore();
    click.mockRestore();
  }
});

test("shows a download error without navigating away", async () => {
  fakeBackend({
    "/api/imports/professors/template": () =>
      Response.json({ detail: "Template unavailable" }, { status: 503 }),
  });
  render(<TemplateDownload kind="professors" />);
  fireEvent.click(
    screen.getByRole("button", { name: "Download template for professors" }),
  );
  expect((await screen.findByRole("alert")).textContent).toBe(
    "Template unavailable",
  );
});
