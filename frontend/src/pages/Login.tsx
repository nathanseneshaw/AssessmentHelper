import { Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { HOME_PATH, type Role } from "../navConfig";

export function Login() {
  const { user, login } = useAuth();
  const navigate = useNavigate();

  if (user) {
    return <Navigate to={HOME_PATH[user.role]} replace />;
  }

  const pick = (role: Role) => {
    login(role);
    navigate(HOME_PATH[role], { replace: true });
  };

  return (
    <div className="mx-auto mt-24 max-w-md rounded-xl border border-line bg-white p-8">
      <h1 className="font-bold text-3xl">Assessment Helper</h1>
      <p className="mt-1 mb-6 text-ink/60">
        Demo login. Choose a role to continue.
      </p>
      <div className="flex flex-col gap-3">
        <button
          type="button"
          onClick={() => pick("ac")}
          className="rounded-md bg-brand-green px-4 py-3 font-medium text-white hover:opacity-90"
        >
          Continue as AC member
        </button>
        <button
          type="button"
          onClick={() => pick("professor")}
          className="rounded-md bg-brand-orange px-4 py-3 font-medium text-white hover:opacity-90"
        >
          Continue as Professor
        </button>
      </div>
    </div>
  );
}
