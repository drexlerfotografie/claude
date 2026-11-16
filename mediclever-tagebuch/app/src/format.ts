// Datum, Zahlen und Farben. Durchgehend deutsch.
export const MONTHS = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
export const WEEKDAYS = ['Sonntag', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag'];
export const WD_SHORT = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
export const LEVEL_NAMES = ['', 'Sehr gut', 'Gut', 'Mittel', 'Schlecht', 'Sehr schlecht'];

export const pad = (n: number): string => String(n).padStart(2, '0');
export const parseDate = (d: string): Date => { const [y, m, day] = d.split('-').map(Number); return new Date(y, m - 1, day); };
export const iso = (d: Date): string => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
export const addDays = (d: string, n: number): string => { const x = parseDate(d); x.setDate(x.getDate() + n); return iso(x); };
export const addMonths = (m: string, n: number): string => { const [y, mo] = m.split('-').map(Number); const x = new Date(y, mo - 1 + n, 1); return `${x.getFullYear()}-${pad(x.getMonth() + 1)}`; };
export const monthLabel = (m: string): string => { const [y, mo] = m.split('-').map(Number); return `${MONTHS[mo - 1]} ${y}`; };
export const daysInMonth = (m: string): number => { const [y, mo] = m.split('-').map(Number); return new Date(y, mo, 0).getDate(); };
export const dayLabel = (d: string): string => { const x = parseDate(d); return `${WEEKDAYS[x.getDay()]}, ${x.getDate()}. ${MONTHS[x.getMonth()]}`; };
export const dayLabelYear = (d: string): string => `${dayLabel(d)} ${d.slice(0, 4)}`;
export const shortDate = (d: string): string => { const x = parseDate(d); return `${x.getDate()}.${x.getMonth() + 1}.${x.getFullYear()}`; };
export const diffDays = (a: string, b: string): number => Math.round((parseDate(a).getTime() - parseDate(b).getTime()) / 86400000);
export const nowTime = (): string => { const d = new Date(); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; };
export const num = (n: number | null | undefined, digits = 1): string => (n === null || n === undefined ? '–' : n.toLocaleString('de-DE', { minimumFractionDigits: digits, maximumFractionDigits: digits }));
export const signed = (n: number | null | undefined, digits = 0): string => (n === null || n === undefined ? '–' : (n > 0 ? '+' : n < 0 ? '−' : '±') + Math.abs(n).toLocaleString('de-DE', { minimumFractionDigits: digits, maximumFractionDigits: digits }));
export const duration = (min: number): string => `${Math.floor(min / 60)}:${pad(Math.round(min % 60))} Std.`;
export const plural = (n: number, one: string, many: string): string => `${n.toLocaleString('de-DE')} ${n === 1 ? one : many}`;

/** Farbschemata für die fünf Stufen (Stufe 1 = beste). Eigene MediClever-Namen und -Werte. */
export const SCHEMES: Record<string, { label: string; colors: string[] }> = {
  klar: { label: 'Klar', colors: ['#2D7553', '#3F8F87', '#57789B', '#B8741F', '#B9303F'] },
  ruhig: { label: 'Ruhig', colors: ['#1F6F8B', '#3F8AA0', '#6F8494', '#84709C', '#6A4C7D'] },
  warm: { label: 'Warm', colors: ['#B87A12', '#C0663A', '#A66064', '#7D5A7A', '#4E4A6B'] },
  grau: { label: 'Zurückhaltend', colors: ['#11202C', '#3A4753', '#66717B', '#8A6A4F', '#962331'] },
};
/** Im dunklen Modus hellere Varianten, damit Symbole und Flächen erkennbar bleiben. */
const DARK: Record<string, string[]> = {
  klar: ['#5FBF92', '#62BDB3', '#8FB0D3', '#E0A650', '#F0707D'],
  ruhig: ['#5FB7D3', '#7FC3D6', '#A9BBC8', '#B8A6CE', '#B592CB'],
  warm: ['#E8B04A', '#EE9A6E', '#D99A9E', '#BC96B8', '#9A96BD'],
  grau: ['#E8ECEF', '#B7C0C8', '#8E99A3', '#C9A587', '#F0707D'],
};

export const PALETTE = ['#B9303F', '#BF5A26', '#A86F14', '#6F7F1F', '#2D7553', '#1F7F76', '#2476A0', '#3F62B5', '#6A55B0', '#96469E', '#B0457A', '#66717B'];
const PALETTE_DARK = ['#F0707D', '#F09462', '#E0A650', '#B5C55A', '#5FBF92', '#58C2B7', '#62B3DD', '#8FA8F0', '#A999EC', '#D08AD8', '#EA86B8', '#A3AEB8'];

export const isDark = (): boolean => document.querySelector('.mct')?.getAttribute('data-mode') === 'dark';

export function levelColors(scheme: string, custom: string[]): string[] {
  if (scheme === 'eigen' && custom.length === 5) return custom;
  const key = SCHEMES[scheme] ? scheme : 'klar';
  return isDark() ? DARK[key] : SCHEMES[key].colors;
}

/** Löst eine gespeicherte Farbe (c1 bis c12 oder Hex) in einen Farbwert auf. */
export function color(c: string | null | undefined, fallback = 'c12'): string {
  const v = c || fallback;
  const m = /^c(\d{1,2})$/.exec(v);
  if (m) return (isDark() ? PALETTE_DARK : PALETTE)[(Number(m[1]) - 1) % 12];
  return /^#[0-9a-f]{6}$/i.test(v) ? v : PALETTE[11];
}

/** Sätze für den Tag: eigene, ruhige Texte. Auswahl nach Kalendertag. */
const QUOTES = [
  'Ein Eintrag muss nicht lang sein, um zu zählen.', 'Auch ein mittelmäßiger Tag ist ein vollständiger Tag.', 'Du musst heute nichts bewerten. Aufschreiben reicht.',
  'Kleine Schritte sind auch Schritte.', 'Was dir heute gutgetan hat, darf morgen wiederkommen.', 'Schlechte Tage gehören in dieselbe Liste wie gute.',
  'Erst notieren, dann einordnen.', 'Nach einer langen Schicht zählt auch das Ausruhen.', 'Muster zeigen sich erst über Wochen. Bleib gelassen.',
  'Heute reicht ein Satz.', 'Pausen sind Teil der Arbeit.', 'Du kennst deinen Alltag am besten. Schreib ihn so auf, wie er ist.',
  'Niemand liest mit. Schreib ehrlich.', 'Ein Tag ohne Eintrag ist kein Rückschritt.', 'Was hat heute Kraft gekostet, was hat Kraft gegeben?',
  'Manchmal ist „okay“ die genaueste Antwort.', 'Gewohnheiten entstehen leise.', 'Schau zurück, um zu verstehen, nicht um zu urteilen.',
  'Dein Tagebuch muss niemandem gefallen.', 'Eine Sache, die heute geklappt hat, genügt.', 'Müdigkeit ist eine Information, kein Versagen.',
  'Ordnung im Kopf beginnt oft mit drei Stichworten.', 'Du darfst stolz auf Kleinigkeiten sein.', 'Auch Routinen verdienen einen Eintrag.',
  'Der Rückblick wird leichter, wenn du heute kurz anhältst.', 'Schreib auf, was war. Der Rest ergibt sich.', 'Gute Tage erkennt man manchmal erst im Nachhinein.',
  'Zwei Minuten für dich sind keine verlorene Zeit.', 'Was würdest du einer Kollegin heute raten?', 'Du musst nicht jeden Tag gleich viel schaffen.',
  'Ein ehrlicher Eintrag hilft dir mehr als ein schöner.',
];
export const quoteFor = (date: string): string => QUOTES[(parseDate(date).getDate() + parseDate(date).getMonth() * 7) % QUOTES.length];
