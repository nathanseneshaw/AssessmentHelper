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
import { API_URL } from "../../api/client";
import type { ImportResult } from "../../api/imports";
import { metrics } from "../../test/acDashboardFixtures";
import { fakeBackend, resetBackend, signInAs } from "../../test/fakeBackend";
import { ac } from "../../test/observerFixtures";

const EMPTY_QUEUE = () =>
  Response.json({ cycleId: 1, cycleLabel: "Fall 2026", count: 0, faculty: [] });

const PROFESSORS_CSV = [
  "email,first_name,last_name,department_code,rank,hire_date,active",
  "wren.halloway@example.edu,Wren,Halloway,CS,ASSOCIATE,2019-08-15,true",
  "jasper.quill@example.edu,Jasper,Quill,CS,ASSISTANT,2024-08-19,",
  "marisol.fenwick@example.edu,Marisol,Fenwick,ATEC,FULL,8/1/2012,yes",
].join("\n");

const savedImport: ImportResult = { saved: true, rowCount: 3, errors: [] };

const failedImport: ImportResult = {
  saved: false,
  rowCount: 3,
  errors: [
    { row: null, column: "hire_dat", message: "Unknown column 'hire_dat'." },
    {
      row: 14,
      column: "department_code",
      message: "Unknown department code 'CSS'.",
    },
  ],
};

interface ImportRequest {
  url: URL;
  init: RequestInit | undefined;
}

let requests: ImportRequest[];
let answerImport: (url: URL) => Response | Promise<Response>;
let facultyDue: number;

beforeEach(() => {
  requests = [];
  facultyDue = metrics.facultyDue;
  answerImport = () => Response.json(savedImport);
  const imports = (init: RequestInit | undefined, url: URL) => {
    requests.push({ url, init });
    return answerImport(url);
  };
  fakeBackend({
    "/api/due-evaluations": EMPTY_QUEUE,
    "/api/overdue-evaluations": EMPTY_QUEUE,
    "/api/observer-eligibility/ac": () => Response.json(ac),
    "/api/ac-dashboard/metrics": () =>
      Response.json({ ...metrics, facultyDue }),
    "/api/imports/professors": imports,
    "/api/imports/sections": imports,
  });
  signInAs("ac");
});

afterEach(() => {
  cleanup();
  resetBackend();
});

async function openDashboard() {
  window.history.pushState({}, "", "/ac");
  render(<App />);
  const upload = await screen.findByRole("button", { name: "Upload CSV" });
  await waitFor(() => expect(document.body.textContent).not.toMatch(/Loading/));
  return upload;
}

async function openDialog() {
  const button = await openDashboard();
  button.focus();
  fireEvent.click(button);
  return screen.getByRole("dialog", { name: "Upload CSV" });
}

function csvFile(name = "professors.csv", content = PROFESSORS_CSV) {
  return new File([content], name, { type: "text/csv" });
}

function pick(dialog: HTMLElement, file: File) {
  fireEvent.change(within(dialog).getByLabelText("Choose CSV file"), {
    target: { files: [file] },
  });
}

function button(dialog: HTMLElement, name: string) {
  return within(dialog).getByRole("button", { name }) as HTMLButtonElement;
}

async function importFile(dialog: HTMLElement, file: File, heading: string) {
  pick(dialog, file);
  fireEvent.click(button(dialog, "Import"));
  return within(dialog).findByRole("heading", { name: heading });
}

function tableRows(dialog: HTMLElement) {
  return within(dialog)
    .getAllByRole("row")
    .map((row) =>
      Array.from(row.querySelectorAll("th, td")).map((cell) =>
        cell.textContent?.trim(),
      ),
    );
}

function facultyDueShown() {
  const card = screen.getByText("Faculty Due This Cycle", {
    selector: "p",
  }).parentElement;
  return card?.querySelectorAll("p")[1]?.textContent;
}

test("the dialog offers both import types with their templates", async () => {
  const dialog = await openDialog();
  const professors = within(dialog).getByRole("radio", { name: "Professors" });
  expect((professors as HTMLInputElement).checked).toBe(true);
  expect(
    within(dialog).getByRole("radio", { name: "Class sections" }),
  ).toBeDefined();
  expect(
    within(dialog)
      .getByRole("link", { name: "Download template for professors" })
      .getAttribute("href"),
  ).toBe(`${API_URL}/api/imports/professors/template`);
  expect(
    within(dialog)
      .getByRole("link", { name: "Download template for class sections" })
      .getAttribute("href"),
  ).toBe(`${API_URL}/api/imports/sections/template`);
  expect(button(dialog, "Import").disabled).toBe(true);
  pick(dialog, csvFile());
  expect(button(dialog, "Import").disabled).toBe(false);
});

test("importing posts the file as form data to the chosen import and saves it", async () => {
  const dialog = await openDialog();
  fireEvent.click(
    within(dialog).getByRole("radio", { name: "Class sections" }),
  );
  const file = csvFile("sections.csv", "course_prefix,course_number\n");
  await importFile(dialog, file, "Import complete");

  expect(requests).toHaveLength(1);
  const [request] = requests;
  expect(request?.url.pathname).toBe("/api/imports/sections");
  expect(request?.init?.method).toBe("POST");
  const body = request?.init?.body;
  expect(body).toBeInstanceOf(FormData);
  const sent = (body as FormData).get("file") as File;
  expect(sent.name).toBe("sections.csv");
  expect(await sent.text()).toBe("course_prefix,course_number\n");
  expect(new Headers(request?.init?.headers).has("Content-Type")).toBe(false);
});

test("an import shows what it saved and refreshes the dashboard", async () => {
  const dialog = await openDialog();
  expect(facultyDueShown()).toBe("13");
  facultyDue = 16;
  await importFile(dialog, csvFile(), "Import complete");

  expect(
    within(dialog).getByText("Saved 3 rows from professors.csv."),
  ).toBeDefined();
  expect(within(dialog).queryByRole("table")).toBeNull();
  expect(within(dialog).queryByRole("button", { name: "Import" })).toBeNull();
  await waitFor(() => expect(facultyDueShown()).toBe("16"));

  fireEvent.click(button(dialog, "Done"));
  expect(screen.queryByRole("dialog")).toBeNull();
});

test("a file with problems saves nothing, lists each problem and needs choosing again", async () => {
  answerImport = () => Response.json(failedImport);
  const dialog = await openDialog();
  await importFile(dialog, csvFile(), "Problems found");

  expect(
    within(dialog).getByText(
      /Found 2 problems\. Nothing was imported\. Fix them in professors\.csv/,
    ),
  ).toBeDefined();
  expect(tableRows(dialog)).toEqual([
    ["Row", "Column", "Problem"],
    ["File", "hire_dat", "Unknown column 'hire_dat'."],
    ["14", "department_code", "Unknown department code 'CSS'."],
  ]);
  expect(button(dialog, "Import").disabled).toBe(true);

  pick(dialog, csvFile());
  expect(
    within(dialog).queryByRole("heading", { name: "Problems found" }),
  ).toBeNull();
  expect(within(dialog).queryByRole("table")).toBeNull();
  expect(button(dialog, "Import").disabled).toBe(false);
});

test("controls are disabled while an import runs, and a failed request shows its message", async () => {
  let answer: (response: Response) => void = () => {};
  answerImport = () =>
    new Promise<Response>((resolve) => {
      answer = resolve;
    });
  const dialog = await openDialog();
  pick(dialog, csvFile());
  fireEvent.click(button(dialog, "Import"));

  await waitFor(() => expect(requests).toHaveLength(1));
  expect(button(dialog, "Importing…").disabled).toBe(true);
  expect(button(dialog, "Close").disabled).toBe(true);
  expect(
    (
      within(dialog).getByRole("group", {
        name: "What are you importing?",
      }) as HTMLFieldSetElement
    ).disabled,
  ).toBe(true);
  expect(
    (within(dialog).getByLabelText("Choose CSV file") as HTMLInputElement)
      .disabled,
  ).toBe(true);
  answer(
    Response.json(
      { status: 400, detail: "Required part 'file' is not present." },
      { status: 400 },
    ),
  );
  expect((await within(dialog).findByRole("alert")).textContent).toBe(
    "Required part 'file' is not present.",
  );
  expect(button(dialog, "Import").disabled).toBe(false);
});
