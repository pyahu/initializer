import 'server-only';
import { betterAuth } from 'better-auth';
import { nextCookies } from 'better-auth/next-js';
import { genericOAuth } from 'better-auth/plugins/generic-oauth';

// The OIDC code flow runs on this server. Without a database, the session and the provider's tokens live in
// encrypted httpOnly cookies; the browser cannot read them (Next.js guide, step 3).
function create() {
  return betterAuth({
    baseURL: process.env.BETTER_AUTH_URL ?? 'http://localhost:3001',
    secret: process.env.BETTER_AUTH_SECRET,
    session: {
      cookieCache: { enabled: true, strategy: 'jwe', maxAge: 60 * 60, refreshCache: true },
    },
    plugins: [
      genericOAuth({
        config: [
          {
            providerId: 'oidc',
            discoveryUrl: `${process.env.OIDC_ISSUER_URI ?? 'http://localhost:8090/default'}/.well-known/openid-configuration`,
            requireIdTokenVerification: true,
            clientId: process.env.OIDC_CLIENT_ID ?? '[[name]]-web',
            clientSecret: process.env.OIDC_CLIENT_SECRET ?? 'local',
            scopes: ['openid', 'profile', 'email', 'offline_access'],
          },
        ],
      }),
      nextCookies(),
    ],
  });
}

let instance: ReturnType<typeof create> | undefined;

// Created on first use, so next build does not ask the identity provider for its configuration.
export function auth() {
  instance ??= create();
  return instance;
}
