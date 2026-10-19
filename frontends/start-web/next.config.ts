import type { NextConfig } from 'next';

const config: NextConfig = {
  // A small server with only the files each page needs, for the image (Next.js guide, step 6).
  output: 'standalone',
  poweredByHeader: false,
  // Headers for every path (step 7). Add Strict-Transport-Security where you serve HTTPS or set it at the edge.
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'Referrer-Policy', value: 'strict-origin-when-cross-origin' },
          { key: 'X-Frame-Options', value: 'DENY' },
        ],
      },
    ];
  },
};

export default config;
