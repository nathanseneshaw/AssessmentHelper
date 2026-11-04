import { afterEach, expect, mock, spyOn, test } from "bun:test";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "../auth/AuthContext";
import { ACCOUNTS } from "../test/fakeBackend";
import { LoginPage } from "./LoginPage";

const OLIVIA = {
  userId: 21,
  professorId: 21,
  schoolId: 1,
  name: "Olivia Observee",
  email: "olivia.observee@test.edu",
  roles: ["PROFESSOR"],
};
const PASSWORD = "Observe123!";

/** Every sign-in the page sent, as the form fields it posted. */
let logins: URLSearchParams[] = [];

function problem(status: number, detail: string) {
  return Response.json(
    { status, detail },
    { status, headers: { "Content-Type": "application/problem+json" } },
  );
}

/** Stands in for the backend's auth endpoints. Nobody is signed in to start with. */
function backend(login: (form: URLSearchParams) => Response) {
  logins = [];
  spyOn(globalThis, "fetch").mockImplementation((async (input, init) => {
    const path = new URL(String(input)).pathname;
    if (path === "/api/auth/me") return problem(401, "Sign in to continue.");
    if (path === "/api/auth/csrf") return new Response(null, { status: 204 });
    if (path === "/api/auth/login") {
      const form = new URLSearchParams(String(init?.body));
      logins.push(form);
      return login(form);
    }
    return problem(404, "Not found");
  }) as typeof fetch);
}

const oliviaSignsIn = (form: URLSearchParams) =>
  form.get("email") === OLIVIA.email && form.get("password") === PASSWORD
    ? Response.json(OLIVIA)
    : problem(401, "Incorrect email or password.");

afterEach(() => {
  cleanup();
  mock.restore();
});

async function renderLogin() {
  render(
    <MemoryRouter initialEntries={["/login"]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/faculty" element={<h1>Faculty home</h1>} />
          <Route path="/ac" element={<h1>Committee home</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
  const email = (await screen.findByLabelText("Email")) as HTMLInputElement;
  const password = screen.getByLabelText("Password") as HTMLInputElement;
  return { email, password };
}

async function signInAs(email: string, password: string) {
  const fields = await renderLogin();
  fireEvent.change(fields.email, { target: { value: email } });
  fireEvent.change(fields.password, { target: { value: password } });
  fireEvent.click(screen.getByRole("button", { name: "Sign In" }));
}

test("an empty form says both fields are required and sends nothing", async () => {
  backend(oliviaSignsIn);
  await renderLogin();

  fireEvent.click(screen.getByRole("button", { name: "Sign In" }));

  expect(screen.getAllByRole("alert").map((a) => a.textContent)).toEqual([
    "Email is required.",
    "Password is required.",
  ]);
  expect(logins).toHaveLength(0);
});

test("an email without a domain is refused and keeps focus on the email", async () => {
  backend(oliviaSignsIn);
  await signInAs("olivia.observee", PASSWORD);

  expect(screen.getByRole("alert").textContent).toBe(
    "Please enter a valid email address.",
  );
  expect(document.activeElement).toBe(screen.getByLabelText("Email"));
  expect(logins).toHaveLength(0);
});

test("a missing password moves focus to the password", async () => {
  backend(oliviaSignsIn);
  await signInAs(OLIVIA.email, "");

  expect(screen.getByRole("alert").textContent).toBe("Password is required.");
  expect(document.activeElement).toBe(screen.getByLabelText("Password"));
  expect(logins).toHaveLength(0);
});

test("a faculty member signs in and goes to the faculty home", async () => {
  backend(oliviaSignsIn);
  await signInAs(OLIVIA.email, PASSWORD);

  await screen.findByRole("heading", { name: "Faculty home" });
  expect(logins).toHaveLength(1);
});

test("a committee member signs in and goes to the committee home", async () => {
  backend(() => Response.json(ACCOUNTS.ac.user));
  await signInAs(ACCOUNTS.ac.user.email, ACCOUNTS.ac.password);

  await screen.findByRole("heading", { name: "Committee home" });
});

test("a wrong password shows the backend's message", async () => {
  backend(oliviaSignsIn);
  await signInAs(OLIVIA.email, "wrong");

  expect((await screen.findByRole("alert")).textContent).toBe(
    "Incorrect email or password.",
  );
  expect(screen.queryByRole("heading", { name: "Faculty home" })).toBeNull();
});

test("a server failure shows a general message, not the server's", async () => {
  backend(() => problem(500, "NullPointerException at AuthController"));
  await signInAs(OLIVIA.email, PASSWORD);

  expect((await screen.findByRole("alert")).textContent).toBe(
    "Unable to sign in. Please try again.",
  );
  // The button is usable again for another try.
  expect(
    (screen.getByRole("button", { name: "Sign In" }) as HTMLButtonElement)
      .disabled,
  ).toBe(false);
});
