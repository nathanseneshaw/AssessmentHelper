import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { HOME_PATH } from "../navConfig";

export function Forbidden() {
  const { user } = useAuth();
  const home = user ? HOME_PATH[user.role] : "/login";
  return (
    <div className="mx-auto mt-24 max-w-md text-center">
      <h1 className="font-bold text-3xl">No access</h1>
      <p className="mt-2 text-ink/60">Your role can't view that page.</p>
      <Link to={home} className="mt-6 inline-block text-brand-green underline">
        Back to my dashboard
      </Link>
    </div>
  );
}
