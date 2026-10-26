import { Navigate, Outlet, useLocation } from "react-router-dom";
import type { Role } from "../navConfig";
import { useAuth } from "./AuthContext";

export function RequireRole({ allow }: { allow: Role }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return null;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  if (user.role !== allow) {
    return <Navigate to="/forbidden" replace />;
  }
  return <Outlet />;
}
