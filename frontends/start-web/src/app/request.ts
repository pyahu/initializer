// The options of one service; in a monorepo each vertical picks its own (monorepo guide, ADR 0004).
const SINGLE_ONLY = ['package', 'architecture', 'database', 'frontend'];

// What the service is asked for, from the form's values: a monorepo leaves out the options of one service, one
// service leaves out the verticals.
export function requestOf(values: Record<string, string>) {
  const monorepo = values.layout === 'monorepo';
  return Object.fromEntries(
    Object.entries(values).filter(([id]) => (monorepo ? !SINGLE_ONLY.includes(id) : id !== 'verticals')),
  );
}
