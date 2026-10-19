'use client';

import { type FormEvent, useRef, useState, useTransition } from 'react';
import type { components } from '@/api/schema';
import { preview } from './actions';

type Field = components['schemas']['Field'];
type BaselineInfo = components['schemas']['BaselineInfo'];
type Preview = components['schemas']['Preview'];
type FieldError = components['schemas']['FieldError'];

const LABELS: Record<string, string> = {
  name: 'Name',
  group: 'Group',
  package: 'Package',
  description: 'Description',
  architecture: 'Architecture',
  database: 'Database',
  auth: 'Login',
  frontend: 'Frontend',
};

const FIRST_FILE = 'README.md';

function label(field: Field) {
  return LABELS[field.id] ?? field.id;
}

// The form posts to /starter.zip, so it downloads the zip even before this script runs. With the script, a rejected
// field shows next to its input instead of as JSON.
export function Starter({
  fields,
  baseline,
  initial,
}: {
  fields: Field[];
  baseline: BaselineInfo;
  initial: Record<string, string>;
}) {
  const [values, setValues] = useState(initial);
  const [errors, setErrors] = useState<FieldError[]>([]);
  const [notice, setNotice] = useState<string>();
  const [files, setFiles] = useState<Preview>();
  const [selected, setSelected] = useState<string>();
  const [generating, setGenerating] = useState(false);
  const [exploring, startExploring] = useTransition();
  const panel = useRef<HTMLElement>(null);

  const text = fields.filter((f) => !f.choices?.length);
  const choices = fields.filter((f) => f.choices?.length);
  const derivedPackage = `${values.group || 'com.example'}.${(values.name || 'demo').replaceAll('-', '')}`;

  function change(id: string, value: string) {
    setValues((v) => ({ ...v, [id]: value }));
    setErrors((e) => e.filter((x) => x.field !== id));
    setFiles(undefined);
  }

  function rejected(id: string) {
    return errors.filter((e) => e.field === id).map((e) => e.message);
  }

  async function generate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setGenerating(true);
    setNotice(undefined);
    try {
      const response = await fetch('/starter.zip', { method: 'POST', body: new URLSearchParams(values) });
      if (response.ok) {
        const disposition = response.headers.get('content-disposition') ?? '';
        const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? 'project.zip';
        const url = URL.createObjectURL(await response.blob());
        const link = document.createElement('a');
        link.href = url;
        link.download = name;
        link.click();
        URL.revokeObjectURL(url);
        return;
      }
      const problem = await response.json().catch(() => undefined);
      if (problem?.errors?.length) {
        setErrors(problem.errors);
      } else {
        setNotice(problem?.detail ?? 'The project could not be generated. Try again.');
      }
    } catch {
      setNotice('The generator is unavailable. Try again in a moment.');
    } finally {
      setGenerating(false);
    }
  }

  function explore() {
    setNotice(undefined);
    startExploring(async () => {
      const result = await preview(values);
      if ('preview' in result) {
        setFiles(result.preview);
        const paths = result.preview.files.map((f) => f.path);
        setSelected(paths.includes(FIRST_FILE) ? FIRST_FILE : paths[0]);
        // On a phone the files are below the form.
        if (window.matchMedia('(max-width: 900px)').matches) {
          panel.current?.scrollIntoView({ block: 'start' });
        }
      } else if ('errors' in result) {
        setErrors(result.errors);
      } else {
        setNotice('The generator is unavailable. Try again in a moment.');
      }
    });
  }

  // The same request from a terminal, with only the values that differ from the defaults.
  const command = [
    'curl https://start.pyahu.io/starter.zip',
    ...fields
      .filter((f) => values[f.id] && values[f.id] !== (f.defaultValue ?? ''))
      .map((f) => `-d ${f.id}=${quote(values[f.id])}`),
    `-o ${values.name || 'demo'}.zip`,
  ].join(' ');

  return (
    <div className="starter">
      <form className="options" action="/starter.zip" method="post" onSubmit={generate} noValidate>
        <h1>Generate a service</h1>
        <p className="lead">
          A Spring Boot service that already follows the <a href="https://initializer.pyahu.io/guides/">guides</a>:
          build, contract, telemetry, image, manifests and CI.
        </p>

        <fieldset>
          <legend>Project</legend>
          {text.map((field) => (
            <div key={field.id} className="text">
              <label htmlFor={field.id}>{label(field)}</label>
              <input
                id={field.id}
                name={field.id}
                value={values[field.id] ?? ''}
                placeholder={field.id === 'package' ? derivedPackage : undefined}
                maxLength={field.maxLength}
                autoComplete="off"
                spellCheck={false}
                aria-invalid={rejected(field.id).length > 0}
                aria-describedby={`${field.id}-hint`}
                onChange={(e) => change(field.id, e.target.value)}
              />
              <small id={`${field.id}-hint`}>{rejected(field.id).join('; ') || field.description}</small>
            </div>
          ))}
        </fieldset>

        {choices.map((field) => (
          <fieldset key={field.id} className="choices">
            <legend>{label(field)}</legend>
            <div role="radiogroup" aria-label={label(field)}>
              {field.choices?.map((choice) => (
                <label key={choice.id} className="choice">
                  <input
                    type="radio"
                    name={field.id}
                    value={choice.id}
                    checked={values[field.id] === choice.id}
                    onChange={() => change(field.id, choice.id)}
                  />
                  <span>{choice.title}</span>
                </label>
              ))}
            </div>
            <small>{rejected(field.id).join('; ') || field.description}</small>
          </fieldset>
        ))}

        <div className="actions">
          <button type="submit" className="button primary" disabled={generating}>
            {generating ? 'Generating…' : 'Generate'}
          </button>
          <button type="button" className="button quiet" onClick={explore} disabled={exploring}>
            {exploring ? 'Exploring…' : 'Explore'}
          </button>
        </div>
        {notice && (
          <p className="notice" role="alert">
            {notice}
          </p>
        )}
        <p className="baseline">
          Baseline <a href={baseline.url}>{baseline.id}</a>: Java {baseline.java}, Spring Boot {baseline.springBoot}.
        </p>
      </form>

      <section className="explore" aria-label="Explore" ref={panel}>
        {files ? (
          <Explorer preview={files} selected={selected} onSelect={setSelected} />
        ) : (
          <div className="empty">
            <h2>Look before you download</h2>
            <p>Explore shows every file of the project with the options on the left.</p>
            <p>From a terminal:</p>
            <pre>
              <code>{command}</code>
            </pre>
          </div>
        )}
      </section>
    </div>
  );
}

function Explorer({
  preview,
  selected,
  onSelect,
}: {
  preview: Preview;
  selected?: string;
  onSelect: (path: string) => void;
}) {
  const file = preview.files.find((f) => f.path === selected);
  const rows: { key: string; depth: number; name: string; path?: string }[] = [];
  const seen = new Set<string>();
  for (const f of preview.files) {
    const parts = f.path.split('/');
    parts.slice(0, -1).forEach((part, depth) => {
      const folder = parts.slice(0, depth + 1).join('/');
      if (!seen.has(folder)) {
        seen.add(folder);
        rows.push({ key: `${folder}/`, depth, name: `${part}/` });
      }
    });
    rows.push({ key: f.path, depth: parts.length - 1, name: parts[parts.length - 1], path: f.path });
  }
  return (
    <div className="explorer">
      <nav className="tree" aria-label="Files">
        <p className="root">{preview.root}/</p>
        <ul>
          {rows.map((row) => (
            <li key={row.key} style={{ paddingLeft: `${row.depth * 0.9}rem` }}>
              {row.path ? (
                <button
                  type="button"
                  aria-current={row.path === selected ? 'true' : undefined}
                  onClick={() => onSelect(row.path!)}
                >
                  {row.name}
                </button>
              ) : (
                <span className="folder">{row.name}</span>
              )}
            </li>
          ))}
        </ul>
      </nav>
      <div className="file">
        {file && (
          <>
            <p className="path">{file.path}</p>
            {file.content === undefined ? (
              <p className="binary">A binary file of {file.size} bytes.</p>
            ) : (
              <pre aria-label={`Contents of ${file.path}`}>
                <code>{file.content}</code>
              </pre>
            )}
          </>
        )}
      </div>
    </div>
  );
}

function quote(value: string) {
  return /^[\w.,:/@+-]+$/.test(value) ? value : `'${value.replaceAll("'", "'\\''")}'`;
}
