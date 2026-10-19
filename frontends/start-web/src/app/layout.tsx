import '@fontsource-variable/space-grotesk';
import '@fontsource/ibm-plex-mono/400.css';
import './globals.css';
import type { ReactNode } from 'react';

export const metadata = {
  title: 'Pyahu Initializer',
  description: 'Generate a Spring Boot service that already follows the Pyahu Initializer guides.',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <header className="site-header">
          <a className="brand" href="/" aria-label="Pyahu Initializer start">
            <img src="/mark.svg" alt="" width="30" height="30" />
            <span>
              pyahu<span className="brand-divider">/</span>
              <span className="brand-product">start</span>
            </span>
          </a>
          <span className="badge" title="Until every combination runs on a real cluster">
            Preview
          </span>
          <nav aria-label="Primary navigation">
            <a href="https://initializer.pyahu.io/guides/">Guides</a>
            <a href="https://github.com/pyahu/initializer">GitHub</a>
          </nav>
        </header>
        {children}
      </body>
    </html>
  );
}
