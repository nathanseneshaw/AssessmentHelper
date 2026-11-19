import type { ImportError } from "../../api/imports";

export function CycleImportProblems({ problems }: { problems: ImportError[] }) {
  if (!problems.length) return null;
  return (
    <div className="max-h-64 overflow-auto rounded border border-line">
      <table className="w-full text-left text-sm">
        <thead className="bg-cream">
          <tr>
            <th className="p-2">Row</th>
            <th className="p-2">Column</th>
            <th className="p-2">Problem</th>
          </tr>
        </thead>
        <tbody>
          {problems.map((problem) => (
            <tr
              key={`${problem.row}-${problem.column}-${problem.message}`}
              className="border-line border-t"
            >
              <td className="p-2">{problem.row ?? "File"}</td>
              <td className="p-2">{problem.column ?? "—"}</td>
              <td className="p-2">{problem.message}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
