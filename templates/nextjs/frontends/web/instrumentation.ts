import { registerOTel } from '@vercel/otel';

// Spans for rendering, routes and fetch, sent over OTLP when OTEL_EXPORTER_OTLP_ENDPOINT is set. The calls to the
// service carry traceparent, so a click and the service's work are one trace (Next.js guide, step 8).
export function register() {
  registerOTel({
    serviceName: '[[name]]-web',
    instrumentationConfig: {
      fetch: { propagateContextUrls: [process.env.API_BASE_URL ?? 'http://localhost:8080'] },
    },
  });
}
