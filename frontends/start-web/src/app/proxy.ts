import 'server-only';

// The service is not public: the browser and a terminal reach /starter.zip and /metadata here. This server
// passes them on by the service's cluster name.
const base = process.env.API_BASE_URL ?? 'http://localhost:8080';

// The service takes at most 4 KB of form fields; the same limit here keeps a large body out of memory.
const MAX_BODY = 4096;

const PASSED = ['content-type', 'content-disposition', 'retry-after'];

export async function forward(request: Request, path: string): Promise<Response> {
  const url = new URL(request.url);
  const init: RequestInit = { method: request.method };
  if (request.method === 'POST') {
    const length = Number(request.headers.get('content-length') ?? NaN);
    if (!Number.isFinite(length) || length > MAX_BODY) {
      return problem(413, 'Send at most 4 KB of form fields, with a Content-Length.');
    }
    init.body = await request.text();
    init.headers = { 'Content-Type': request.headers.get('content-type') ?? 'application/x-www-form-urlencoded' };
  }
  let upstream: Response;
  try {
    upstream = await fetch(`${base}${path}${url.search}`, init);
  } catch {
    return problem(503, 'The generator is unavailable. Try again in a moment.');
  }
  const headers = new Headers();
  for (const name of PASSED) {
    const value = upstream.headers.get(name);
    if (value) {
      headers.set(name, value);
    }
  }
  return new Response(upstream.body, { status: upstream.status, headers });
}

function problem(status: number, detail: string): Response {
  return Response.json({ status, detail }, { status, headers: { 'Content-Type': 'application/problem+json' } });
}
