import 'server-only';
import createClient from 'openapi-fetch';
import type { paths } from './schema';

// The service, called from the server only and by its cluster name. The types come from its contract, so a
// changed field stops the build (Next.js guide, steps 1, 2 and 4).
export function api() {
  return createClient<paths>({
    baseUrl: process.env.API_BASE_URL ?? 'http://localhost:8080',
  });
}
