'use client';

import { type FormEvent, useEffect, useRef, useState } from 'react';
import type { components } from '@/api/schema';
import { preview, type PreviewResult } from './actions';
import { type Change, Explorer } from './explorer';

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

// Name and group shape every path; package and description are rarely changed, so they wait under More.
const MAIN = ['name', 'group'];
const FIRST_FILE = 'README.md';
const SETTLE_MS = 350;
const UNAVAILABLE = 'The generator is unavailable. Try again in a moment.';

function label(field: Field) {
  return LABELS[field.id] ?? field.id;
}

// The form posts to /starter.zip, so it downloads the zip even before this script runs. With the script, the files
// follow every change and a rejected field shows next to its input instead of as JSON.
export function Starter({
  fields,
  baseline,
  initial,
  first,
}: {
  fields: Field[];
  baseline: BaselineInfo;
  initial: Record<string, string>;
  first: PreviewResult;
}) {
  const [values, setValues] = useState(initial);
  const [errors, setErrors] = useState<FieldError[]>('errors' in first ? first.errors : []);
  const [notice, setNotice] = useState<string | undefined>('unavailable' in first ? UNAVAILABLE : undefined);
  const [files, setFiles] = useState<Preview | undefined>('preview' in first ? first.preview : undefined);
  const [changes, setChanges] = useState<ReadonlyMap<string, Change>>(new Map());
  const [selected, setSelected] = useState<string | undefined>(() => firstFile(files));
  const [updating, setUpdating] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [pane, setPane] = useState<'options' | 'files'>('options');
  const [shared, setShared] = useState(false);
  const asked = useRef(0);
  const touched = useRef(false);
  const shown = useRef(files);
  const [mod, setMod] = useState('Ctrl');

  const text = fields.filter((f) => !f.choices?.length);
  const main = text.filter((f) => MAIN.includes(f.id));
  const more = text.filter((f) => !MAIN.includes(f.id));
  const choices = fields.filter((f) => f.choices?.length);
  const derivedPackage = `${values.group || 'com.example'}.${(values.name || 'demo').replaceAll('-', '')}`;
  const differs = fields.filter((f) => values[f.id] && values[f.id] !== (f.defaultValue ?? ''));

  // Every change asks for the files again once typing settles; an older answer that arrives late is dropped.
  useEffect(() => {
    if (!touched.current) {
      return;
    }
    const query = new URLSearchParams(differs.map((f) => [f.id, values[f.id]]));
    window.history.replaceState(null, '', query.size ? `?${query}` : window.location.pathname);
    const ask = ++asked.current;
    setUpdating(true);
    const timer = setTimeout(async () => {
      const result = await preview(values).catch((): PreviewResult => ({ unavailable: true }));
      if (ask !== asked.current) {
        return;
      }
      setUpdating(false);
      if ('preview' in result) {
        setErrors([]);
        setNotice(undefined);
        setChanges(compare(shown.current, result.preview));
        shown.current = result.preview;
        setFiles(result.preview);
        setSelected((path) => (result.preview.files.some((f) => f.path === path) ? path : firstFile(result.preview)));
      } else if ('errors' in result) {
        setErrors(result.errors);
      } else {
        setNotice(UNAVAILABLE);
      }
    }, SETTLE_MS);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [values]);

  function change(id: string, value: string) {
    touched.current = true;
    setValues((v) => ({ ...v, [id]: value }));
  }

  function rejected(id: string) {
    return errors.filter((e) => e.field === id).map((e) => e.message);
  }

  async function generate(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    setGenerating(true);
    setNotice(undefined);
    try {
      const response = await fetch('/starter.zip', { method: 'POST', body: new URLSearchParams(values) });
      if (response.ok) {
        const disposition = response.headers.get('content-disposition') ?? '';
        const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? 'project.zip';
        save(await response.blob(), name);
        return;
      }
      const problem = await response.json().catch(() => undefined);
      if (problem?.errors?.length) {
        setErrors(problem.errors);
        setPane('options');
      } else {
        setNotice(problem?.detail ?? 'The project could not be generated. Try again.');
      }
    } catch {
      setNotice(UNAVAILABLE);
    } finally {
      setGenerating(false);
    }
  }

  // Ctrl or Cmd with Enter generates from anywhere on the page.
  useEffect(() => {
    if (/Mac|iP/.test(navigator.platform)) {
      setMod('⌘');
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        document.querySelector<HTMLFormElement>('#starter')?.requestSubmit();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  async function share() {
    await navigator.clipboard.writeText(window.location.href);
    setShared(true);
    setTimeout(() => setShared(false), 1500);
  }

  // The same request from a terminal, with only the values that differ from the defaults.
  const command = [
    'curl https://start.pyahu.io/starter.zip',
    ...differs.map((f) => `-d ${f.id}=${quote(values[f.id])}`),
    `-o ${values.name || 'demo'}.zip`,
  ].join(' ');

  const input = (field: Field) => (
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
  );

  return (
    <div className="workspace" data-pane={pane}>
      <div className="tabs" role="tablist" aria-label="View">
        <button type="button" role="tab" aria-selected={pane === 'options'} onClick={() => setPane('options')}>
          Options
        </button>
        <button type="button" role="tab" aria-selected={pane === 'files'} onClick={() => setPane('files')}>
          Files{files && <span className="count">{files.files.length}</span>}
        </button>
      </div>

      <form id="starter" className="options" action="/starter.zip" method="post" onSubmit={generate} noValidate>
        <div className="intro">
          <h1>Generate a service</h1>
          <p className="lead">
            Spring Boot with build, contract, telemetry, image, manifests and CI, as the{' '}
            <a href="https://initializer.pyahu.io/guides/">guides</a> do it.
          </p>
        </div>

        <fieldset className="project">
          <legend>Project</legend>
          <div className="pair">{main.map(input)}</div>
          <details open={more.some((f) => rejected(f.id).length > 0 || values[f.id] !== (f.defaultValue ?? '')) || undefined}>
            <summary>
              <span>More</span>
              <span className="derived">
                package <code>{values.package || derivedPackage}</code>
              </span>
            </summary>
            <div className="more">{more.map(input)}</div>
          </details>
        </fieldset>

        <fieldset className="choices">
          <legend>Options</legend>
          {choices.map((field) => (
            <div key={field.id} className="choice-row">
              <span className="choice-label" id={`${field.id}-label`}>
                {label(field)}
              </span>
              <div className="segments" role="radiogroup" aria-labelledby={`${field.id}-label`}>
                {field.choices?.map((choice) => (
                  <label key={choice.id} className="segment">
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
            </div>
          ))}
        </fieldset>

        <details className="terminal">
          <summary>From a terminal</summary>
          <div className="command">
            <pre>
              <code>{command}</code>
            </pre>
            <button type="button" className="icon-button" onClick={() => navigator.clipboard.writeText(command)}>
              Copy
            </button>
          </div>
        </details>

        <p className="baseline">
          Baseline <a href={baseline.url}>{baseline.id}</a>: Java {baseline.java}, Spring Boot {baseline.springBoot}.
        </p>
      </form>

      <div className="dock">
        {notice && (
          <p className="notice" role="alert">
            {notice}
          </p>
        )}
        <div className="dock-buttons">
          <button type="submit" form="starter" className="button primary" disabled={generating}>
            {generating ? 'Generating…' : 'Generate'}
            <kbd aria-hidden="true">{mod} ↵</kbd>
          </button>
          <button type="button" className="button quiet" onClick={share}>
            {shared ? 'Link copied' : 'Share'}
          </button>
        </div>
      </div>

      <section className="explore" aria-label="Explore">
        {files ? (
          <Explorer
            preview={files}
            changes={changes}
            selected={selected}
            onSelect={setSelected}
            updating={updating}
          />
        ) : (
          <div className="empty">
            <h2>No files to show</h2>
            <p>{notice ?? 'Fix the options marked in red and the files show here.'}</p>
          </div>
        )}
      </section>
    </div>
  );
}

function firstFile(preview?: Preview) {
  const paths = preview?.files.map((f) => f.path) ?? [];
  return paths.includes(FIRST_FILE) ? FIRST_FILE : paths[0];
}

// What the last change did: files it added and files whose contents moved. A renamed project changes every path,
// which says nothing, so a different root compares as nothing changed.
function compare(before: Preview | undefined, after: Preview): Map<string, Change> {
  const changes = new Map<string, Change>();
  if (!before || before.root !== after.root) {
    return changes;
  }
  const old = new Map(before.files.map((f) => [f.path, f]));
  for (const file of after.files) {
    const was = old.get(file.path);
    if (!was) {
      changes.set(file.path, 'new');
    } else if (was.content !== file.content || was.size !== file.size) {
      changes.set(file.path, 'changed');
    }
  }
  return changes;
}

function save(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  URL.revokeObjectURL(url);
}

function quote(value: string) {
  return /^[\w.,:/@+-]+$/.test(value) ? value : `'${value.replaceAll("'", "'\\''")}'`;
}
