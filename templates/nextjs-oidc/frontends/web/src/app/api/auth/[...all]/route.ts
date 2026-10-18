import { toNextJsHandler } from 'better-auth/next-js';
import { auth } from '@/auth';

// The login callback and the session endpoints.
export const { GET, POST } = toNextJsHandler((request) => auth().handler(request));
