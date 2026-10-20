// The project as a tree: folders first, then files, each by name. A folder with one folder and nothing else is
// shown as one row, as in an IDE, so src/main/java/com/example/demo takes one line instead of five.
export type Node = Folder | Leaf;
export type Folder = { kind: 'folder'; path: string; name: string; children: Node[] };
export type Leaf = { kind: 'file'; path: string; name: string };

export function tree(paths: string[]): Folder {
  const root: Folder = { kind: 'folder', path: '', name: '', children: [] };
  for (const path of paths) {
    const parts = path.split('/');
    let folder = root;
    parts.slice(0, -1).forEach((part, i) => {
      const at = parts.slice(0, i + 1).join('/');
      let next = folder.children.find((c): c is Folder => c.kind === 'folder' && c.path === at);
      if (!next) {
        next = { kind: 'folder', path: at, name: part, children: [] };
        folder.children.push(next);
      }
      folder = next;
    });
    folder.children.push({ kind: 'file', path, name: parts[parts.length - 1] });
  }
  return { ...root, children: arrange(root.children) };
}

function arrange(nodes: Node[]): Node[] {
  return nodes
    .map((node) => (node.kind === 'folder' ? compact(node) : node))
    .sort((a, b) => (a.kind === b.kind ? a.name.localeCompare(b.name, 'en') : a.kind === 'folder' ? -1 : 1));
}

function compact(folder: Folder): Folder {
  let current = folder;
  let name = folder.name;
  while (current.children.length === 1 && current.children[0].kind === 'folder') {
    current = current.children[0];
    name = `${name}/${current.name}`;
  }
  return { kind: 'folder', path: current.path, name, children: arrange(current.children) };
}

/** The folders that hold a path, outermost first, as the tree shows them. */
export function ancestors(root: Folder, path: string): string[] {
  const found: string[] = [];
  let folder: Folder | undefined = root;
  while (folder) {
    const next: Folder | undefined = folder.children.find(
      (c): c is Folder => c.kind === 'folder' && path.startsWith(`${c.path}/`),
    );
    if (next) {
      found.push(next.path);
    }
    folder = next;
  }
  return found;
}

export type Row = { node: Node; depth: number; parent?: string };

/** The rows on screen: every node whose folders are all open. */
export function visible(root: Folder, open: ReadonlySet<string>): Row[] {
  const rows: Row[] = [];
  const walk = (nodes: Node[], depth: number, parent?: string) => {
    for (const node of nodes) {
      rows.push({ node, depth, parent });
      if (node.kind === 'folder' && open.has(node.path)) {
        walk(node.children, depth + 1, node.path);
      }
    }
  };
  walk(root.children, 0);
  return rows;
}

export function folders(root: Folder): string[] {
  const all: string[] = [];
  const walk = (nodes: Node[]) => {
    for (const node of nodes) {
      if (node.kind === 'folder') {
        all.push(node.path);
        walk(node.children);
      }
    }
  };
  walk(root.children);
  return all;
}
