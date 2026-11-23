import { chromium } from '@playwright/test';
const browser = await chromium.launch({ headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, deviceScaleFactor: 1 });
await page.goto('http://127.0.0.1:4450', { waitUntil: 'networkidle' });
await page.emulateMedia({ media: 'print' });
await page.pdf({
  path: 'dist/developer-cv.pdf',
  format: 'A4',
  printBackground: true,
  preferCSSPageSize: true,
  margin: { top: '0', right: '0', bottom: '0', left: '0' },
});
await browser.close();
console.log('PDF generated: dist/developer-cv.pdf');