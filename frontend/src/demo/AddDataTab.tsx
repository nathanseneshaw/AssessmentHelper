import {
  type ChangeEvent,
  type FormEvent,
  useEffect,
  useId,
  useState,
} from "react";
import {
  addProfessor,
  addSection,
  type DemoAccount,
  type Department,
  getDepartments,
} from "../api/demo";
import { getEvaluationPolicies } from "../api/evaluations";

const LABEL = "block font-semibold text-sm";
const FIELD =
  "mt-1 w-full rounded-lg border border-line bg-white px-3 py-2 text-sm";
const SUBMIT =
  "mt-3 rounded-lg bg-brand-green px-4 py-2 font-semibold text-sm text-white hover:opacity-90 disabled:opacity-50";

interface Outcome {
  error: boolean;
  text: string;
}

type Field = HTMLInputElement | HTMLSelectElement;

function twoYearsAgo() {
  const date = new Date();
  date.setFullYear(date.getFullYear() - 2);
  return date.toISOString().slice(0, 10);
}

function idOrNull(value: string) {
  return value ? Number(value) : null;
}

function OutcomeText({ outcome }: { outcome: Outcome | null }) {
  if (!outcome) return null;
  return (
    <p
      role={outcome.error ? "alert" : "status"}
      className={`mt-2 text-sm ${outcome.error ? "text-brand-red" : "text-brand-green"}`}
    >
      {outcome.text}
    </p>
  );
}

async function attempt(
  action: () => Promise<string>,
  fallback: string,
): Promise<Outcome> {
  try {
    return { error: false, text: await action() };
  } catch (e) {
    return { error: true, text: e instanceof Error ? e.message : fallback };
  }
}

function ProfessorForm({
  departments,
  ranks,
  onAdded,
}: {
  departments: Department[];
  ranks: string[];
  onAdded: (account: DemoAccount) => void;
}) {
  const id = useId();
  const [form, setForm] = useState({
    firstName: "",
    lastName: "",
    departmentId: "",
    rank: "",
    hireDate: twoYearsAgo(),
  });
  const [saving, setSaving] = useState(false);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const change = (field: keyof typeof form) => (e: ChangeEvent<Field>) =>
    setForm({ ...form, [field]: e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setOutcome(
      await attempt(async () => {
        const account = await addProfessor({
          ...form,
          departmentId: idOrNull(form.departmentId),
          hireDate: form.hireDate || null,
        });
        setForm({ ...form, firstName: "", lastName: "" });
        onAdded(account);
        return `Added ${account.name} (${account.email}).`;
      }, "Could not add the professor."),
    );
    setSaving(false);
  }

  return (
    <form onSubmit={submit} aria-labelledby={`${id}-title`}>
      <h3 id={`${id}-title`} className="font-bold">
        Add a professor
      </h3>
      <div className="mt-2 grid grid-cols-2 gap-3">
        <div>
          <label htmlFor={`${id}-first`} className={LABEL}>
            First name
          </label>
          <input
            id={`${id}-first`}
            required
            value={form.firstName}
            onChange={change("firstName")}
            className={FIELD}
          />
        </div>
        <div>
          <label htmlFor={`${id}-last`} className={LABEL}>
            Last name
          </label>
          <input
            id={`${id}-last`}
            required
            value={form.lastName}
            onChange={change("lastName")}
            className={FIELD}
          />
        </div>
        <div className="col-span-2">
          <label htmlFor={`${id}-department`} className={LABEL}>
            Department
          </label>
          <select
            id={`${id}-department`}
            required
            value={form.departmentId}
            onChange={change("departmentId")}
            className={FIELD}
          >
            <option value="">Choose a department</option>
            {departments.map((d) => (
              <option key={d.departmentId} value={d.departmentId}>
                {d.departmentName}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor={`${id}-rank`} className={LABEL}>
            Rank
          </label>
          <select
            id={`${id}-rank`}
            required
            value={form.rank}
            onChange={change("rank")}
            className={FIELD}
          >
            <option value="">Choose</option>
            {ranks.map((rank) => (
              <option key={rank} value={rank}>
                {rank.charAt(0) + rank.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor={`${id}-hired`} className={LABEL}>
            Hire date
          </label>
          <input
            id={`${id}-hired`}
            type="date"
            value={form.hireDate}
            onChange={change("hireDate")}
            className={FIELD}
          />
        </div>
      </div>
      <button type="submit" disabled={saving} className={SUBMIT}>
        {saving ? "Adding…" : "Add professor"}
      </button>
      <OutcomeText outcome={outcome} />
    </form>
  );
}

function SectionForm({
  accounts,
  departments,
  onAdded,
}: {
  accounts: DemoAccount[];
  departments: Department[];
  onAdded: () => void;
}) {
  const id = useId();
  const [form, setForm] = useState({
    professorId: "",
    departmentId: "",
    courseNumber: "",
    courseName: "",
    sectionNumber: "001",
    meetingDays: "",
    startTime: "",
    endTime: "",
    location: "",
  });
  const [saving, setSaving] = useState(false);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  const change = (field: keyof typeof form) => (e: ChangeEvent<Field>) =>
    setForm({ ...form, [field]: e.target.value });
  const instructors = [...accounts].sort((a, b) =>
    a.name.localeCompare(b.name),
  );

  // Picking an instructor fills in their department, which is the usual case.
  function chooseInstructor(e: ChangeEvent<HTMLSelectElement>) {
    const professorId = e.target.value;
    const account = accounts.find((a) => String(a.professorId) === professorId);
    const department = departments.find(
      (d) => d.departmentName === account?.department,
    );
    setForm({
      ...form,
      professorId,
      departmentId: department
        ? String(department.departmentId)
        : form.departmentId,
    });
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setOutcome(
      await attempt(async () => {
        const section = await addSection({
          ...form,
          professorId: idOrNull(form.professorId),
          departmentId: idOrNull(form.departmentId),
        });
        onAdded();
        return `Added ${section.label} for ${section.semester}.`;
      }, "Could not add the section."),
    );
    setSaving(false);
  }

  return (
    <form onSubmit={submit} aria-labelledby={`${id}-title`}>
      <h3 id={`${id}-title`} className="font-bold">
        Add a course section
      </h3>
      <div className="mt-2 grid grid-cols-2 gap-3">
        <div className="col-span-2">
          <label htmlFor={`${id}-instructor`} className={LABEL}>
            Instructor
          </label>
          <select
            id={`${id}-instructor`}
            required
            value={form.professorId}
            onChange={chooseInstructor}
            className={FIELD}
          >
            <option value="">Choose an instructor</option>
            {instructors.map((a) => (
              <option key={a.professorId} value={a.professorId}>
                {a.name}
              </option>
            ))}
          </select>
        </div>
        <div className="col-span-2">
          <label htmlFor={`${id}-department`} className={LABEL}>
            Department
          </label>
          <select
            id={`${id}-department`}
            required
            value={form.departmentId}
            onChange={change("departmentId")}
            className={FIELD}
          >
            <option value="">Choose a department</option>
            {departments.map((d) => (
              <option key={d.departmentId} value={d.departmentId}>
                {d.departmentName}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor={`${id}-number`} className={LABEL}>
            Course number
          </label>
          <input
            id={`${id}-number`}
            required
            inputMode="numeric"
            placeholder="4485"
            value={form.courseNumber}
            onChange={change("courseNumber")}
            className={FIELD}
          />
        </div>
        <div>
          <label htmlFor={`${id}-section`} className={LABEL}>
            Section
          </label>
          <input
            id={`${id}-section`}
            required
            value={form.sectionNumber}
            onChange={change("sectionNumber")}
            className={FIELD}
          />
        </div>
        <div className="col-span-2">
          <label htmlFor={`${id}-name`} className={LABEL}>
            Course name
          </label>
          <input
            id={`${id}-name`}
            value={form.courseName}
            onChange={change("courseName")}
            className={FIELD}
          />
        </div>
        <div>
          <label htmlFor={`${id}-days`} className={LABEL}>
            Meeting days
          </label>
          <input
            id={`${id}-days`}
            required
            placeholder="MW or TR"
            value={form.meetingDays}
            onChange={change("meetingDays")}
            className={FIELD}
          />
        </div>
        <div>
          <label htmlFor={`${id}-location`} className={LABEL}>
            Location
          </label>
          <input
            id={`${id}-location`}
            value={form.location}
            onChange={change("location")}
            className={FIELD}
          />
        </div>
        <div>
          <label htmlFor={`${id}-start`} className={LABEL}>
            Starts
          </label>
          <input
            id={`${id}-start`}
            type="time"
            required
            value={form.startTime}
            onChange={change("startTime")}
            className={FIELD}
          />
        </div>
        <div>
          <label htmlFor={`${id}-end`} className={LABEL}>
            Ends
          </label>
          <input
            id={`${id}-end`}
            type="time"
            required
            value={form.endTime}
            onChange={change("endTime")}
            className={FIELD}
          />
        </div>
      </div>
      <button type="submit" disabled={saving} className={SUBMIT}>
        {saving ? "Adding…" : "Add section"}
      </button>
      <OutcomeText outcome={outcome} />
    </form>
  );
}

export function AddDataTab({
  accounts,
  onAccountAdded,
  onDataChanged,
}: {
  accounts: DemoAccount[];
  onAccountAdded: (account: DemoAccount) => void;
  onDataChanged: () => void;
}) {
  const [departments, setDepartments] = useState<Department[]>([]);
  const [ranks, setRanks] = useState<string[]>([]);

  useEffect(() => {
    const controller = new AbortController();
    getDepartments(controller.signal).then(setDepartments, () => undefined);
    getEvaluationPolicies().then(
      (policies) => setRanks(policies.map((p) => p.professorRank)),
      () => undefined,
    );
    return () => controller.abort();
  }, []);

  return (
    <div className="space-y-6">
      <ProfessorForm
        departments={departments}
        ranks={ranks}
        onAdded={(account) => {
          onAccountAdded(account);
          onDataChanged();
        }}
      />
      <SectionForm
        accounts={accounts}
        departments={departments}
        onAdded={onDataChanged}
      />
    </div>
  );
}
