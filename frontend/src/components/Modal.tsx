import { type ReactNode, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";

export function Modal({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children?: ReactNode;
}) {
  const navigate = useNavigate();

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        navigate("/faculty");
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [navigate]);

  return (
    <div className="fixed inset-0 z-20 flex items-center justify-center p-4">
      <Link
        to="/faculty"
        aria-label="Close"
        className="absolute inset-0 bg-ink/40"
      />
      <dialog
        open
        aria-labelledby="modal-title"
        className="relative m-0 w-full max-w-xl rounded-xl border border-line bg-white p-6 text-ink shadow-xl"
      >
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="modal-title" className="font-bold text-xl">
              {title}
            </h2>
            {subtitle && <p className="mt-1 text-ink/60 text-sm">{subtitle}</p>}
          </div>
          <Link
            to="/faculty"
            className="rounded-md border border-line px-3 py-1 text-sm hover:bg-mist"
          >
            Close
          </Link>
        </div>
        <div className="mt-5">{children}</div>
      </dialog>
    </div>
  );
}
