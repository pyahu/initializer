[[#oidc]]
import { headers } from 'next/headers';
[[/oidc]]
[[^oidc]]
import { connection } from 'next/server';
[[/oidc]]
import { api } from '@/api/client';
[[#oidc]]
import { auth } from '@/auth';
import { signIn } from './actions';
[[/oidc]]

// Rendered on the server for every request: the service's address[[#oidc]] and the token[[/oidc]] never reach the browser
// (Next.js guide, steps 1 and 4).
export default async function Home() {
[[#oidc]]
  const requestHeaders = await headers();
  // The session is checked here, where the data is read (step 3).
  const session = await auth().api.getSession({ headers: requestHeaders });
  if (!session) {
    return (
      <main>
        <h1>[[name]]</h1>
        <form action={signIn}>
          <button type="submit">Sign in</button>
        </form>
      </main>
    );
  }
  const { accessToken } = await auth().api.getAccessToken({
    body: { useAccountCookie: true },
    headers: requestHeaders,
  });
  const { data } = await api(accessToken)
    .GET('/ping')
    .catch(() => ({ data: undefined }));
[[/oidc]]
[[^oidc]]
  await connection();
  const { data } = await api()
    .GET('/ping')
    .catch(() => ({ data: undefined }));
[[/oidc]]
  return (
    <main>
      <h1>[[name]]</h1>
[[#oidc]]
      <p>Signed in as {session.user.email}</p>
[[/oidc]]
      <p>The service says: {data ? data.message : 'nothing, it is unavailable'}</p>
[[#postgres]]
      {data && <p>Pings counted in the database: {data.count}</p>}
[[/postgres]]
    </main>
  );
}
