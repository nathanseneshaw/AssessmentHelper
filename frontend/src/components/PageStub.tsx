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
      <h1 className="font-bold text-3xl">{title}</h1>
      {subtitle && <p className="mt-1 text-ink/60">{subtitle}</p>}
      <div className="mt-6 rounded-lg border border-line border-dashed bg-white p-8 text-ink/60">
        Not built yet
      </div>
    </section>
  );
}
