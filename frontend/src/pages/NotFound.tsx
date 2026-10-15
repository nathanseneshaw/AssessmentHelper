import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { HOME_PATH } from "../navConfig";

export function NotFound() {
  const { user } = useAuth();
  const home = user ? HOME_PATH[user.role] : "/login";
  return (
    <div className="mx-auto mt-24 max-w-md text-center">
      <h1 className="font-bold text-3xl">Page not found</h1>
      <p className="mt-2 text-ink/60">That address doesn't exist.</p>
      <Link to={home} className="mt-6 inline-block text-brand-green underline">
        Go home
      </Link>
    </div>
  );
}
