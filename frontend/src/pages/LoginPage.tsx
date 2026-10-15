
import { useState, type FormEvent } from "react";

export function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const [showPassword, setShowPassword] = useState(false);

  const [emailError, setEmailError] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [message, setMessage] = useState("");

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    // Clear previous errors and messages
    setEmailError("");
    setPasswordError("");
    setMessage("");

    let hasError = false;

    // Validate email
    if (!email.trim()) {
      setEmailError("Email is required.");
      hasError = true;
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setEmailError("Please enter a valid email address.");
      hasError = true;
    }

    // Validate password
    if (!password) {
      setPasswordError("Password is required.");
      hasError = true;
    }

    // Stop submission if validation fails
    if (hasError) {
      return;
    }

    // Temporary behavior until backend authentication is connected
    setMessage(
      "Login form validated. Authentication is not yet connected."
    );
  }

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-100 px-4 text-slate-900">
      <section className="w-full max-w-md rounded-xl bg-white p-8 shadow-lg">
        <h1 className="text-3xl font-bold text-center">
          Assessment Helper
        </h1>

        <p className="mt-2 text-center text-slate-500">
          Faculty & Assessment Committee Portal
        </p>

        <form
          onSubmit={handleSubmit}
          noValidate
          className="mt-8 space-y-5"
        >
          {/* Email field */}
          <div>
            <label
              htmlFor="email"
              className="block mb-2 font-medium"
            >
              Email
            </label>

            <input
              id="email"
              type="email"
              autoComplete="username"
              required
              value={email}
              onChange={(event) => {
                setEmail(event.target.value);
                setEmailError("");
              }}
              placeholder="Enter your email"
              aria-invalid={Boolean(emailError)}
              aria-describedby={emailError ? "email-error" : undefined}
              className="w-full rounded-lg border border-slate-300 p-3"
            />

            {emailError && (
              <p
                id="email-error"
                role="alert"
                className="mt-1 text-sm text-red-600"
              >
                {emailError}
              </p>
            )}
          </div>

          {/* Password field */}
          <div>
            <label
              htmlFor="password"
              className="block mb-2 font-medium"
            >
              Password
            </label>

            <div className="relative">
              <input
                id="password"
                type={showPassword ? "text" : "password"}
                autoComplete="current-password"
                required
                value={password}
                onChange={(event) => {
                  setPassword(event.target.value);
                  setPasswordError("");
                }}
                placeholder="Enter your password"
                aria-invalid={Boolean(passwordError)}
                aria-describedby={
                  passwordError ? "password-error" : undefined
                }
                className="w-full rounded-lg border border-slate-300 p-3 pr-16"
              />

              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                aria-label={
                  showPassword ? "Hide password" : "Show password"
                }
                className="absolute right-3 top-1/2 -translate-y-1/2 text-sm font-medium text-blue-700 hover:underline"
              >
                {showPassword ? "Hide" : "Show"}
              </button>
            </div>

            {passwordError && (
              <p
                id="password-error"
                role="alert"
                className="mt-1 text-sm text-red-600"
              >
                {passwordError}
              </p>
            )}
          </div>

          {/* Sign-in button */}
          <button
            type="submit"
            className="w-full rounded-lg bg-blue-700 p-3 font-semibold text-white hover:bg-blue-800"
          >
            Sign In
          </button>

          {/* Temporary status message */}
          {message && (
            <p
              role="status"
              className="text-center text-sm text-slate-600"
            >
              {message}
            </p>
          )}
        </form>
      </section>
    </main>
  );
}
