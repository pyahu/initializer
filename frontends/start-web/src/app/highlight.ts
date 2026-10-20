import type { HighlighterCore, ThemedToken } from 'shiki/core';

// Colors for the code come from globals.css, so the files read in the palette of the site.
const LANGUAGES: [RegExp, string][] = [
  [/\.java$/, 'java'],
  [/\.kts?$/, 'kotlin'],
  [/\.ya?ml$/, 'yaml'],
  [/\.json$/, 'json'],
  [/\.sql$/, 'sql'],
  [/\.md$/, 'markdown'],
  [/(^|\/)Dockerfile$/, 'docker'],
  [/(^|\/)gradlew$|\.sh$/, 'shellscript'],
  [/\.tsx$/, 'tsx'],
  [/\.ts$/, 'typescript'],
  [/\.m?js$/, 'javascript'],
  [/\.toml$/, 'toml'],
  [/\.properties$/, 'properties'],
  [/\.xml$/, 'xml'],
  [/\.css$/, 'css'],
];

export function language(path: string) {
  return LANGUAGES.find(([pattern]) => pattern.test(path))?.[1];
}

let highlighter: Promise<HighlighterCore> | undefined;

// Loaded on the first file shown, so the form does not wait for the grammars.
function load() {
  highlighter ??= Promise.all([
    import('shiki/core'),
    import('shiki/engine/javascript'),
  ]).then(([{ createHighlighterCore, createCssVariablesTheme }, { createJavaScriptRegexEngine }]) =>
    createHighlighterCore({
      themes: [createCssVariablesTheme({ name: 'site', variablePrefix: '--code-', fontStyle: true })],
      langs: [
        import('shiki/langs/java.mjs'),
        import('shiki/langs/kotlin.mjs'),
        import('shiki/langs/yaml.mjs'),
        import('shiki/langs/json.mjs'),
        import('shiki/langs/sql.mjs'),
        import('shiki/langs/markdown.mjs'),
        import('shiki/langs/docker.mjs'),
        import('shiki/langs/shellscript.mjs'),
        import('shiki/langs/tsx.mjs'),
        import('shiki/langs/typescript.mjs'),
        import('shiki/langs/javascript.mjs'),
        import('shiki/langs/toml.mjs'),
        import('shiki/langs/properties.mjs'),
        import('shiki/langs/xml.mjs'),
        import('shiki/langs/css.mjs'),
      ],
      engine: createJavaScriptRegexEngine({ forgiving: true }),
    }),
  );
  return highlighter;
}

export async function tokens(path: string, code: string): Promise<ThemedToken[][] | undefined> {
  const lang = language(path);
  if (!lang) {
    return undefined;
  }
  const shiki = await load();
  return shiki.codeToTokensBase(code, { lang, theme: 'site' });
}
