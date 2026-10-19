'use server';

import { api } from '@/api/client';
import type { components } from '@/api/schema';

type Preview = components['schemas']['Preview'];
type FieldError = components['schemas']['FieldError'];

export type PreviewResult = { preview: Preview } | { errors: FieldError[] } | { unavailable: true };

// Explore: the files a request gives, asked from the server so the service stays inside the cluster. The service
// validates every field; this only keeps the values strings.
export async function preview(values: Record<string, string>): Promise<PreviewResult> {
  const form = new URLSearchParams();
  for (const [field, value] of Object.entries(values)) {
    if (typeof value === 'string') {
      form.set(field, value);
    }
  }
  const { data, error } = await api()
    .POST('/preview', {
      body: {},
      bodySerializer: () => form.toString(),
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    })
    .catch(() => ({ data: undefined, error: undefined }));
  if (data) {
    return { preview: data };
  }
  if (error?.errors) {
    return { errors: error.errors };
  }
  return { unavailable: true };
}
