// Liest ein ZIP-Archiv direkt im Browser, ohne es als Ganzes in den Speicher zu laden.
// Nötig, weil ein Upload von mehreren hundert MB an den Grenzen eines einfachen Webspace scheitert.
export interface ZipEntry { name: string; base: string; method: number; compSize: number; size: number; offset: number }

const u16 = (v: DataView, o: number) => v.getUint16(o, true);
const u32 = (v: DataView, o: number) => v.getUint32(o, true);

async function view(file: Blob, start: number, len: number): Promise<DataView> {
  return new DataView(await file.slice(start, start + len).arrayBuffer());
}

/** Normalisiert einen Pfad aus dem Archiv: führende Schrägstriche weg, keine Sprünge nach oben. */
export function safePath(name: string): string {
  return name.replace(/\\/g, '/').split('/').filter((p) => p !== '' && p !== '.' && p !== '..').join('/');
}

export async function readZip(file: Blob): Promise<ZipEntry[]> {
  const tailLen = Math.min(file.size, 65557);
  const tail = await view(file, file.size - tailLen, tailLen);
  let eocd = -1;
  for (let i = tailLen - 22; i >= 0; i--) {
    if (u32(tail, i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('Die Datei ist kein lesbares Archiv.');
  const count = u16(tail, eocd + 10);
  const cdSize = u32(tail, eocd + 12);
  const cdOffset = u32(tail, eocd + 16);
  if (count === 0xffff || cdOffset === 0xffffffff) throw new Error('Dieses Archiv ist zu groß für den Import im Browser (ZIP64).');
  const cd = await view(file, cdOffset, cdSize);
  const dec = new TextDecoder();
  const out: ZipEntry[] = [];
  let p = 0;
  for (let i = 0; i < count && p + 46 <= cdSize; i++) {
    if (u32(cd, p) !== 0x02014b50) break;
    const nameLen = u16(cd, p + 28);
    const name = safePath(dec.decode(new Uint8Array(cd.buffer, cd.byteOffset + p + 46, nameLen)));
    out.push({ name, base: name.split('/').pop() || '', method: u16(cd, p + 10), compSize: u32(cd, p + 20), size: u32(cd, p + 24), offset: u32(cd, p + 42) });
    p += 46 + nameLen + u16(cd, p + 30) + u16(cd, p + 32);
  }
  return out;
}

export async function zipBlob(file: Blob, e: ZipEntry): Promise<Blob> {
  const head = await view(file, e.offset, 30);
  if (u32(head, 0) !== 0x04034b50) throw new Error('Das Archiv ist beschädigt.');
  const start = e.offset + 30 + u16(head, 26) + u16(head, 28);
  const raw = file.slice(start, start + e.compSize);
  if (e.method === 0) return raw;
  if (e.method !== 8) throw new Error('Das Archiv nutzt ein nicht unterstütztes Packverfahren.');
  return new Response(raw.stream().pipeThrough(new DecompressionStream('deflate-raw'))).blob();
}

export async function sha256(text: string): Promise<string> {
  const buf = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buf), (b) => b.toString(16).padStart(2, '0')).join('');
}

/** CSV nach RFC 4180: Anführungszeichen, Kommas und Zeilenumbrüche in Feldern. */
export function parseCsv(text: string): Record<string, string>[] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = '';
  let quoted = false;
  const t = text.replace(/^﻿/, '');
  for (let i = 0; i < t.length; i++) {
    const c = t[i];
    if (quoted) {
      if (c === '"' && t[i + 1] === '"') { cell += '"'; i++; } else if (c === '"') quoted = false; else cell += c;
    } else if (c === '"') quoted = true;
    else if (c === ',') { row.push(cell); cell = ''; } else if (c === '\n' || c === '\r') {
      if (c === '\r' && t[i + 1] === '\n') i++;
      row.push(cell); cell = '';
      if (row.length > 1 || row[0] !== '') rows.push(row);
      row = [];
    } else cell += c;
  }
  if (cell !== '' || row.length) { row.push(cell); rows.push(row); }
  const head = rows.shift() || [];
  return rows.map((r) => Object.fromEntries(head.map((h, i) => [h.trim(), r[i] ?? ''])));
}
