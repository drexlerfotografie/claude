// Rendert alle Screens als PNG und erstellt den Kontaktbogen.
// Aufruf: node render.js   (aus diesem Ordner)
const fs = require('fs'), path = require('path');
const { chromium } = require('playwright');
const S = require('./screens.js');
const SRC = __dirname, OUT = path.join(__dirname, '..', 'png'), DES = path.join(__dirname, '..');

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const files = S.map((s, i) => {
    const n = String(i + 1).padStart(2, '0');
    const f = `${n}-${s.slug}`;
    fs.writeFileSync(path.join(SRC, f + '.html'), s.html);
    return { ...s, n, f };
  });
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 412, height: 892 }, deviceScaleFactor: 2 });
  const page = await ctx.newPage();
  for (const s of files) {
    await page.goto('file://' + path.join(SRC, s.f + '.html'), { waitUntil: 'networkidle' });
    await page.evaluate(() => document.fonts.ready);
    await page.screenshot({ path: path.join(OUT, s.f + '.png') });
    console.log('ok', s.f);
  }
  // Kontaktbogen
  const sheet = `<!doctype html><html><head><meta charset="utf-8"><link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Oswald:wght@500&family=Roboto:wght@400;500&display=swap"><style>
  body{margin:0;background:#F4F5F7;font-family:Roboto;padding:48px 48px 40px;width:${5*260+4*28+96}px;box-sizing:border-box}
  h1{font-family:Oswald;font-weight:500;font-size:40px;margin:0;color:#11202C}h1 b{font-weight:500;color:#B9303F}
  p{margin:6px 0 30px;color:#5A606B;font-size:16px}
  .g{display:grid;grid-template-columns:repeat(5,260px);gap:34px 28px}
  img{width:260px;height:${Math.round(260*892/412)}px;display:block;box-shadow:0 8px 24px rgba(17,32,44,.18)}
  .c{margin-top:10px;font-size:14px;color:#2D323C}.c span{font-family:Oswald;color:#B9303F;margin-right:6px}</style></head><body>
  <h1>Schicht<b>Clever</b> · 20 Entwürfe</h1><p>Ein mediclever-Produkt · Version 0.1 · Dunkel als Standard, hell bei Tagschicht</p>
  <div class="g">${files.map(s => `<div><img src="png/${s.f}.png"><div class="c"><span>${s.n}</span>${s.title}</div></div>`).join('')}</div></body></html>`;
  fs.writeFileSync(path.join(DES, 'uebersicht.html'), sheet);
  const p2 = await browser.newPage({ viewport: { width: 5*260+4*28+96, height: 800 }, deviceScaleFactor: 1 });
  await p2.goto('file://' + path.join(DES, 'uebersicht.html'), { waitUntil: 'networkidle' });
  await p2.evaluate(() => document.fonts.ready);
  await p2.screenshot({ path: path.join(DES, 'uebersicht.png'), fullPage: true });
  fs.unlinkSync(path.join(DES, 'uebersicht.html'));
  // README
  const md = `# SchichtClever – Entwurfsbilder\n\n20 Screens, 412 × 892 dp, gerendert mit 2× (824 × 1784 px). Quellen in \`src/\`, neu erzeugen mit \`cd src && node render.js\`.\n\nStil: mediclever (Rot #B9303F, Dunkel #11202C, Oswald + Roboto, eckige Kanten). Dunkel ist Standard, Tagschichten hell.\n\n![Übersicht](uebersicht.png)\n\n| Nr. | Screen | Beschreibung |\n|---|---|---|\n${files.map(s => `| ${s.n} | [${s.title}](png/${s.f}.png) | ${s.desc} |`).join('\n')}\n`;
  fs.writeFileSync(path.join(DES, 'README.md'), md);
  await browser.close();
})();
