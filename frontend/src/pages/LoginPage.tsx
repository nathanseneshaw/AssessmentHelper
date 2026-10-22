import { type FormEvent, useRef, useState } from "react";
import { Navigate } from "react-router-dom";
import { ApiError } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { HOME_PATH } from "../navConfig";

export function LoginPage() {
  const { user, loading, login } = useAuth();
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const [showPassword, setShowPassword] = useState(false);

  const [emailError, setEmailError] = useState("");
  const [passwordError, setPasswordError] = useState("");
  const [authError, setAuthError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSubmitting) return;

    // Clear previous errors
    setEmailError("");
    setPasswordError("");
    setAuthError("");

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
      if (!email.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
        emailRef.current?.focus();
      } else {
        passwordRef.current?.focus();
      }
      return;
    }

    setIsSubmitting(true);
    try {
      await login(email.trim(), password);
    } catch (error) {
      setAuthError(
        error instanceof ApiError && error.status === 401
          ? error.message
          : "Unable to sign in. Please try again.",
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  if (loading) {
    return null;
  }
  if (user) {
    return <Navigate to={HOME_PATH[user.role]} replace />;
  }

  return (
    <main className="flex min-h-dvh flex-col items-center justify-center px-4 py-8 text-neutral-900 sm:px-6 sm:py-12">
      <section
        aria-labelledby="login-title"
        className="w-full max-w-md overflow-hidden rounded-2xl border border-neutral-200 bg-white shadow-xl shadow-neutral-900/5"
      >
        <div className="h-1.5 bg-utd-orange" />
        <div className="p-5 sm:p-8">
          <div
            aria-hidden="true"
            className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-xl bg-utd-green-light text-lg font-bold text-utd-green-dark"
          >
            UTD
          </div>
          <p className="mb-3 text-center text-sm font-semibold text-utd-green">
            The University of Texas at Dallas
          </p>
          <p className="text-center text-xs font-semibold uppercase tracking-widest text-utd-green">
            Faculty & Assessment Committee
          </p>
          <h1
            id="login-title"
            className="mt-3 text-center text-2xl font-bold tracking-tight text-utd-green sm:text-3xl"
          >
            Assessment Helper
          </h1>

          <p className="mt-2 text-center text-sm leading-6 text-neutral-600">
            Sign in to the assessment workspace.
          </p>

          <form
            onSubmit={handleSubmit}
            noValidate
            aria-busy={isSubmitting}
            className="mt-8 space-y-5"
          >
            {/* Email field */}
            <div>
              <label htmlFor="email" className="block mb-2 font-medium">
                Email
              </label>

              <input
                id="email"
                ref={emailRef}
                name="email"
                type="email"
                autoCapitalize="none"
                spellCheck={false}
                autoComplete="username"
                required
                disabled={isSubmitting}
                value={email}
                onChange={(event) => {
                  setEmail(event.target.value);
                  setEmailError("");
                  setAuthError("");
                }}
                placeholder="Enter your email"
                aria-invalid={Boolean(emailError)}
                aria-describedby={emailError ? "email-error" : undefined}
                className="w-full min-w-0 rounded-lg border border-neutral-300 bg-white p-3 text-base transition-colors placeholder:text-neutral-400 hover:border-neutral-400 focus:border-utd-green aria-invalid:border-red-600 disabled:bg-neutral-50 disabled:text-neutral-500"
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
              <label htmlFor="password" className="block mb-2 font-medium">
                Password
              </label>

              <div className="relative">
                <input
                  id="password"
                  ref={passwordRef}
                  name="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  required
                  disabled={isSubmitting}
                  value={password}
                  onChange={(event) => {
                    setPassword(event.target.value);
                    setPasswordError("");
                    setAuthError("");
                  }}
                  placeholder="Enter your password"
                  aria-invalid={Boolean(passwordError)}
                  aria-describedby={
                    passwordError ? "password-error" : undefined
                  }
                  className="w-full min-w-0 rounded-lg border border-neutral-300 bg-white p-3 pr-20 text-base transition-colors placeholder:text-neutral-400 hover:border-neutral-400 focus:border-utd-green aria-invalid:border-red-600 disabled:bg-neutral-50 disabled:text-neutral-500"
                />

                <button
                  type="button"
                  disabled={isSubmitting}
                  onClick={() => setShowPassword(!showPassword)}
                  aria-pressed={showPassword}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  className="absolute right-1 top-1/2 min-h-11 min-w-16 -translate-y-1/2 rounded-md px-2 text-sm font-semibold text-utd-green hover:bg-utd-green-light disabled:opacity-60"
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
              disabled={isSubmitting}
              className="min-h-12 w-full rounded-lg bg-utd-green p-3 font-semibold text-white shadow-sm transition-colors hover:bg-utd-green-dark disabled:cursor-wait disabled:opacity-60"
            >
              {isSubmitting ? "Signing in…" : "Sign In"}
            </button>

            {authError && (
              <p
                role="alert"
                className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm leading-6 text-red-800"
              >
                {authError}
              </p>
            )}
          </form>
          <details className="mt-6 rounded-xl border border-neutral-200 bg-neutral-50 text-sm text-neutral-600">
            <summary className="min-h-11 cursor-pointer rounded-xl px-4 py-3 font-semibold text-neutral-800">
              Demo accounts
            </summary>
            <div className="space-y-3 px-4 pb-4 leading-6">
              <p>Sample accounts:</p>
              <div>
                <p className="font-semibold text-neutral-800">
                  Faculty (Bob Martinez)
                </p>
                <p className="break-all">bob.martinez@example.edu</p>
                <p>
                  Password: <code>Faculty123!</code>
                </p>
              </div>
              <div>
                <p className="font-semibold text-neutral-800">
                  Assessment Committee (Alice Nguyen)
                </p>
                <p className="break-all">alice.nguyen@example.edu</p>
                <p>
                  Password: <code>Committee123!</code>
                </p>
              </div>
            </div>
          </details>
        </div>
      </section>
    </main>
  );
}
