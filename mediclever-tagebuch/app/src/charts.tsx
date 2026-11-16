import type { JSX } from 'preact';

// Eigene SVG-Diagramme. Farben werden als feste Werte übergeben, damit sich ein Diagramm als Bild teilen lässt.
export interface Theme { text: string; muted: string; line: string; surface: string; accent: string }

export function theme(): Theme {
  const el = document.querySelector('.mct');
  const cs = el ? getComputedStyle(el) : null;
  const v = (n: string, d: string) => cs?.getPropertyValue(n).trim() || d;
  return { text: v('--text', '#2D323C'), muted: v('--muted', '#66717B'), line: v('--line', '#DDE1E5'), surface: v('--surface', '#fff'), accent: v('--accent', '#B9303F') };
}

const FONT = 'Roboto, Arial, sans-serif';

interface LineProps {
  points: { x: number; y: number | null; label?: string }[]; xMax: number; yMin: number; yMax: number; yTicks: { v: number; label: string }[];
  xTicks: { v: number; label: string }[]; colorAt?: (y: number) => string; stroke?: string; marks?: number[]; markLabel?: string; title: string; desc: string; bars?: boolean;
}

/** Linien- oder Balkendiagramm über Tage oder Monate. Lücken bleiben Lücken. */
export function LineChart(p: LineProps): JSX.Element {
  const t = theme();
  const W = 340, H = 170, L = 34, R = 8, T = 10, B = 24;
  const sx = (x: number) => L + ((x - 1) / Math.max(1, p.xMax - 1)) * (W - L - R);
  // Bei Balken beginnt die Achse etwas unter dem kleinsten Wert, damit auch dieser als Balken sichtbar bleibt.
  const lo = p.bars ? p.yMin - (p.yMax - p.yMin) * 0.14 : p.yMin;
  const sy = (y: number) => T + (1 - (y - lo) / (p.yMax - lo)) * (H - T - B);
  const pts = p.points.filter((q) => q.y !== null) as { x: number; y: number }[];
  let d = '';
  let prev: number | null = null;
  for (const q of pts) {
    d += `${prev !== null && q.x - prev <= 3 ? 'L' : 'M'}${sx(q.x).toFixed(1)} ${sy(q.y).toFixed(1)} `;
    prev = q.x;
  }
  const bw = Math.max(3, Math.min(14, (W - L - R) / p.xMax - 2));
  return (
    <svg class="mct-chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label={p.title + '. ' + p.desc} font-family={FONT}>
      {p.yTicks.map((k) => (
        <g key={k.v}>
          <line x1={L} x2={W - R} y1={sy(k.v)} y2={sy(k.v)} stroke={t.line} stroke-width="1" />
          <text x={L - 6} y={sy(k.v) + 4} text-anchor="end" font-size="10" fill={t.muted}>{k.label}</text>
        </g>
      ))}
      {p.xTicks.map((k) => <text key={k.v} x={sx(k.v)} y={H - 6} text-anchor="middle" font-size="10" fill={t.muted}>{k.label}</text>)}
      {(p.marks || []).map((x) => <rect key={x} x={sx(x) - bw / 2 - 1} y={T} width={bw + 2} height={H - T - B} fill={t.accent} opacity="0.13" rx="2" />)}
      {p.bars
        ? pts.map((q) => <rect key={q.x} x={sx(q.x) - bw / 2} y={sy(q.y)} width={bw} height={Math.max(1, sy(lo) - sy(q.y))} rx="2" fill={p.colorAt ? p.colorAt(q.y) : p.stroke || t.accent} />)
        : <>
          <path d={d} fill="none" stroke={p.stroke || t.muted} stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
          {pts.map((q) => <circle key={q.x} cx={sx(q.x)} cy={sy(q.y)} r="3.5" fill={p.colorAt ? p.colorAt(q.y) : p.stroke || t.accent} stroke={t.surface} stroke-width="1.5" />)}
        </>}
    </svg>
  );
}

function arc(cx: number, cy: number, r: number, a0: number, a1: number): string {
  const p = (a: number) => `${(cx + r * Math.cos(a)).toFixed(2)} ${(cy + r * Math.sin(a)).toFixed(2)}`;
  return `M${p(a0)} A${r} ${r} 0 ${a1 - a0 > Math.PI ? 1 : 0} 1 ${p(a1)}`;
}

/** Halbring (Stimmungszähler) oder voller Ring (Verteilung). */
export function Ring({ parts, half, center, sub, title }: { parts: { value: number; color: string; label: string }[]; half?: boolean; center: string; sub?: string; title: string }): JSX.Element {
  const t = theme();
  const total = parts.reduce((s, x) => s + x.value, 0);
  const W = 220, H = half ? 124 : 200, cx = 110, cy = half ? 110 : 100, r = 82;
  const start = half ? Math.PI : -Math.PI / 2;
  const span = half ? Math.PI : Math.PI * 2;
  const gap = total > 0 && parts.filter((x) => x.value > 0).length > 1 ? 0.03 : 0;
  let a = start;
  return (
    <svg class="mct-chart ring" viewBox={`0 0 ${W} ${H}`} role="img" font-family={FONT}
      aria-label={title + ': ' + (total === 0 ? 'keine Daten' : parts.filter((x) => x.value > 0).map((x) => `${x.label} ${x.value}`).join(', '))}>
      <path d={arc(cx, cy, r, start, start + span - 0.0001)} fill="none" stroke={t.line} stroke-width="20" stroke-linecap={half ? 'round' : 'butt'} />
      {total > 0 && parts.map((x, i) => {
        if (x.value <= 0) return null;
        const len = (x.value / total) * span;
        const el = <path key={i} d={arc(cx, cy, r, a + gap / 2, Math.max(a + gap / 2 + 0.001, a + len - gap / 2))} fill="none" stroke={x.color} stroke-width="20" />;
        a += len;
        return el;
      })}
      <text x={cx} y={half ? cy - 16 : cy + 4} text-anchor="middle" font-size="30" font-weight="800" fill={t.text}>{center}</text>
      {sub && <text x={cx} y={half ? cy + 4 : cy + 24} text-anchor="middle" font-size="12" fill={t.muted}>{sub}</text>}
    </svg>
  );
}

/** Waagerechte Balken mit Beschriftung links und Wert rechts. */
export function Bars({ rows, max, title }: { rows: { label: string; value: number; text: string; color: string }[]; max: number; title: string }): JSX.Element {
  return (
    <div class="mct-bars" role="list" aria-label={title}>
      {rows.map((r, i) => (
        <div class="mct-bar" role="listitem" key={i}>
          <span class="mct-bar-label">{r.label}</span>
          <span class="mct-bar-track"><span style={{ width: `${Math.max(2, (r.value / (max || 1)) * 100)}%`, background: r.color }} /></span>
          <span class="mct-bar-value">{r.text}</span>
        </div>
      ))}
    </div>
  );
}

/** Wandelt ein Diagramm (SVG) samt Titel in ein PNG um und teilt es oder lädt es herunter. */
export async function shareChart(card: HTMLElement, title: string): Promise<void> {
  const svg = card.querySelector('svg.mct-chart') as SVGSVGElement | null;
  const t = theme();
  const scale = 3;
  const W = 360;
  const lines = Array.from(card.querySelectorAll('[data-share]')).map((e) => (e.textContent || '').trim()).filter(Boolean).slice(0, 8);
  let svgH = 0;
  let img: HTMLImageElement | null = null;
  if (svg) {
    const vb = svg.viewBox.baseVal;
    svgH = Math.round(((W - 20) / vb.width) * vb.height);
    const clone = svg.cloneNode(true) as SVGSVGElement;
    clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
    clone.setAttribute('width', String(W - 20));
    clone.setAttribute('height', String(svgH));
    img = new Image();
    img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(new XMLSerializer().serializeToString(clone));
    await img.decode();
  }
  const H = 56 + svgH + lines.length * 22 + 44;
  const canvas = document.createElement('canvas');
  canvas.width = W * scale;
  canvas.height = H * scale;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  ctx.scale(scale, scale);
  ctx.fillStyle = t.surface;
  ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = t.text;
  ctx.font = '800 18px Roboto, Arial, sans-serif';
  ctx.fillText(title, 16, 34);
  if (img) ctx.drawImage(img, 10, 48, W - 20, svgH);
  ctx.font = '400 14px Roboto, Arial, sans-serif';
  lines.forEach((l, i) => ctx.fillText(l.length > 46 ? l.slice(0, 45) + '…' : l, 16, 70 + svgH + i * 22));
  ctx.fillStyle = t.muted;
  ctx.font = '500 11px Roboto, Arial, sans-serif';
  ctx.fillText('MediClever Tagebuch', 16, H - 16);
  const blob: Blob | null = await new Promise((res) => canvas.toBlob(res, 'image/png'));
  if (!blob) return;
  const file = new File([blob], 'auswertung.png', { type: 'image/png' });
  if (navigator.canShare?.({ files: [file] })) {
    try { await navigator.share({ files: [file], title }); return; } catch { /* abgebrochen: stattdessen herunterladen */ }
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'auswertung.png';
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}
