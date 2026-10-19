import { forward } from '../proxy';

// curl https://start.pyahu.io/starter.zip -d name=orders -o orders.zip
export function GET(request: Request) {
  return forward(request, '/starter.zip');
}

export function POST(request: Request) {
  return forward(request, '/starter.zip');
}
