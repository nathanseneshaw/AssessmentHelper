import { Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import type { AuthUser, UserRole } from "../auth/mockAuth";
import { HOME_PATH, type Role } from "../navConfig";
import { LoginPage } from "./LoginPage";

// mockAuth and the router use different names for the same two roles.
const ROLE_FOR: Record<UserRole, Role> = {
  faculty: "professor",
  "assessment-committee": "ac",
};

export function Login() {
  const { user, login } = useAuth();
  const navigate = useNavigate();

  if (user) {
    return <Navigate to={HOME_PATH[user.role]} replace />;
  }

  const handleLogin = (account: AuthUser) => {
    const role = ROLE_FOR[account.role];
    login({ role, name: account.name });
    navigate(HOME_PATH[role], { replace: true });
  };

  return <LoginPage onLogin={handleLogin} />;
}
