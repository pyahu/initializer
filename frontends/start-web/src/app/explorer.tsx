'use client';

import { type KeyboardEvent, useEffect, useMemo, useRef, useState } from 'react';
import type { ThemedToken } from 'shiki/core';
import type { components } from '@/api/schema';
import { tokens } from './highlight';
import { ancestors, folders, type Folder, type Row, tree, visible } from './tree';

type Preview = components['schemas']['Preview'];
type PreviewFile = components['schemas']['PreviewFile'];

export type Change = 'new' | 'changed';

// Larger files are shown without colors: highlighting them would hold the page for no reading benefit.
const HIGHLIGHT_LIMIT = 100_000;

export function Explorer({
  preview,
  changes,
  selected,
  onSelect,
  updating,
}: {
  preview: Preview;
  changes: ReadonlyMap<string, Change>;
  selected?: string;
  onSelect: (path: string) => void;
  updating: boolean;
}) {
  const root = useMemo(() => tree(preview.files.map((f) => f.path)), [preview]);
  const [open, setOpen] = useState<Set<string>>(() => initiallyOpen(root, selected));
  const [picking, setPicking] = useState(false);
  const file = preview.files.find((f) => f.path === selected);

  // A new option can add folders; open the ones that hold what changed so it can be seen.
  useEffect(() => {
    setOpen((current) => {
      const next = new Set(current);
      for (const path of changes.keys()) {
        ancestors(root, path).forEach((f) => next.add(f));
      }
      if (selected) {
        ancestors(root, selected).forEach((f) => next.add(f));
      }
      return next;
    });
  }, [root, changes, selected]);

  const added = [...changes.values()].filter((c) => c === 'new').length;
  const changed = changes.size - added;

  return (
    <div className="explorer" data-picking={picking}>
      <div className="explorer-bar">
        <span className="explorer-root">
          <FolderIcon open />
          {preview.root}
          <span className="count">{preview.files.length} files</span>
        </span>
        <span className="status" role="status">
          {updating ? 'Updating…' : changes.size > 0 ? summary(added, changed) : ''}
        </span>
        <span className="bar-actions">
          <button type="button" className="icon-button" onClick={() => setOpen(new Set(folders(root)))}>
            Expand all
          </button>
          <button type="button" className="icon-button" onClick={() => setOpen(new Set())}>
            Collapse all
          </button>
        </span>
      </div>
      <Tree
        root={root}
        open={open}
        setOpen={setOpen}
        selected={selected}
        changes={changes}
        onSelect={(path) => {
          onSelect(path);
          setPicking(false);
        }}
      />
      <div className="file">
        <div className="file-bar">
          <button
            type="button"
            className="file-path"
            aria-expanded={picking}
            aria-label={file ? `${file.path}, choose another file` : 'Choose a file'}
            onClick={() => setPicking((p) => !p)}
          >
            {file && <FileIcon path={file.path} />}
            <span>{file ? breadcrumb(file.path) : 'Choose a file'}</span>
          </button>
          {file && <FileActions file={file} />}
        </div>
        {file && <Contents key={file.path} file={file} />}
      </div>
    </div>
  );
}

function summary(added: number, changed: number) {
  const parts = [added && `${added} new`, changed && `${changed} changed`].filter(Boolean);
  return `${parts.join(' · ')} with the last option`;
}

function initiallyOpen(root: Folder, selected?: string) {
  const open = new Set(root.children.filter((c) => c.kind === 'folder').map((c) => c.path));
  if (selected) {
    ancestors(root, selected).forEach((f) => open.add(f));
  }
  return open;
}

// The WAI-ARIA tree: one tab stop, arrows move, right opens, left closes or goes to the folder.
function Tree({
  root,
  open,
  setOpen,
  selected,
  changes,
  onSelect,
}: {
  root: Folder;
  open: Set<string>;
  setOpen: (update: (open: Set<string>) => Set<string>) => void;
  selected?: string;
  changes: ReadonlyMap<string, Change>;
  onSelect: (path: string) => void;
}) {
  const rows = visible(root, open);
  const [focus, setFocus] = useState<string | undefined>(selected);
  const list = useRef<HTMLUListElement>(null);
  const current = rows.some((r) => r.node.path === focus) ? focus : (selected ?? rows[0]?.node.path);

  // A file chosen another way, such as by a change of option, is scrolled into the tree's view.
  useEffect(() => {
    if (selected) {
      list.current?.querySelector(`[data-path="${CSS.escape(selected)}"]`)?.scrollIntoView({ block: 'nearest' });
    }
  }, [selected]);

  const toggle = (path: string, to?: boolean) =>
    setOpen((o) => {
      const next = new Set(o);
      if (to ?? !next.has(path)) {
        next.add(path);
      } else {
        next.delete(path);
      }
      return next;
    });

  const moveTo = (path?: string) => {
    if (!path) {
      return;
    }
    setFocus(path);
    list.current?.querySelector<HTMLElement>(`[data-path="${CSS.escape(path)}"]`)?.focus();
  };

  function onKeyDown(event: KeyboardEvent, row: Row, index: number) {
    const { node } = row;
    const folder = node.kind === 'folder';
    const expanded = folder && open.has(node.path);
    const keys: Record<string, () => void> = {
      ArrowDown: () => moveTo(rows[index + 1]?.node.path),
      ArrowUp: () => moveTo(rows[index - 1]?.node.path),
      Home: () => moveTo(rows[0]?.node.path),
      End: () => moveTo(rows[rows.length - 1]?.node.path),
      ArrowRight: () => (folder && !expanded ? toggle(node.path, true) : folder && moveTo(rows[index + 1]?.node.path)),
      ArrowLeft: () => (expanded ? toggle(node.path, false) : moveTo(row.parent)),
      Enter: () => (folder ? toggle(node.path) : onSelect(node.path)),
      ' ': () => (folder ? toggle(node.path) : onSelect(node.path)),
    };
    const action = keys[event.key];
    if (action) {
      event.preventDefault();
      action();
    }
  }

  return (
    <nav className="tree" aria-label="Files">
      <ul role="tree" aria-label="Files" ref={list}>
        {rows.map((row, index) => {
          const { node } = row;
          const folder = node.kind === 'folder';
          const expanded = folder && open.has(node.path);
          const change = folder ? folderChange(node.path, changes) : changes.get(node.path);
          return (
            <li
              key={node.path}
              role="treeitem"
              data-path={node.path}
              aria-level={row.depth + 1}
              aria-expanded={folder ? expanded : undefined}
              aria-selected={folder ? undefined : node.path === selected}
              tabIndex={node.path === current ? 0 : -1}
              style={{ paddingLeft: `${0.5 + row.depth}rem` }}
              onClick={() => {
                setFocus(node.path);
                if (folder) {
                  toggle(node.path);
                } else {
                  onSelect(node.path);
                }
              }}
              onKeyDown={(e) => onKeyDown(e, row, index)}
            >
              {folder ? (
                <>
                  <Chevron open={expanded} />
                  <FolderIcon open={expanded} />
                </>
              ) : (
                <>
                  <span className="chevron-space" />
                  <FileIcon path={node.path} />
                </>
              )}
              <span className="name">{node.name}</span>
              {change && <span className={`mark ${change}`} title={change === 'new' ? 'New' : 'Changed'} />}
            </li>
          );
        })}
      </ul>
    </nav>
  );
}

function folderChange(path: string, changes: ReadonlyMap<string, Change>): Change | undefined {
  let found: Change | undefined;
  for (const [file, change] of changes) {
    if (file.startsWith(`${path}/`)) {
      found = change === 'changed' || found === 'changed' ? 'changed' : 'new';
    }
  }
  return found;
}

function breadcrumb(path: string) {
  const parts = path.split('/');
  return parts.map((part, i) => (
    <span key={i} className={i === parts.length - 1 ? 'leaf' : 'crumb'}>
      {part}
      {i < parts.length - 1 && <span className="sep">/</span>}
    </span>
  ));
}

function FileActions({ file }: { file: PreviewFile }) {
  const [copied, setCopied] = useState(false);
  if (file.content === undefined) {
    return null;
  }
  const content = file.content;
  const name = file.path.split('/').pop() ?? file.path;
  return (
    <span className="file-actions">
      <button
        type="button"
        className="icon-button"
        onClick={async () => {
          await navigator.clipboard.writeText(content);
          setCopied(true);
          setTimeout(() => setCopied(false), 1500);
        }}
      >
        {copied ? 'Copied' : 'Copy'}
      </button>
      <button
        type="button"
        className="icon-button"
        onClick={() => {
          const url = URL.createObjectURL(new Blob([content], { type: 'text/plain' }));
          const link = document.createElement('a');
          link.href = url;
          link.download = name;
          link.click();
          URL.revokeObjectURL(url);
        }}
      >
        Download
      </button>
    </span>
  );
}

function Contents({ file }: { file: PreviewFile }) {
  const [colored, setColored] = useState<{ content: string; lines?: ThemedToken[][] }>();
  const content = file.content;

  useEffect(() => {
    let current = true;
    if (content !== undefined && content.length <= HIGHLIGHT_LIMIT) {
      tokens(file.path, content)
        .then((lines) => current && setColored({ content, lines }))
        .catch(() => undefined);
    }
    return () => {
      current = false;
    };
  }, [file.path, content]);

  if (content === undefined) {
    return <p className="binary">A binary file of {file.size} bytes. It comes in the zip as it is.</p>;
  }
  const plain = content.replace(/\n$/, '').split('\n');
  const lines = colored?.content === content ? colored.lines : undefined;
  return (
    <pre className="code" aria-label={`Contents of ${file.path}`} style={{ ['--digits' as string]: String(plain.length).length }}>
      <code>
        {lines
          ? lines.map((line, i) => (
              <span key={i} className="line">
                {line.map((token, j) => (
                  <span key={j} style={style(token)}>
                    {token.content}
                  </span>
                ))}
                {'\n'}
              </span>
            ))
          : plain.map((line, i) => (
              <span key={i} className="line">
                {line}
                {'\n'}
              </span>
            ))}
      </code>
    </pre>
  );
}

function style(token: ThemedToken) {
  const fontStyle = token.fontStyle ?? 0;
  return {
    color: token.color,
    fontStyle: fontStyle & 1 ? 'italic' : undefined,
    fontWeight: fontStyle & 2 ? 600 : undefined,
  };
}

function Chevron({ open }: { open: boolean }) {
  return (
    <svg className="chevron" data-open={open} viewBox="0 0 16 16" width="12" height="12" aria-hidden="true">
      <path d="M6 4l4 4-4 4" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  );
}

function FolderIcon({ open = false }: { open?: boolean }) {
  return (
    <svg className="icon folder-icon" viewBox="0 0 16 16" width="15" height="15" aria-hidden="true">
      {open ? (
        <path d="M1.5 4.5a1 1 0 0 1 1-1h3.6l1.4 1.5h5.9a1 1 0 0 1 1 1v.5H4.2L2 12.5h-.5z M3.8 7h11l-2.2 5.5H1.6z" />
      ) : (
        <path d="M1.5 4a1 1 0 0 1 1-1h3.6l1.4 1.5h6a1 1 0 0 1 1 1V12a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1z" />
      )}
    </svg>
  );
}

const KINDS: [RegExp, string][] = [
  [/\.java$/, 'java'],
  [/\.kts?$|\.gradle$/, 'kotlin'],
  [/\.ya?ml$/, 'yaml'],
  [/\.tsx?$|\.m?js$/, 'script'],
  [/\.json$|\.toml$|\.properties$|\.xml$/, 'data'],
  [/\.sql$/, 'sql'],
  [/\.md$/, 'doc'],
  [/(^|\/)(Dockerfile|\.dockerignore)$/, 'docker'],
];

function FileIcon({ path }: { path: string }) {
  const kind = KINDS.find(([pattern]) => pattern.test(path))?.[1] ?? 'plain';
  return (
    <svg className={`icon file-icon ${kind}`} viewBox="0 0 16 16" width="15" height="15" aria-hidden="true">
      <path d="M3.5 1.5h6l3 3v10h-9z" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" />
      <path d="M9.5 1.5v3h3" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" />
    </svg>
  );
}
