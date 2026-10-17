import { defineCollection, z } from "astro:content";
import { docsLoader, i18nLoader } from "@astrojs/starlight/loaders";
import { docsSchema, i18nSchema } from "@astrojs/starlight/schema";

// The guide block is validated by guidekit (Go); the site only keeps it.
export const collections = {
  docs: defineCollection({ loader: docsLoader(), schema: docsSchema({ extend: z.object({ guide: z.unknown().optional() }) }) }),
  i18n: defineCollection({ loader: i18nLoader(), schema: i18nSchema() }),
};
