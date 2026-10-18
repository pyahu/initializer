import { defineConfig } from "astro/config";
import starlight from "@astrojs/starlight";
import starlightLlmsTxt from "starlight-llms-txt";
import { readFileSync } from "node:fs";

const site = "https://initializer.pyahu.io";

// Every guide for llms.txt, in the order of the guides index page.
const guideLinks = readFileSync(new URL("./src/content/docs/guides.mdx", import.meta.url), "utf8")
  .split("\n")
  .filter((l) => /^- \[[^\]]+\]\(\/guides\/[a-z0-9-]+\/\)/.test(l))
  .map((l) => l.replace("](/", `](${site}/`));

export default defineConfig({
  site,
  integrations: [
    starlight({
      title: "Pyahu Initializer",
      description: "Production practices for Java with Spring Boot and Next.js and a generator for production-ready projects.",
      logo: { src: "./src/assets/mark.svg", alt: "Pyahu Initializer", replacesTitle: false },
      favicon: "/assets/mark.svg",
      social: [{ icon: "github", label: "GitHub", href: "https://github.com/pyahu/initializer" }],
      customCss: ["./src/styles/starlight.css"],
      // Until the pilot format is approved.
      head: [{ tag: "meta", attrs: { name: "robots", content: "noindex" } }],
      sidebar: [
        { label: "Start here", items: [{ label: "All guides", slug: "guides" }] },
        {
          label: "1. Foundations",
          items: [
            { label: "Build and code quality", slug: "guides/build-quality" },
            { label: "Architecture", slug: "guides/architecture" },
            { label: "Architecture styles compared", slug: "guides/architecture-styles" },
            { label: "Testing a Spring Boot service", slug: "guides/testing" },
          ],
        },
        {
          label: "2. Build the service",
          items: [
            { label: "REST APIs, contract first", slug: "guides/rest-apis" },
            { label: "gRPC services", slug: "guides/grpc" },
            { label: "The database in production", slug: "guides/database" },
            { label: "JPA and jOOQ", slug: "guides/jpa-and-jooq" },
            { label: "HTTP clients and resilience", slug: "guides/http-clients" },
            { label: "Virtual threads and concurrency", slug: "guides/virtual-threads" },
          ],
        },
        {
          label: "3. Events and data",
          items: [
            { label: "Kafka with the outbox", slug: "guides/kafka" },
            { label: "RabbitMQ", slug: "guides/rabbitmq" },
            { label: "Caching with Valkey", slug: "guides/caching" },
          ],
        },
        {
          label: "4. Run in production",
          items: [
            { label: "The JVM in containers", slug: "guides/jvm-in-containers" },
            { label: "Configuration and secrets", slug: "guides/configuration" },
            { label: "Observability", slug: "guides/observability" },
            { label: "Security hardening", slug: "guides/security" },
          ],
        },
        {
          label: "5. Ship",
          items: [
            { label: "The container image and supply chain", slug: "guides/container-image" },
            { label: "CI pipelines", slug: "guides/ci-pipelines" },
            { label: "Deploying to Kubernetes", slug: "guides/kubernetes-deployment" },
            { label: "Kubernetes manifests", slug: "guides/kubernetes-manifests" },
          ],
        },
        {
          label: "6. Grow",
          items: [
            { label: "Local development", slug: "guides/local-development" },
            { label: "Monorepos with several services", slug: "guides/monorepo" },
            { label: "Next.js next to the service", slug: "guides/nextjs" },
          ],
        },
        {
          label: "About",
          items: [
            { label: "Why it exists", slug: "why" },
            { label: "How we keep it current", slug: "maintenance" },
            { label: "Reference docs", slug: "reference/docs" },
          ],
        },
      ],
      plugins: [
        starlightLlmsTxt({
          details: [
            `- [The guides](${site}/guides/): Pyahu's production practices for Spring Boot, Java and Next.js in six parts, one step per question.`,
            `- [Agent Skill](https://github.com/pyahu/initializer/tree/main/skills): the same guides as plain Markdown, one file per guide, with the full checklist.`,
            `- [Reference docs](${site}/reference/docs/): official documentation for the exact versions of the baseline.`,
            `- [Why it exists](${site}/why/): the problem it solves, why guides come first and why versions are pinned.`,
            `- [How we keep it current](${site}/maintenance/): how versions, advice and links stay up to date.`,
            "",
            "Guides:",
            "",
            ...guideLinks,
          ].join("\n"),
          minify: { details: false },
          pageSeparator: "\n\n<!-- page -->\n\n",
          customSelectors: { all: [".dg-dialog", ".dg-hint"] },
          promote: ["guides", "guides/**", "reference/**"],
        }),
      ],
    }),
  ],
});
