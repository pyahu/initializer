'use server';

import { redirect } from 'next/navigation';
import { auth } from '@/auth';

// Server Actions are public endpoints (Next.js guide, step 3). This one only starts the login.
export async function signIn() {
  const { url } = await auth().api.signInSocial({ body: { provider: 'oidc', callbackURL: '/' } });
  if (!url) {
    throw new Error('the identity provider gave no login address');
  }
  redirect(url);
}
