(async()=>{
const { chromium } = require('playwright');
const http=require('http'), fs=require('fs'), path=require('path');
const root = process.cwd();
const types = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript' };
const srv = http.createServer((q, r) => { const f = path.join(root, decodeURIComponent(q.url.split('?')[0])); fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); return; } r.writeHead(200, { 'content-type': types[path.extname(f)] || 'application/octet-stream' }); r.end(d); }); }).listen(8765);
const shots = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const outDir = process.argv[3]; fs.mkdirSync(outDir, { recursive: true });
const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.on('console', m => console.log('[page]', m.text())); page.on('pageerror', e => console.log('[err]', e.message));
await page.goto('http://localhost:8765/index.html');
await page.waitForFunction(() => window.ready === true, null, { timeout: 120000 });
console.log(JSON.stringify(await page.evaluate(() => window.stats()), null, 1));
for (const s of shots) {
  await page.evaluate((c) => window.show(c), s);
  await page.screenshot({ path: path.join(outDir, s.name + '.png') });
  console.log('shot', s.name);
}
await browser.close(); srv.close();
})();
