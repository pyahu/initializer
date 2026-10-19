import { expect, test } from '@playwright/test';

test('the form shows the options the service supports and its baseline', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Generate a service' })).toBeVisible();
  await expect(page.getByText('Preview', { exact: true })).toBeVisible();
  await expect(page.getByRole('radiogroup', { name: 'Architecture' }).getByRole('radio')).toHaveCount(2);
  await expect(page.getByRole('radio', { name: 'PostgreSQL' })).toBeVisible();
  await expect(page.getByText(/Baseline v\d+\.\d+-b\d+/)).toBeVisible();
});

test('Explore shows every file with its contents', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Name').fill('orders');
  await page.getByRole('radio', { name: 'PostgreSQL' }).check();
  await page.getByRole('button', { name: 'Explore' }).click();
  const files = page.getByRole('navigation', { name: 'Files' });
  await expect(files.getByText('orders/', { exact: true }).first()).toBeVisible();
  await expect(files.getByRole('button', { name: 'roles.sql' })).toBeVisible();
  await files.getByRole('button', { name: 'application.yaml' }).click();
  await expect(page.getByLabel('Contents of src/main/resources/application.yaml')).toContainText('name: orders');
  await files.getByRole('button', { name: 'gradle-wrapper.jar' }).click();
  await expect(page.getByText(/A binary file of \d+ bytes/)).toBeVisible();
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
  await page.getByRole('button', { name: 'Generate' }).click();
  await expect(page.getByLabel('Name')).toHaveAttribute('aria-invalid', 'true');
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
