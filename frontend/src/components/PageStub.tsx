import { PageHeader } from "./PageHeader";

export function PageStub({
  title,
  subtitle,
  frs,
}: {
  title: string;
  subtitle?: string;
  frs?: string;
}) {
  return (
    <section>
      <PageHeader title={title} subtitle={subtitle} />
      <div className="mt-8 rounded-lg border border-line border-dashed bg-white p-8 text-muted">
        Not built yet
      </div>
    </section>
  );
}
