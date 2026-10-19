import type { ReactNode } from "react";
import { Link } from "react-router-dom";

const TONES = {
  green: "bg-brand-green",
  orange: "bg-brand-orange",
};

/**
 * The large filled button from the Figma designs. The label sits on the left,
 * and callers set the width with `className` (for example `min-w-52.5` for 210px).
 * Pass `to` for a link, or `onClick` for an action.
 */
export function ActionButton({
  tone,
  to,
  onClick,
  ariaLabel,
  className = "",
  children,
}: {
  tone: keyof typeof TONES;
  to?: string;
  onClick?: () => void;
  ariaLabel?: string;
  className?: string;
  children: ReactNode;
}) {
  const classes = `inline-flex items-center rounded-lg px-5 py-3.25 text-white hover:opacity-90 ${TONES[tone]} ${className}`;

  if (to) {
    return (
      <Link to={to} aria-label={ariaLabel} className={classes}>
        {children}
      </Link>
    );
  }
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={ariaLabel}
      className={classes}
    >
      {children}
    </button>
  );
}
