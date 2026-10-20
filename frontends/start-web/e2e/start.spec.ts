import { expect, test } from '@playwright/test';

test('the form shows the options the service supports and its baseline', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Generate a service' })).toBeVisible();
  await expect(page.getByText('Preview', { exact: true })).toBeVisible();
  await expect(page.getByRole('radiogroup', { name: 'Architecture' }).getByRole('radio')).toHaveCount(2);
  await expect(page.getByRole('radio', { name: 'PostgreSQL' })).toBeVisible();
  await expect(page.getByText(/Baseline v\d+\.\d+-b\d+/)).toBeVisible();
});

test('the files of the options show before any click, in a tree', async ({ page }) => {
  await page.goto('/');
  const files = page.getByRole('tree', { name: 'Files' });
  await expect(page.getByLabel('Contents of README.md')).toContainText('# demo');
  await page.getByLabel('Name').fill('orders');
  await expect(page.getByText('orders', { exact: true }).first()).toBeVisible();
  await page.getByRole('button', { name: 'Expand all' }).click();
  await files.getByRole('treeitem', { name: 'application.yaml' }).click();
  await expect(page.getByLabel('Contents of src/main/resources/application.yaml')).toContainText('name: orders');
  await files.getByRole('treeitem', { name: 'gradle-wrapper.jar' }).click();
  await expect(page.getByText(/A binary file of \d+ bytes/)).toBeVisible();
});

test('a change of option marks the files it adds and keeps the link', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('radio', { name: 'PostgreSQL' }).check();
  await expect(page.getByRole('status')).toContainText(/\d+ new/);
  await expect(page.getByRole('treeitem', { name: 'roles.sql' })).toBeVisible();
  await expect(page).toHaveURL(/\?database=postgres$/);
});

test('the tree opens, closes and moves with the keyboard', async ({ page }) => {
  await page.goto('/');
  const deploy = page.getByRole('treeitem', { name: 'deploy', exact: true });
  await deploy.focus();
  await expect(deploy).toHaveAttribute('aria-expanded', 'true');
  await page.keyboard.press('ArrowLeft');
  await expect(deploy).toHaveAttribute('aria-expanded', 'false');
  await page.keyboard.press('ArrowRight');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('ArrowRight');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await expect(page.getByLabel('Contents of deploy/base/deployment.yaml')).toContainText('kind: Deployment');
});

test('Generate downloads the zip', async ({ page }) => {
  await page.goto('/?name=billing&architecture=ddd-layered');
  await expect(page.getByRole('radio', { name: 'DDD layered' })).toBeChecked();
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Generate' }).click(),
  ]);
  expect(download.suggestedFilename()).toBe('billing.zip');
  const stream = await download.createReadStream();
  const chunks: Buffer[] = [];
  for await (const chunk of stream) {
    chunks.push(chunk as Buffer);
  }
  const zip = Buffer.concat(chunks);
  expect(zip.subarray(0, 4)).toEqual(Buffer.from([0x50, 0x4b, 0x03, 0x04]));
  expect(zip.includes(Buffer.from('billing/build.gradle.kts'))).toBe(true);
});

test('a rejected field says why, next to its input', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Name').fill('Orders');
  await expect(page.getByLabel('Name')).toHaveAttribute('aria-invalid', 'true');
  await page.getByRole('button', { name: 'Generate' }).click();
  await expect(page.getByText(/must match/)).toBeVisible();
});

test('a terminal gets the same zip through the page', async ({ request }) => {
  const response = await request.post('/starter.zip', { form: { name: 'orders', frontend: 'nextjs' } });
  expect(response.status()).toBe(200);
  expect(response.headers()['content-disposition']).toBe('attachment; filename="orders.zip"');
  const rejected = await request.get('/starter.zip?nme=orders');
  expect(rejected.status()).toBe(400);
  expect((await rejected.json()).errors[0].field).toBe('nme');
});
