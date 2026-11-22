import * as THREE from 'three';

// ---------------------------------------------------------------- geometry
export class TriBuilder {
  constructor() { this.p = []; }
  tri(a, b, c) { this.p.push(...a, ...b, ...c); }
  quad(a, b, c, d) { this.tri(a, b, c); this.tri(a, c, d); }
  geo() {
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.Float32BufferAttribute(this.p, 3));
    g.computeVertexNormals();
    return g;
  }
}

export function outlineNormals(pts) {
  const out = [];
  for (let i = 0; i < pts.length; i++) {
    const p = pts[i];
    let n1 = null, n2 = null;
    if (i > 0) { const a = pts[i - 1]; const dx = p[0] - a[0], dy = p[1] - a[1], l = Math.hypot(dx, dy); n1 = [dy / l, -dx / l]; }
    if (i < pts.length - 1) { const b = pts[i + 1]; const dx = b[0] - p[0], dy = b[1] - p[1], l = Math.hypot(dx, dy); n2 = [dy / l, -dx / l]; }
    n1 = n1 || n2; n2 = n2 || n1;
    let mx = n1[0] + n2[0], my = n1[1] + n2[1]; const ml = Math.hypot(mx, my) || 1; mx /= ml; my /= ml;
    const s = 1 / Math.max(0.6, mx * n1[0] + my * n1[1]);
    out.push([mx * s, my * s]);
  }
  return out;
}
// Profil verfeinern (Catmull-Rom), Originalpunkt i liegt danach auf Index i*n
export function refine(pts, n, sharp = new Set()) {
  if (n <= 1) return pts.slice();
  const out = [];
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(pts.length - 1, i + 2)];
    out.push(p1);
    for (let k = 1; k < n; k++) {
      const t = k / n;
      if (sharp.has(i) || sharp.has(i + 1)) { out.push([p1[0] + (p2[0] - p1[0]) * t, p1[1] + (p2[1] - p1[1]) * t]); continue; }
      const t2 = t * t, t3 = t2 * t;
      const f = (a, b, c, d) => 0.5 * ((2 * b) + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (-a + 3 * b - 3 * c + d) * t3);
      out.push([f(p0[0], p1[0], p2[0], p3[0]), f(p0[1], p1[1], p2[1], p3[1])]);
    }
  }
  out.push(pts[pts.length - 1]);
  return out;
}
export const edgeSet = (list, n) => new Set(list.flatMap(e => Array.from({ length: n }, (_, k) => e * n + k)));
export const shrink = (pts, r) => { const ns = outlineNormals(pts); return pts.map((p, i) => [p[0] - ns[i][0] * r, p[1] - ns[i][1] * r]); };

// Hülle über das Seitenprofil, mit gerundeten Schultern (k) und gewölbter Mitte (m, crown)
export function skin(pts, i0, i1, o) {
  const ns = outlineNormals(pts);
  const body = new TriBuilder(), glass = new TriBuilder();
  const rows = [];
  const m = o.m || 1, crown = o.crown || 0;
  for (let i = i0; i <= i1; i++) {
    const p = pts[i], n = ns[i], row = [];
    const cx = p[0] - n[0] * o.r, cy = p[1] - n[1] * o.r, zc = o.W / 2 - o.r;
    for (let s = 0; s <= o.k; s++) { const a = s / o.k * Math.PI / 2; row.push([cx + n[0] * o.r * Math.sin(a), cy + n[1] * o.r * Math.sin(a), zc + o.r * Math.cos(a)]); }
    for (let j = 1; j < m; j++) { const z = zc - 2 * zc * j / m, f = 1 - (z / zc) ** 2; row.push([p[0] + n[0] * crown * f, p[1] + n[1] * crown * f, z]); }
    for (let s = o.k; s >= 0; s--) { const a = s / o.k * Math.PI / 2; row.push([cx + n[0] * o.r * Math.sin(a), cy + n[1] * o.r * Math.sin(a), -zc - o.r * Math.cos(a)]); }
    rows.push(row);
  }
  for (let j = 0; j < rows.length - 1; j++) {
    const A = rows[j], B = rows[j + 1], ei = i0 + j;
    for (let c = 0; c < A.length - 1; c++) {
      const mid = c >= o.k && c < o.k + m;
      if (o.only === 'mid' && !mid) continue;
      if (o.only === 'arcs' && mid) continue;
      const tb = mid && o.glass && o.glass.has(ei) ? glass : body;
      tb.quad(A[c], A[c + 1], B[c + 1], B[c]);
    }
  }
  return { body: body.geo(), glass: glass.p.length ? glass.geo() : null };
}

const toV2 = (a) => a.map(p => new THREE.Vector2(p[0], p[1]));
export function polyShape(pts, holes = []) {
  const s = new THREE.Shape(toV2(pts));
  holes.forEach(h => s.holes.push(new THREE.Path(toV2(h))));
  return s;
}
export function grow(poly, d) { // grob nach außen versetzen
  const c = poly.reduce((a, p) => [a[0] + p[0] / poly.length, a[1] + p[1] / poly.length], [0, 0]);
  return poly.map(p => { const dx = p[0] - c[0], dy = p[1] - c[1], l = Math.hypot(dx, dy); return [p[0] + dx / l * d, p[1] + dy / l * d]; });
}
function flipWinding(g) {
  g = g.index ? g.toNonIndexed() : g;
  const a = g.attributes.position.array;
  for (let i = 0; i < a.length; i += 9) for (let k = 0; k < 3; k++) { const t = a[i + 3 + k]; a[i + 3 + k] = a[i + 6 + k]; a[i + 6 + k] = t; }
  g.computeVertexNormals();
  return g;
}
// flache Wand im xy-Profil bei z, nach außen orientiert (side = +1 rechts / -1 links)
export function wallXY(pts, holes, z, side) {
  let g = new THREE.ShapeGeometry(polyShape(pts, holes)).toNonIndexed();
  if (side < 0) g = flipWinding(g);
  g.translate(0, 0, z);
  return g;
}
// Wand in der zy-Ebene bei x (Front/Heck), side=+1 zeigt nach +x
export function wallZY(pts, holes, x, side) {
  let g = new THREE.ShapeGeometry(polyShape(pts, holes)).toNonIndexed(); // (z,y) -> x,y
  g.rotateY(-Math.PI / 2); // Normale +z -> -x?  rotateY(-90): z -> -x
  if (side > 0) g = flipWinding(g);
  // nach rotateY(-90°) liegt die alte x-Achse (unser z) auf +z: passt.
  g.translate(x, 0, 0);
  return g;
}
// extrudierte Platte (Tür) im xy-Profil, Außenseite bei z
export function plateXY(pts, holes, zOuter, side, depth = 0.045) {
  let g = new THREE.ExtrudeGeometry(polyShape(pts, holes), { depth, bevelEnabled: false }).toNonIndexed();
  g.translate(0, 0, side > 0 ? zOuter - depth : zOuter);
  return g;
}
export function plateZY(pts, holes, xOuter, side, depth = 0.045) {
  let g = new THREE.ExtrudeGeometry(polyShape(pts, holes), { depth, bevelEnabled: false }).toNonIndexed();
  g.rotateY(-Math.PI / 2); // extrude z -> -x
  g.translate(side > 0 ? xOuter + 0 + depth : xOuter + depth, 0, 0);
  if (side > 0) g.translate(-depth, 0, 0);
  return g;
}

// ---------------------------------------------------------------- Beklebung / UV
const clamp01 = (v) => Math.min(1, Math.max(0, v));
export function liveryUV(geo, S, opt = {}) {
  geo = geo.index ? geo.toNonIndexed() : geo;
  const pos = geo.attributes.position, uv = new Float32Array(pos.count * 2);
  const a = new THREE.Vector3(), b = new THREE.Vector3(), c = new THREE.Vector3(), e1 = new THREE.Vector3(), e2 = new THREE.Vector3();
  for (let i = 0; i < pos.count; i += 3) {
    a.fromBufferAttribute(pos, i); b.fromBufferAttribute(pos, i + 1); c.fromBufferAttribute(pos, i + 2);
    e1.subVectors(b, a); e2.subVectors(c, a); const n = e1.cross(e2).normalize();
    let mode;
    if (opt.innerZ !== undefined && n.z * opt.innerZ < -0.5) mode = 'grey';
    else if (opt.innerX !== undefined && n.x * opt.innerX < -0.5) mode = 'grey';
    else if (Math.abs(n.x) > Math.max(Math.abs(n.y), Math.abs(n.z)) * 1.15) mode = n.x > 0 ? 'front' : 'rear';
    else if (Math.abs(n.y) > Math.abs(n.z)) mode = 'right';
    else mode = n.z >= 0 ? 'right' : 'left';
    [a, b, c].forEach((v, k) => {
      const fy = clamp01(v.y / S.H) * 0.98, fz = clamp01((v.z + S.W / 2) / S.W);
      let u, w;
      if (mode === 'grey') { u = 0.995; w = 0.995; }
      else if (mode === 'front') { u = 0.70 + 0.15 * (1 - fz); w = 0.5 + 0.5 * fy; }
      else if (mode === 'rear') { u = 0.85 + 0.14 * fz; w = 0.5 + 0.5 * fy; }
      else if (mode === 'right') { u = 0.70 * clamp01((v.x - S.xmin) / S.L); w = 0.5 + 0.5 * fy; }
      else { u = 0.70 * clamp01((S.xmin + S.L - v.x) / S.L); w = 0.5 * fy; }
      uv[(i + k) * 2] = u; uv[(i + k) * 2 + 1] = w;
    });
  }
  geo.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
  return geo;
}

// Zeichenhilfe für den Beklebungs-Atlas (2048x1024)
export class LiveryPainter {
  constructor(S) {
    this.S = S;
    this.cv = document.createElement('canvas'); this.cv.width = 2048; this.cv.height = 1024;
    this.g = this.cv.getContext('2d');
    this.g.fillStyle = '#f3f4f5'; this.g.fillRect(0, 0, 2048, 1024);
  }
  Y(y, left) { return (left ? 1024 : 512) - 512 * 0.98 * clamp01(y / this.S.H) * 1.0; }
  X(x, left) { const S = this.S; return 0.70 * 2048 * (left ? (S.xmin + S.L - x) : (x - S.xmin)) / S.L; }
  sideRect(x0, x1, y0, y1, col) {
    for (const left of [false, true]) {
      const X0 = this.X(x0, left), X1 = this.X(x1, left), Y0 = this.Y(y0, left), Y1 = this.Y(y1, left);
      this.g.fillStyle = col; this.g.fillRect(Math.min(X0, X1), Math.min(Y0, Y1), Math.abs(X1 - X0) + 0.6, Math.abs(Y1 - Y0) + 0.6);
    }
  }
  checker(x0, x1, y0, y1, cs, c1, c2, phase = 0) {
    let col = 0;
    for (let x = x0; x < x1 - 1e-4; x += cs, col++) {
      let row = 0;
      for (let y = y0; y < y1 - 1e-4; y += cs, row++) {
        this.sideRect(x, Math.min(x + cs, x1), y, Math.min(y + cs, y1), (col + row + phase) % 2 ? c2 : c1);
      }
    }
  }
  sidePoly(pts, col) {
    for (const left of [false, true]) {
      const g = this.g; g.fillStyle = col; g.beginPath();
      pts.forEach((p, i) => { const X = this.X(p[0], left), Y = this.Y(p[1], left); i ? g.lineTo(X, Y) : g.moveTo(X, Y); });
      g.closePath(); g.fill();
    }
  }
  sideText(txt, x, y, size, col, font = 'bold') {
    for (const left of [false, true]) {
      const g = this.g; g.save(); g.fillStyle = col;
      const px = size / this.S.H * 512 * 0.98;
      g.font = `${font} ${px}px "DejaVu Sans Condensed", Arial Narrow, sans-serif`;
      g.textAlign = 'center'; g.textBaseline = 'middle';
      g.fillText(txt, this.X(x, left), this.Y(y, left)); g.restore();
    }
  }
  // Front/Heck: z von -W/2..W/2 , y
  endRect(which, z0, z1, y0, y1, col) {
    const S = this.S, base = which === 'front' ? 0.70 : 0.85, span = which === 'front' ? 0.15 : 0.14;
    const fz = (z) => which === 'front' ? 1 - (z + S.W / 2) / S.W : (z + S.W / 2) / S.W;
    const X0 = (base + span * fz(z0)) * 2048, X1 = (base + span * fz(z1)) * 2048;
    const Y0 = this.Y(y0), Y1 = this.Y(y1);
    this.g.fillStyle = col; this.g.fillRect(Math.min(X0, X1), Math.min(Y0, Y1), Math.abs(X1 - X0) + 0.6, Math.abs(Y1 - Y0) + 0.6);
  }
  endChecker(which, z0, z1, y0, y1, cs, c1, c2) {
    let i = 0;
    for (let z = z0; z < z1 - 1e-4; z += cs, i++) { let j = 0; for (let y = y0; y < y1 - 1e-4; y += cs, j++) this.endRect(which, z, Math.min(z + cs, z1), y, Math.min(y + cs, y1), (i + j) % 2 ? c2 : c1); }
  }
  texture() {
    this.g.fillStyle = '#c9ced4'; this.g.fillRect(2028, 0, 20, 12); // Innenseite
    const t = new THREE.CanvasTexture(this.cv);
    t.colorSpace = THREE.SRGBColorSpace; t.anisotropy = 8;
    return t;
  }
}

// ---------------------------------------------------------------- Materialien
export function makeMaterials(liveryTex) {
  const std = (c, o = {}) => new THREE.MeshStandardMaterial({ color: c, roughness: 0.6, metalness: 0.05, flatShading: true, ...o });
  const body = new THREE.MeshStandardMaterial({ map: liveryTex, roughness: 0.38, metalness: 0.1, flatShading: true, side: THREE.DoubleSide });
  body.onBeforeCompile = (sh) => {
    sh.fragmentShader = sh.fragmentShader.replace('#include <map_fragment>', `
      #ifdef USE_MAP
        vec4 sampledDiffuseColor = texture2D( map, vMapUv );
        if (!gl_FrontFacing) sampledDiffuseColor = vec4(0.62,0.65,0.69,1.0);
        diffuseColor *= sampledDiffuseColor;
      #endif`);
  };
  const lens = (c, e) => new THREE.MeshStandardMaterial({ color: c, emissive: e, emissiveIntensity: 0, roughness: 0.15, metalness: 0.1, flatShading: true });
  return {
    body,
    door: body,
    glass: new THREE.MeshStandardMaterial({ color: 0x1d2b36, roughness: 0.05, metalness: 0.5, transparent: true, opacity: 0.42, side: THREE.DoubleSide, depthWrite: false }),
    frosted: new THREE.MeshStandardMaterial({ color: 0xdfe6ea, roughness: 0.3, metalness: 0.1, transparent: true, opacity: 0.82, side: THREE.DoubleSide }),
    glassBroken: new THREE.MeshStandardMaterial({ color: 0xcfd6dc, roughness: 0.8, metalness: 0.0, transparent: true, opacity: 0.75, side: THREE.DoubleSide, flatShading: true }),
    black: std(0x1b1c1e, { roughness: 0.8 }),
    darkgrey: std(0x3a3d42, { roughness: 0.7 }),
    grey: std(0x8d9299),
    steel: std(0xb9bec5, { metalness: 0.6, roughness: 0.35 }),
    alu: std(0xc4c8cc, { metalness: 0.5, roughness: 0.45 }),
    white: std(0xf1f2f3, { roughness: 0.5 }),
    cabinet: std(0xf4f5f6, { roughness: 0.4 }),
    trimBlue: std(0x1f3f9a, { roughness: 0.5 }),
    seatBlue: std(0x24408e, { roughness: 0.85 }),
    seatBlack: std(0x222428, { roughness: 0.9 }),
    floor: std(0x2c4f9e, { roughness: 0.9 }),
    floorNEF: std(0x7b8ea3, { roughness: 0.9 }),
    lining: std(0xe3e6ea, { roughness: 0.7, side: THREE.DoubleSide }),
    tire: std(0x141414, { roughness: 0.95 }),
    rim: std(0x9da3aa, { metalness: 0.55, roughness: 0.35 }),
    red: std(0xd8101e), blue: std(0x1d3f9e), yellow: std(0xf2c500), orange: std(0xf06a10), green: std(0x2a8a3a),
    neon: std(0xd4ef12), mattress: std(0x1e1f22, { roughness: 0.9 }), sheet: std(0xe9eef2, { roughness: 0.9 }),
    plate: std(0xf5f5f0),
    airbag: std(0xf2f2ee, { roughness: 0.95 }),
    // Lichter
    head: lens(0xe8eef5, 0xffffff), blueA: lens(0x0f2c8a, 0x2a5cff), blueB: lens(0x0f2c8a, 0x2a5cff),
    rearRed: lens(0x7a0a0a, 0xff1010), brake3: lens(0x5a0808, 0xff1010), ind: lens(0xb86a00, 0xff8a00),
    ceiling: lens(0xf2f2f2, 0xffffff), screen: lens(0x101418, 0x6fd3ff), work: lens(0xdfe3e8, 0xffffff),
  };
}

// ---------------------------------------------------------------- Bausteine
export function box(w, h, d, mat, x = 0, y = 0, z = 0, rx = 0, ry = 0, rz = 0) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat);
  m.position.set(x, y, z); m.rotation.set(rx, ry, rz);
  return m;
}
export function cyl(r, h, seg, mat, x = 0, y = 0, z = 0, axis = 'y') {
  const m = new THREE.Mesh(new THREE.CylinderGeometry(r, r, h, seg), mat);
  m.position.set(x, y, z);
  if (axis === 'z') m.rotation.x = Math.PI / 2; if (axis === 'x') m.rotation.z = Math.PI / 2;
  return m;
}
export function group(...children) { const g = new THREE.Group(); children.forEach(c => c && g.add(c)); return g; }

// Rad: Reifen (Lathe-Profil), Felge mit Speichen, Nabe
export function wheel(M, r, w, opts = {}) {
  const seg = opts.seg || 18;
  const g = new THREE.Group();
  const prof = [[r * 0.66, -w / 2], [r * 0.93, -w / 2], [r, -w * 0.32], [r, w * 0.32], [r * 0.93, w / 2], [r * 0.66, w / 2]].map(p => new THREE.Vector2(p[0], p[1]));
  const tire = new THREE.Mesh(new THREE.LatheGeometry(prof, seg), M.tire);
  tire.rotation.x = Math.PI / 2; g.add(tire);
  const rim = new THREE.Mesh(new THREE.CylinderGeometry(r * 0.66, r * 0.66, w * 0.7, seg, 1, true), M.rim); rim.rotation.x = Math.PI / 2; g.add(rim);
  const disc = new THREE.Mesh(new THREE.CircleGeometry(r * 0.66, seg), M.rim); disc.position.z = w * 0.30; g.add(disc);
  const hub = new THREE.Mesh(new THREE.CylinderGeometry(r * 0.17, r * 0.2, 0.05, 8), M.steel); hub.rotation.x = Math.PI / 2; hub.position.z = w * 0.33; g.add(hub);
  const spokes = opts.spokes ?? 6;
  for (let i = 0; i < spokes; i++) {
    const s = box(r * 0.42, 0.045, 0.03, M.steel, 0, 0, w * 0.32); const a = i / spokes * Math.PI * 2;
    s.position.x = Math.cos(a) * r * 0.40; s.position.y = Math.sin(a) * r * 0.40; s.rotation.z = a; g.add(s);
  }
  return g;
}

// sitzende / liegende Person (Low-Poly)
export function person(M, pose = 'sit', colors = {}) {
  const top = colors.top || M.red, legs = colors.legs || M.darkgrey, skin = colors.skin || new THREE.MeshStandardMaterial({ color: 0xd9a27e, flatShading: true, roughness: 0.8 });
  const hair = colors.hair || M.black;
  const g = new THREE.Group();
  const torso = box(0.24, 0.52, 0.40, top, 0, 0.36, 0); g.add(torso);
  if (colors.stripe) g.add(box(0.245, 0.05, 0.405, colors.stripe, 0, 0.30, 0));
  const head = new THREE.Mesh(new THREE.IcosahedronGeometry(0.115, 1), skin); head.position.set(0.01, 0.76, 0); head.scale.set(1, 1.12, 0.92); g.add(head);
  const hairM = new THREE.Mesh(new THREE.IcosahedronGeometry(0.118, 0), hair); hairM.position.set(-0.02, 0.81, 0); hairM.scale.set(1, 0.75, 0.95); g.add(hairM);
  g.add(box(0.07, 0.08, 0.08, skin, 0.01, 0.65, 0)); // Hals
  for (const s of [-1, 1]) {
    const ua = box(0.10, 0.30, 0.10, top, 0.02, 0.43, s * 0.25, 0, 0, pose === 'sit' ? 0.35 : 0); g.add(ua);
    const fa = box(0.28, 0.09, 0.09, top, 0.17, 0.27, s * 0.25); if (pose !== 'sit') { fa.rotation.z = -1.4; fa.position.set(0.02, 0.15, s * 0.25); } g.add(fa);
    g.add(box(0.08, 0.06, 0.07, skin, pose === 'sit' ? 0.34 : 0.02, pose === 'sit' ? 0.27 : 0.03, s * 0.25));
    if (pose === 'sit') {
      g.add(box(0.44, 0.15, 0.15, legs, 0.20, 0.04, s * 0.10));
      g.add(box(0.13, 0.44, 0.13, legs, 0.40, -0.18, s * 0.10));
      g.add(box(0.22, 0.07, 0.10, M.black, 0.45, -0.41, s * 0.10));
    } else {
      g.add(box(0.15, 0.44, 0.15, legs, 0, -0.14, s * 0.10));
      g.add(box(0.13, 0.44, 0.13, legs, 0, -0.58, s * 0.10));
      g.add(box(0.22, 0.07, 0.10, M.black, 0.06, -0.82, s * 0.10));
    }
  }
  g.add(box(0.26, 0.16, 0.38, legs, 0, 0.06, 0));
  if (pose === 'lie') { const w = new THREE.Group(); g.rotation.z = Math.PI / 2; w.add(g); w.rotation.y = Math.PI; return w; }
  return g;
}

export function countTris(obj, filter = () => true) {
  let n = 0;
  obj.traverse(o => {
    if (o.isMesh && filter(o)) { const g = o.geometry; n += (g.index ? g.index.count : g.attributes.position.count) / 3; }
  });
  return Math.round(n);
}

// ---------------------------------------------------------------- Fahrzeug-Basis
export class Vehicle {
  constructor(name) {
    this.name = name; this.root = new THREE.Group(); this.root.name = name;
    this.doors = {}; this.items = {}; this.M = null;
    this.lights = { blue: false, low: false, hazard: false, brake: false, interior: false };
    this.realLights = { head: [], blue: [], brake: [], ind: [] };
    this.airbags = new THREE.Group(); this.airbags.visible = false; this.airbags.userData.extra = true;
    this.root.add(this.airbags);
    this.wrecked = false; this.static = true;
    this.interior = new THREE.Group(); this.interior.name = 'innenraum'; this.interior.userData.interior = true; this.root.add(this.interior);
  }
  pivot(name, pivot, children, cfg) {
    const g = new THREE.Group(); g.name = name; g.position.copy(pivot);
    children.flat().filter(Boolean).forEach(c => { c.position.sub(pivot); g.add(c); });
    this.root.add(g);
    this.doors[name] = { group: g, base: pivot.clone(), t: 0, removed: false, ...cfg };
    return g;
  }
  setDoor(name, t) {
    const d = this.doors[name]; if (!d) return; d.t = t;
    if (d.type === 'hinge') d.group.rotation[d.axis] = d.angle * t;
    else if (d.type === 'slide') {
      const t1 = Math.min(1, t / 0.18), t2 = Math.max(0, (t - 0.18) / 0.82);
      d.group.position.copy(d.base).addScaledVector(d.out, t1).addScaledVector(d.travel, t2);
    } else if (d.type === 'drawer') d.group.position.copy(d.base).addScaledVector(d.travel, t);
  }
  setInterior(on) { this.interior.visible = on; Object.values(this.doors).forEach(d => { if (d.group.userData.interior) d.group.visible = on && !d.removed; }); }
  removeDoor(name, removed) { const d = this.doors[name]; d.removed = removed; d.group.visible = !removed; }
  addItem(name, obj, inPose, outPose) {
    obj.userData.extra = true; obj.name = name;
    this.root.add(obj);
    this.items[name] = { obj, inPose, outPose, state: 'in' };
    this.setItem(name, 'in');
  }
  setItem(name, state) {
    const it = this.items[name]; if (!it) return; it.state = state;
    it.obj.visible = state !== 'hidden';
    const p = state === 'out' && it.outPose ? it.outPose : it.inPose;
    it.obj.position.copy(p.pos); it.obj.rotation.set(...(p.rot || [0, 0, 0]));
    if (p.apply) p.apply(it.obj);
  }
  setLights(l) { Object.assign(this.lights, l); this.update(0); }
  update(time) {
    const M = this.M, L = this.lights;
    let a, b, ind;
    if (this.static) { a = b = ind = true; }
    else {
      const ph = (time * 1.6) % 1;
      a = (ph < 0.07) || (ph > 0.13 && ph < 0.2); const ph2 = (ph + 0.5) % 1; b = (ph2 < 0.07) || (ph2 > 0.13 && ph2 < 0.2);
      ind = (time * 1.5) % 1 < 0.5;
    }
    const broken = this.wrecked;
    M.blueA.emissiveIntensity = L.blue && a ? 3.2 : 0; M.blueB.emissiveIntensity = L.blue && b ? 3.2 : 0;
    M.head.emissiveIntensity = L.low && !broken ? 2.2 : 0.0;
    M.rearRed.emissiveIntensity = L.brake ? 3 : (L.low ? 0.9 : 0);
    M.brake3.emissiveIntensity = L.brake ? 3 : 0;
    M.ind.emissiveIntensity = L.hazard && ind ? 3 : 0;
    M.ceiling.emissiveIntensity = L.interior ? 2.5 : 0;
    M.screen.emissiveIntensity = L.interior ? 1.5 : 0.2;
    this.realLights.head.forEach(l => l.visible = L.low && !broken);
    this.realLights.blue.forEach((l, i) => { l.visible = L.blue; l.intensity = (i % 2 ? b : a) ? l.userData.i * 0.45 : 0; });
    this.realLights.brake.forEach(l => { l.visible = L.brake || L.low; l.intensity = L.brake ? l.userData.i : l.userData.i * 0.2; });
    this.realLights.ind.forEach(l => { l.visible = L.hazard; l.intensity = ind ? l.userData.i : 0; });
  }
  // Wrack: Front eindrücken, Airbags auslösen
  setWreck(on, params) {
    if (on === this.wrecked) return;
    this.wrecked = on;
    const saved = {}; Object.entries(this.doors).forEach(([k, d]) => { saved[k] = d.t; this.setDoor(k, 0); });
    this.root.updateMatrixWorld(true);
    const inv = new THREE.Matrix4().copy(this.root.matrixWorld).invert();
    const v = new THREE.Vector3();
    this.root.traverse(o => {
      if (!o.isMesh || o.userData.extra || o.userData.noDeform) return;
      let p = o.userData.basePos;
      if (on) {
        if (!o.userData.ownGeo) { o.geometry = o.geometry.clone(); o.userData.ownGeo = true; }
        const pos = o.geometry.attributes.position;
        if (!p) { p = o.userData.basePos = pos.array.slice(); }
        const Mw = new THREE.Matrix4().multiplyMatrices(inv, o.matrixWorld), Mi = Mw.clone().invert();
        let any = false;
        for (let i = 0; i < pos.count; i++) {
          v.set(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).applyMatrix4(Mw);
          if (params.deform(v)) any = true;
          v.applyMatrix4(Mi); pos.setXYZ(i, v.x, v.y, v.z);
        }
        pos.needsUpdate = true; o.geometry.computeVertexNormals();
        if (o.material === this.M.glass && o.userData.windshield) o.material = this.M.glassBroken;
      } else if (p) {
        o.geometry.attributes.position.array.set(p); o.geometry.attributes.position.needsUpdate = true; o.geometry.computeVertexNormals();
        if (o.userData.windshield) o.material = this.M.glass;
      }
    });
    if (params.extra) params.extra(on);
    this.airbags.visible = on;
    Object.entries(saved).forEach(([k, t]) => this.setDoor(k, t));
    this.update(0);
  }
}

export const hash3 = (x, y, z) => { const s = Math.sin(Math.round(x * 500) * 12.9898 + Math.round(y * 500) * 78.233 + Math.round(z * 500) * 37.719) * 43758.5453; return s - Math.floor(s); };
export const smooth = (e0, e1, x) => { const t = clamp01((x - e0) / (e1 - e0)); return t * t * (3 - 2 * t); };

export function addMesh(parent, geo, mat, flags = {}) {
  const m = new THREE.Mesh(geo, mat); Object.assign(m.userData, flags); parent.add(m); return m;
}
export function mesh(geo, mat, flags = {}) { const m = new THREE.Mesh(geo, mat); Object.assign(m.userData, flags); return m; }

// ---------------------------------------------------------------- Innenausstattung
export function seat(M, mat, opts = {}) {
  const g = new THREE.Group();
  g.add(box(0.30, 0.16, 0.38, M.darkgrey, 0.02, -0.14, 0));            // Konsole
  g.add(box(0.50, 0.12, 0.50, mat, 0.02, 0, 0));                          // Sitzfläche
  g.add(box(0.46, 0.06, 0.08, mat, 0.02, 0.06, 0.22)); g.add(box(0.46, 0.06, 0.08, mat, 0.02, 0.06, -0.22));
  g.add(box(0.12, 0.66, 0.50, mat, -0.25, 0.36, 0, 0, 0, 0.14));          // Lehne
  g.add(box(0.12, 0.50, 0.07, mat, -0.21, 0.36, 0.23, 0, 0, 0.14)); g.add(box(0.12, 0.50, 0.07, mat, -0.21, 0.36, -0.23, 0, 0, 0.14));
  g.add(box(0.10, 0.18, 0.28, mat, -0.33, 0.80, 0, 0, 0, 0.14));          // Kopfstütze
  if (opts.belt) g.add(box(0.02, 0.6, 0.05, M.black, -0.15, 0.4, 0.15, 0.5, 0, 0));
  return g;
}
export function bag(M, mat, w, h, d, stripe = true) {
  const g = new THREE.Group();
  g.add(box(w, h, d, mat, 0, h / 2, 0));
  g.add(box(w * 0.18, h * 0.8, d * 0.86, mat, w / 2 + w * 0.06, h * 0.45, 0));      // Fronttasche
  g.add(box(w * 0.6, 0.03, 0.04, M.black, 0, h + 0.015, 0));                      // Griff
  if (stripe) { g.add(box(w + 0.006, 0.04, d + 0.006, M.neon, 0, h * 0.62, 0)); g.add(box(w * 0.28, h * 0.84, 0.04, M.neon, w / 2 + w * 0.10, h * 0.45, 0)); }
  return g;
}
export function cabinetBlock(M, w, h, d, cols, rows, x, y, z, faceDir = 1, axis = 'x') {
  // weißer Schrank mit blauer Kante und Fronten; faceDir +1/-1 entlang axis
  const g = new THREE.Group();
  g.add(box(w, h, d, M.cabinet, 0, 0, 0));
  const fw = axis === 'x' ? d : w;
  for (let c = 0; c < cols; c++) for (let r = 0; r < rows; r++) {
    const fx = -fw / 2 + fw * (c + 0.5) / cols, fy = -h / 2 + h * (r + 0.5) / rows;
    const fr = axis === 'x' ? box(0.012, h / rows - 0.03, fw / cols - 0.03, M.white, faceDir * (w / 2 + 0.006), fy, fx)
      : box(fw / cols - 0.03, h / rows - 0.03, 0.012, M.white, fx, fy, faceDir * (d / 2 + 0.006));
    g.add(fr);
    const kn = axis === 'x' ? box(0.02, 0.04, 0.04, M.trimBlue, faceDir * (w / 2 + 0.014), fy + h / rows * 0.3, fx)
      : box(0.04, 0.04, 0.02, M.trimBlue, fx, fy + h / rows * 0.3, faceDir * (d / 2 + 0.014));
    g.add(kn);
  }
  g.add(axis === 'x' ? box(0.02, 0.025, d + 0.01, M.trimBlue, faceDir * (w / 2 + 0.01), h / 2, 0) : box(w + 0.01, 0.025, 0.02, M.trimBlue, 0, h / 2, faceDir * (d / 2 + 0.01)));
  g.position.set(x, y, z);
  return g;
}
