import { defineConfig } from "astro/config";
import starlight from "@astrojs/starlight";
import starlightLlmsTxt from "starlight-llms-txt";

const site = "https://initializer.pyahu.io";

export default defineConfig({
  site,
  integrations: [
    starlight({
      title: "Pyahu Initializer",
      description: "A knowledge base for production Java with Spring Boot and Next.js, and a generator for production-ready projects.",
      logo: { src: "./src/assets/mark.svg", alt: "Pyahu Initializer", replacesTitle: false },
      favicon: "/assets/mark.svg",
      social: [{ icon: "github", label: "GitHub", href: "https://github.com/pyahu/initializer" }],
      customCss: ["./src/styles/starlight.css"],
      // Until the pilot format is approved.
      head: [{ tag: "meta", attrs: { name: "robots", content: "noindex" } }],
      sidebar: [
        {
          label: "Guides",
          items: [
            { label: "The JVM in containers", slug: "guides/jvm-in-containers" },
            { label: "Observability", slug: "guides/observability" },
          ],
        },
        { label: "Reference", items: [{ label: "Reference docs", slug: "reference/docs" }] },
        {
          label: "Contributing",
          items: [
            { label: "Style guide", slug: "contributing/style" },
            { label: "Diagram kit", slug: "contributing/diagrams" },
          ],
        },
      ],
      plugins: [
        starlightLlmsTxt({
          details: [
            `- [The JVM in containers](${site}/guides/jvm-in-containers/): memory, CPU, GC and startup for a JVM in a pod.`,
            `- [Observability](${site}/guides/observability/): traces, metrics and logs over OTLP with Spring Boot 4.1.`,
            `- [Reference docs](${site}/reference/docs/): official documentation for the exact versions of the baseline.`,
          ].join("\n"),
          minify: { details: false },
          pageSeparator: "\n\n<!-- page -->\n\n",
          customSelectors: { all: [".dg-dialog", ".dg-hint"] },
          promote: ["guides/**", "reference/**"],
        }),
      ],
    }),
  ],
});
