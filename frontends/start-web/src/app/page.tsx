import { connection } from 'next/server';
import { api } from '@/api/client';
import { Starter } from './starter';

type Query = Record<string, string | string[] | undefined>;

// Rendered on the server for every request: the form is whatever the service says it supports, so a new option
// shows up here without a change to this page.
export default async function Home({ searchParams }: { searchParams: Promise<Query> }) {
  await connection();
  const { data } = await api()
    .GET('/metadata')
    .catch(() => ({ data: undefined }));
  if (!data) {
    return (
      <main className="page">
        <h1>The generator is unavailable</h1>
        <p>Try again in a moment.</p>
      </main>
    );
  }
  // A link such as /?name=orders&database=postgres opens the form with those values.
  const query = await searchParams;
  const initial: Record<string, string> = {};
  for (const field of data.fields) {
    const given = query[field.id];
    initial[field.id] = typeof given === 'string' ? given : (field.defaultValue ?? '');
  }
  return (
    <main className="page">
      <Starter fields={data.fields} baseline={data.baseline} initial={initial} />
    </main>
  );
}
