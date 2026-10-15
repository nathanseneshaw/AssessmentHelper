export type Role = "ac" | "professor";

export interface NavItem {
  label: string;
  to: string;
  end?: boolean;
}

export const BREADCRUMB: Record<Role, string> = {
  ac: "Assessment Helper / Assessment Committee",
  professor: "Assessment Helper",
};

export const HOME_PATH: Record<Role, string> = {
  ac: "/ac",
  professor: "/faculty",
};

export const NAV_ITEMS: Record<Role, NavItem[]> = {
  ac: [
    { label: "Overview", to: "/ac", end: true },
    { label: "Faculty Evaluations", to: "/ac/evaluations" },
    { label: "Signups", to: "/ac/signups" },
    { label: "Observer Matching", to: "/ac/matching" },
    { label: "Observations", to: "/ac/observations" },
    { label: "Form Template", to: "/ac/form-template" },
    { label: "Reports & KPIs", to: "/ac/reports" },
  ],
  professor: [],
};
