import { forward } from '../proxy';

// The options as JSON, for clients such as a CLI.
export function GET(request: Request) {
  return forward(request, '/metadata');
}
