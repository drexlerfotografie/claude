import * as THREE from 'three';
import { refine, edgeSet, skin, shrink, wallXY, wallZY, plateXY, plateZY, liveryUV, LiveryPainter, makeMaterials, box, cyl, wheel, person, Vehicle, mesh, grow, hash3, smooth, seat, bag, cabinetBlock } from './core.js';

const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

export function buildRTW() {
  const S = { xmin: -3.62, L: 7.22, W: 2.22, H: 3.05 };
  const WC = 2.0, HC = WC / 2;          // Fahrerhaus
  const WK = 2.22, HK = WK / 2;         // Koffer
  const v = new Vehicle('RTW');

  // ---------- Beklebung
  const P = new LiveryPainter(S);
  const RED = '#e1061c', YEL = '#e8f20c';
  // Fahrerhaus: roter Streifen unten mit gelber Linie
  P.sideRect(1.38, 3.6, 0.42, 0.98, RED); P.sideRect(1.38, 3.6, 0.98, 1.06, YEL);
  // Koffer: Rot unten, ansteigend nach hinten, gelbe Welle als Grenze
  const wave = (x) => 1.06 + 0.95 * (1 - (Math.cos(Math.PI * Math.min(1, Math.max(0, (1.4 - x) / 4.8))) + 1) / 2);
  const xs = []; for (let i = 0; i <= 40; i++) xs.push(1.40 - i * 4.82 / 40);
  P.sidePoly([[1.40, 0.6], ...xs.map(x => [x, wave(x) + 0.17]), [-3.42, 0.6]], YEL);
  P.sidePoly([[1.40, 0.6], ...xs.map(x => [x, wave(x)]), [-3.42, 0.6]], RED);
  P.sideRect(-3.42, 1.40, 0.62, 0.70, '#b8bcc2');
  P.sideText('RETTUNGSDIENST', -0.55, 2.42, 0.20, RED);
  P.sideText('NOTRUF 112', -2.55, 1.35, 0.22, '#ffffff');
  P.endRect('front', -HK, HK, 0.0, 0.62, '#2d3034');
  P.endRect('front', -HK, HK, 0.62, 0.98, RED); P.endRect('front', -HK, HK, 0.98, 1.06, YEL);
  P.endRect('front', -0.62, 0.62, 0.66, 0.98, '#24272b');
  P.endRect('rear', -HK, HK, 0.0, 0.70, '#3a3d42');
  P.endRect('rear', -HK, HK, 0.70, 1.95, RED); P.endRect('rear', -HK, HK, 1.95, 2.12, YEL);
  const M = makeMaterials(P.texture()); v.M = M;
  const R = v.root;
  const add = (o) => { R.add(o); return o; };
  const addI = (o) => { v.interior.add(o); return o; };
  const uv = (g, o) => liveryUV(g, S, o);

  // ---------- Fahrerhaus (Sprinter)
  const cp0 = [[3.38, 0.36], [3.50, 0.46], [3.53, 0.62], [3.51, 0.80], [3.46, 0.98], [3.40, 1.10], [3.20, 1.20], [2.92, 1.27], [2.64, 1.32],
    [2.30, 1.72], [1.98, 2.10], [1.84, 2.20], [1.60, 2.24], [1.40, 2.25]];
  const CR = 2; const cp = refine(cp0, CR, new Set([8]));
  const co = { W: WC, r: 0.13, k: 5, m: 6, crown: 0.03 };
  const csk = (i0, i1, e = {}) => skin(cp, i0 * CR, i1 * CR, { ...co, ...e });
  add(mesh(uv(csk(0, 5).body), M.body));
  const hood = csk(5, 8);
  const ws = csk(8, 10, { glass: edgeSet([8, 9], CR) }); add(mesh(uv(ws.body), M.body)); add(mesh(ws.glass, M.glass, { windshield: true }));
  add(mesh(uv(csk(10, 13).body), M.body));
  const XF = 2.55, XR = -1.11;
  const archF = []; for (let i = 0; i <= 10; i++) { const t = Math.PI - i / 10 * Math.PI; archF.push([XF + 0.45 * Math.cos(t), 0.37 + 0.45 * Math.sin(t)]); }
  const csp = shrink(cp, co.r);
  const cabOutline = [...csp, [1.40, 0.42], ...archF, [csp[0][0], 0.42]];
  const doorC = [[1.50, 0.45], [2.05, 0.45], [2.12, 0.76], [2.40, 0.98], [2.44, 1.12], [1.88, 1.90], [1.50, 1.93]];
  const winC = [[1.56, 1.28], [2.25, 1.28], [1.86, 1.84], [1.56, 1.86]];
  for (const s of [1, -1]) {
    add(mesh(uv(wallXY(cabOutline, [grow(doorC, 0.012)], s * HC, s)), M.body));
    const parts = [mesh(uv(plateXY(doorC, [winC], s * HC, s), { innerZ: s }), M.body), mesh(wallXY(winC, [], s * (HC - 0.02), s), M.glass),
      box(0.14, 0.035, 0.03, M.black, 1.66, 1.18, s * (HC + 0.012)), box(0.55, 0.55, 0.02, M.darkgrey, 1.85, 0.85, s * (HC - 0.06))];
    v.pivot(s > 0 ? 'tuer_beifahrer' : 'tuer_fahrer', V3(2.42, 0, s * HC), parts, { type: 'hinge', axis: 'y', angle: s * 1.2 });
  }
  v.pivot('motorhaube', V3(2.64, 1.32, 0), [mesh(uv(hood.body), M.body)], { type: 'hinge', axis: 'z', angle: 0.85 });

  // ---------- Koffer (Strobel)
  const kp = [[1.38, 2.70], [1.36, 2.83], [1.30, 2.88], [-3.32, 2.88], [-3.38, 2.83], [-3.40, 2.70]];
  const ko = { W: WK, r: 0.07, k: 3, m: 6, crown: 0.02 };
  add(mesh(uv(skin(kp, 0, 5, ko).body), M.body));
  const ksp = shrink(kp, ko.r);
  const a0 = Math.asin(0.29 / 0.47), archR = [];
  for (let i = 0; i <= 8; i++) { const t = Math.PI - a0 - i / 8 * (Math.PI - 2 * a0); archR.push([XR + 0.47 * Math.cos(t), 0.37 + 0.47 * Math.sin(t)]); }
  const kOutline = [[1.38, 0.66], [1.38, 2.70], ...ksp, [-3.40, 2.70], [-3.40, 0.66], ...archR];
  const doorSl = [[1.26, 0.74], [1.26, 2.50], [0.20, 2.50], [0.20, 0.74]];
  const winSl = [[1.16, 1.66], [1.16, 2.32], [0.30, 2.32], [0.30, 1.66]];
  const doorFach = [[-1.70, 0.74], [-1.70, 2.40], [-2.74, 2.40], [-2.74, 0.74]];
  const winL = [[0.95, 1.62], [0.95, 2.30], [-0.35, 2.30], [-0.35, 1.62]];
  // rechts
  add(mesh(uv(wallXY(kOutline, [grow(doorSl, 0.012), grow(doorFach, 0.012)], HK, 1)), M.body));
  v.pivot('schiebetuer', V3(0, 0, 0), [mesh(uv(plateXY(doorSl, [winSl], HK, 1), { innerZ: 1 }), M.body), mesh(wallXY(winSl, [], HK - 0.02, 1), M.glass),
    box(0.04, 0.22, 0.03, M.black, 1.18, 1.45, HK + 0.012), box(1.15, 0.03, 0.03, M.darkgrey, 0.73, 2.47, HK + 0.01)],
    { type: 'slide', out: V3(0, 0, 0.075), travel: V3(-1.02, 0, 0) });
  add(box(1.6, 0.035, 0.035, M.darkgrey, -0.30, 2.53, HK + 0.01)); add(box(1.4, 0.035, 0.035, M.darkgrey, -0.20, 0.80, HK + 0.01));
  // links
  add(mesh(uv(wallXY(kOutline, [grow(doorFach, 0.012), winL], -HK, -1)), M.body));
  add(mesh(wallXY(winL, [], -(HK - 0.012), -1), M.glass));
  // Außenfächer hinten links / rechts
  for (const s of [1, -1]) {
    const parts = [mesh(uv(plateXY(doorFach, [], s * HK, s, 0.04), { innerZ: s }), M.body),
      box(0.04, 0.22, 0.03, M.black, -2.66, 1.55, s * (HK + 0.012))];
    if (s > 0) { // Schaufeltrage an der Türinnenseite
      const st = new THREE.Group();
      for (const dx of [0.2, -0.2]) st.add(box(0.035, 1.5, 0.035, M.alu, -2.22 + dx, 1.58, HK - 0.075));
      for (const y of [1.0, 1.35, 1.8, 2.15]) st.add(box(0.36, 0.28, 0.02, M.alu, -2.22, y, HK - 0.065));
      st.add(box(0.30, 0.20, 0.06, M.red, -2.22, 1.55, HK - 0.10));
      st.userData.extra = true; parts.push(st);
    }
    v.pivot(s > 0 ? 'fach_hinten_rechts' : 'fach_hinten_links', V3(-1.70, 0, s * HK), parts, { type: 'hinge', axis: 'y', angle: s * 1.75 });
  }
  // Front- und Rückwand
  add(mesh(uv(wallZY([[-HK, 0.66], [HK, 0.66], [HK, 2.70], [-HK, 2.70]], [[[-0.38, 1.55], [0.38, 1.55], [0.38, 1.95], [-0.38, 1.95]]], 1.38, 1)), M.body));
  add(mesh(wallZY([[-0.38, 1.55], [0.38, 1.55], [0.38, 1.95], [-0.38, 1.95]], [], 1.37, 1), M.glass));
  const rearHole = [[-0.995, 0.74], [0.995, 0.74], [0.995, 2.58], [-0.995, 2.58]];
  add(mesh(uv(wallZY([[-HK, 0.66], [HK, 0.66], [HK, 2.70], [-HK, 2.70]], [rearHole], -3.40, -1)), M.body));
  // Hecktüren (Flügeltüren)
  for (const s of [1, -1]) {
    const dp = [[0.006, 0.745], [0.985, 0.745], [0.985, 2.575], [0.006, 2.575]].map(p => [p[0] * s, p[1]]);
    const wp = [[0.22, 1.72], [0.80, 1.72], [0.80, 2.40], [0.22, 2.40]].map(p => [p[0] * s, p[1]]);
    const order = (a) => s > 0 ? a : a.slice().reverse();
    const parts = [mesh(uv(plateZY(order(dp), [order(wp)], -3.40, -1), { innerX: -1 }), M.body), mesh(wallZY(order(wp), [], -3.38, -1), M.frosted),
      box(0.012, 0.80, 0.92, M.alu, -3.352, 1.20, s * 0.50),                     // Riffelblech innen
      box(0.03, 0.30, 0.04, M.steel, -3.33, 1.55, s * 0.12),                     // Haltegriff innen
      box(0.03, 0.05, 0.16, M.black, -3.415, 1.50, s * 0.12)];                   // Griff außen
    v.pivot(s > 0 ? 'hecktuer_rechts' : 'hecktuer_links', V3(-3.40, 0, s * 0.99), parts, { type: 'hinge', axis: 'y', angle: s * 1.9 });
  }

  // ---------- Fahrgestell, Räder
  add(box(6.4, 0.16, 0.12, M.black, -0.4, 0.55, 0.45)); add(box(6.4, 0.16, 0.12, M.black, -0.4, 0.55, -0.45));
  add(box(4.80, 0.04, WK - 0.02, M.darkgrey, -1.01, 0.66, 0));
  add(box(1.6, 0.03, WC - 0.04, M.black, 2.5, 0.42, 0));
  add(box(0.25, 0.05, 2.0, M.alu, -3.53, 0.56, 0)); add(box(0.04, 0.12, 2.0, M.black, -3.45, 0.62, 0));
  for (const s of [1, -1]) {
    const g = new THREE.CylinderGeometry(0.45, 0.45, 0.30, 10, 1, true, Math.PI / 2, Math.PI); g.rotateX(Math.PI / 2);
    add(mesh(g, M.black)).position.set(XF, 0.37, s * (HC - 0.17));
    const wf = wheel(M, 0.36, 0.22, { seg: 18 }); wf.position.set(XF, 0.36, s * 0.84); if (s < 0) wf.rotation.y = Math.PI; wf.name = s > 0 ? 'rad_vr' : 'rad_vl'; add(wf);
    for (const z of [0.86, 0.62]) { const wr = wheel(M, 0.36, 0.21, { seg: 18, spokes: z > 0.7 ? 6 : 0 }); wr.position.set(XR, 0.36, s * z); if (s < 0) wr.rotation.y = Math.PI; add(wr); }
    add(box(0.9, 0.03, 0.03, M.black, XR, 0.86, s * (HK + 0.01)));               // Radlauf-Leiste
  }

  // ---------- Leuchten & Anbauteile
  for (const y of [0.74, 0.80, 0.86, 0.92]) add(box(0.02, 0.014, 1.15, M.black, 3.505, y, 0));
  add(box(0.03, 0.10, 0.52, M.plate, 3.54, 0.55, 0));
  for (const s of [1, -1]) {
    add(box(0.12, 0.14, 0.40, M.head, 3.42, 1.00, s * 0.70, 0, -s * 0.35, 0));
    add(box(0.10, 0.035, 0.12, M.ind, 3.36, 1.04, s * 0.90, 0, -s * 0.35, 0));
    add(box(0.03, 0.06, 0.14, s > 0 ? M.blueA : M.blueB, 3.515, 0.86, s * 0.32));
    add(box(0.03, 0.05, 0.12, s > 0 ? M.blueB : M.blueA, 3.535, 0.54, s * 0.70));
    add(box(0.06, 0.03, 0.01, M.ind, 2.95, 0.95, s * (HC + 0.004)));
    // Spiegel
    add(box(0.05, 0.05, 0.22, M.black, 2.30, 1.55, s * (HC + 0.10)));
    add(box(0.10, 0.38, 0.20, M.black, 2.28, 1.70, s * (HC + 0.24)));
    add(box(0.02, 0.03, 0.14, M.ind, 2.33, 1.56, s * (HC + 0.22)));
    // Blaulicht-Kennleuchten Kofferecken
    add(cyl(0.045, 0.04, 8, M.black, 1.22, 2.90, s * 0.96));
    add(cyl(0.095, 0.13, 10, s > 0 ? M.blueA : M.blueB, 1.22, 2.98, s * 0.96));
    add(cyl(0.095, 0.13, 10, s > 0 ? M.blueB : M.blueA, -3.24, 2.98, s * 0.96));
    add(box(0.03, 0.10, 0.30, s > 0 ? M.blueB : M.blueA, 1.395, 2.56, s * 0.55));      // Frontblitzer Koffer
    add(box(0.30, 0.08, 0.02, s > 0 ? M.blueA : M.blueB, -2.95, 2.66, s * (HK + 0.006))); // Seitenblitzer
    add(box(0.18, 0.08, 0.02, M.work, 0.6, 2.66, s * (HK + 0.006)));                    // Umfeldleuchte
    for (const x of [0.9, -0.4, -2.0, -3.2]) add(box(0.05, 0.03, 0.01, M.orange, x, 0.73, s * (HK + 0.005)));
    // Heck
    add(box(0.035, 0.34, 0.10, M.rearRed, -3.415, 1.10, s * 1.05));
    add(box(0.035, 0.12, 0.10, M.ind, -3.415, 0.86, s * 1.05));
    add(box(0.035, 0.09, 0.10, M.head, -3.415, 1.33, s * 1.05));
    add(box(0.035, 0.08, 0.18, M.work, -3.415, 2.64, s * 0.45));
    add(box(0.035, 0.10, 0.16, M.rearRed, -3.415, 2.64, s * 0.80));
  }
  add(box(0.035, 0.05, 0.36, M.brake3, -3.415, 2.64, 0));
  add(box(0.10, 0.03, 0.30, M.darkgrey, -1.0, 2.90, 0.4)); add(cyl(0.007, 0.4, 4, M.black, -1.0, 3.1, 0.4));
  add(box(0.6, 0.08, 0.6, M.darkgrey, -0.6, 2.92, -0.3));                                   // Dachlüfter / Klima
  add(box(0.03, 0.015, 0.7, M.black, 2.60, 1.36, -0.30, 0, 0.25, 0)); add(box(0.03, 0.015, 0.7, M.black, 2.60, 1.36, 0.42, 0, 0.25, 0));

  // ---------- Innenraum Fahrerhaus
  addI(box(1.3, 0.04, WC - 0.08, M.black, 2.05, 0.58, 0));
  addI(box(0.45, 0.34, WC - 0.06, M.darkgrey, 2.52, 1.10, 0));
  addI(box(0.38, 0.05, WC - 0.06, M.black, 2.42, 1.29, 0, 0, 0, -0.25));
  addI(box(0.04, 0.16, 0.26, M.screen, 2.31, 1.30, 0.0, 0, 0, -0.3));
  const sw = new THREE.Mesh(new THREE.TorusGeometry(0.20, 0.022, 4, 14), M.black); sw.position.set(2.18, 1.28, -0.48); sw.rotation.set(0, Math.PI / 2, 0); sw.rotateX(0.55); addI(sw);
  addI(cyl(0.03, 0.3, 6, M.black, 2.30, 1.22, -0.48, 'x')).rotation.z = Math.PI / 2 + 0.5;
  for (const s of [1, -1]) { const st = seat(M, M.seatBlack); st.position.set(1.82, 0.97, s * 0.48); addI(st); }

  // ---------- Innenraum Koffer
  addI(box(4.74, 0.06, WK - 0.04, M.floor, -1.01, 0.85, 0));
  for (const z of [0.42, -0.42]) addI(box(3.6, 0.025, 0.10, M.ceiling, -1.2, 2.83, z));
  for (const z of [0.36, -0.36]) addI(cyl(0.018, 3.2, 6, M.steel, -1.4, 2.62, z, 'x'));
  // links: Ober- und Unterschränke
  addI(cabinetBlock(M, 2.75, 0.58, 0.34, 6, 1, -0.17, 2.50, -(HK - 0.19), 1, 'z'));
  addI(cabinetBlock(M, 1.45, 0.56, 0.46, 3, 2, 0.55, 1.16, -(HK - 0.25), 1, 'z'));
  addI(box(1.46, 0.03, 0.48, M.steel, 0.55, 1.455, -(HK - 0.25)));
  const mon = new THREE.Group(); mon.add(box(0.26, 0.24, 0.10, M.darkgrey, 0, 0, 0)); mon.add(box(0.20, 0.15, 0.01, M.screen, 0, 0.02, 0.055)); mon.position.set(-1.05, 1.85, -(HK - 0.08)); addI(mon);
  addI(box(0.6, 0.35, 0.05, M.cabinet, -1.05, 1.62, -(HK - 0.03)));
  // rechts: Oberschrank, Rucksackfach neben der Schiebetür
  addI(cabinetBlock(M, 1.85, 0.58, 0.34, 5, 1, -0.73, 2.50, HK - 0.19, -1, 'z'));
  addI(box(0.03, 0.60, 0.84, M.cabinet, -0.52, 1.18, 0.67));        // Rückwand Fach
  addI(box(0.68, 0.60, 0.03, M.cabinet, -0.18, 1.18, 0.26));        // Seitenwand
  addI(box(0.70, 0.04, 0.86, M.steel, -0.18, 1.50, 0.67));          // Arbeitsplatte
  addI(box(0.70, 0.04, 0.86, M.trimBlue, -0.18, 1.475, 0.67).translateY(-0.0));
  addI(box(0.02, 0.02, 0.84, M.trimBlue, 0.16, 0.90, 0.67));
  // Tragetisch + Sitze
  addI(box(2.10, 0.10, 0.62, M.alu, -1.95, 0.93, -0.05)); addI(box(2.10, 0.03, 0.10, M.yellow, -1.95, 0.99, -0.05));
  const bs = seat(M, M.seatBlue); bs.position.set(-0.55, 1.33, -0.05); bs.rotation.y = Math.PI; addI(bs);
  const rs = seat(M, M.seatBlue); rs.position.set(-1.55, 1.33, 0.66); rs.rotation.y = Math.PI / 2; addI(rs);
  // Innenwände der Außenfächer
  for (const s of [1, -1]) {
    addI(box(1.06, 1.95, 0.03, M.cabinet, -2.22, 1.86, s * 0.62));
    addI(box(0.03, 1.95, HK - 0.62, M.cabinet, -1.69, 1.86, s * (0.62 + HK) / 2));
    addI(box(0.03, 1.95, HK - 0.62, M.cabinet, -2.75, 1.86, s * (0.62 + HK) / 2));
    addI(box(1.06, 0.03, HK - 0.62, M.cabinet, -2.22, 2.42, s * (0.62 + HK) / 2));
    addI(box(0.12, 0.025, 0.08, M.ceiling, -2.22, 2.39, s * 0.86));
  }

  // ---------- Detailstufe
  // Motorraum
  add(box(0.45, 0.32, 0.70, M.darkgrey, 3.05, 0.92, 0)); add(box(0.40, 0.06, 0.55, M.black, 3.05, 0.92+0.19, 0));
  add(box(0.20, 0.18, 0.28, M.black, 3.05+0.05, 0.92+0.02, 0.55)); add(cyl(0.07, 0.14, 6, M.white, 3.05-0.05, 0.92+0.08, -0.55));
  for (let i = 0; i < 4; i++) addI(box(0.02, 0.05, 0.10, M.black, 2.31, 1.20, -0.78 + i * 0.52));
  const bed = new THREE.Group(); bed.add(box(0.12, 0.10, 0.26, M.black, 0, 0, 0));
  for (let i = 0; i < 8; i++) bed.add(box(0.012, 0.025, 0.03, i < 2 ? M.blue : M.white, 0.065, 0.02 - (i % 2) * 0.04, -0.09 + Math.floor(i / 2) * 0.06));
  bed.position.set(2.30, 1.00, 0.02); addI(bed);
  addI(box(0.12, 0.07, 0.22, M.black, 2.30, 0.86, 0.02)); addI(box(0.006, 0.03, 0.10, M.screen, 2.362, 0.87, 0.02));
  addI(cyl(0.02, 0.18, 6, M.black, 2.20, 1.00, 0.10)); addI(box(0.28, 0.34, 0.24, M.darkgrey, 2.05, 0.75, 0.0));
  for (const s of [1, -1]) {
    add(cyl(0.07, 0.12, 8, M.black, 2.20, 2.30, s * 0.55, 'x'));                                    // Martinshorn-Lautsprecher
    add(box(0.004, 0.32, 0.17, M.steel, 2.225, 1.70, s * (HC + 0.24)));                             // Spiegelglas
    addI(box(0.60, 0.04, 0.10, M.darkgrey, 1.85, 1.08, s * (HC - 0.08)));                            // Armlehne
    add(box(4.70, 0.03, 0.025, M.alu, -1.01, 2.88, s * (HK + 0.006)));                              // Dachkantenprofil
    add(box(0.025, 2.0, 0.025, M.alu, 1.37, 1.68, s * (HK + 0.002)));                               // Eckprofile
    add(box(0.025, 2.0, 0.025, M.alu, -3.39, 1.68, s * (HK + 0.002)));
    add(box(0.30, 0.25, 0.01, M.black, -3.0, 0.50, s * 0.90));                                       // Schmutzfänger
    add(box(0.30, 0.25, 0.01, M.black, 2.05, 0.50, s * (HC - 0.05)));
    add(box(1.8, 0.20, 0.03, M.darkgrey, 0.30, 0.56, s * (HK - 0.10)));                              // Seitenverkleidung
    add(cyl(0.02, 0.22, 6, M.steel, -3.42, 1.70, s * 1.06));                                          // Haltegriffe Heck
    add(box(0.06, 0.10, 0.04, M.steel, -3.41, 0.80, s * 0.99)); add(box(0.06, 0.10, 0.04, M.steel, -3.41, 2.40, s * 0.99)); // Scharniere
    for (let i = 0; i < 4; i++) add(box(0.10, 0.06, 0.06, M.steel, -2.0 + i * 0.6, 0.48, s * 0.45));     // Querträger
    // Wandschienen, Sauerstoff-Wandanschlüsse
    addI(box(2.8, 0.03, 0.02, M.alu, -1.3, 1.62, s * (HK - 0.03)));
  }
  for (let i = 0; i < 3; i++) addI(cyl(0.022, 0.03, 8, i ? M.white : M.green, -0.6 - i * 0.12, 1.55, -(HK - 0.04), 'z'));
  // Dachluke + Lüfter innen
  addI(box(0.55, 0.03, 0.55, M.frosted, -1.4, 2.84, 0)); add(box(0.62, 0.05, 0.62, M.darkgrey, -1.4, 2.90, 0));
  // Unterschränke links hinten (vor dem Fach) und Spritzenpumpen-Halter
  addI(cabinetBlock(M, 1.05, 0.55, 0.42, 2, 2, -1.10, 1.16, -(HK - 0.23), 1, 'z'));
  const pumps = new THREE.Group(); for (let i = 0; i < 2; i++) { pumps.add(box(0.16, 0.10, 0.10, M.white, 0, i * 0.12, 0)); pumps.add(box(0.10, 0.04, 0.005, M.screen, 0.0, i * 0.12, 0.052)); }
  pumps.position.set(-0.40, 1.65, -(HK - 0.08)); addI(pumps);
  addI(box(0.40, 0.30, 0.20, M.darkgrey, 0.95, 2.45, 0.0));                                          // Bedienteil Decke vorn
  addI(box(0.36, 0.006, 0.16, M.screen, 0.95, 2.30, 0.0));
  // Rucksackfach: Schublade darunter
  addI(box(0.01, 0.10, 0.80, M.cabinet, 0.17, 0.83, 0.67));
  // Fachböden in den Außenfächern
  for (const s of [1, -1]) addI(box(1.04, 0.025, HK - 0.64, M.alu, -2.22, 1.62, s * (0.62 + HK) / 2));
  // Heckleuchten-Verkleidung, Rückfahrkamera, Rammschutz
  add(box(0.06, 0.06, 0.10, M.black, -3.43, 2.80, 0)); add(box(0.03, 0.03, 2.0, M.black, -3.58, 0.56, 0));
  for (let i = 0; i < 8; i++) add(box(0.22, 0.008, 0.03, M.darkgrey, -3.53, 0.586, -0.9 + i * 0.26));

  // ---------- Trage mit Patient
  const trage = new THREE.Group();
  trage.add(box(1.96, 0.05, 0.58, M.black, 0, 0, 0));
  trage.add(box(1.20, 0.08, 0.56, M.mattress, -0.35, 0.065, 0));
  const back = box(0.72, 0.08, 0.56, M.mattress, 0.0, 0, 0); const bk = new THREE.Group(); bk.position.set(0.25, 0.065, 0); back.position.set(0.36, 0, 0); bk.add(back); bk.rotation.z = 0.35; trage.add(bk);
  trage.add(box(1.10, 0.03, 0.56, M.sheet, -0.40, 0.12, 0));
  for (const z of [0.30, -0.30]) { trage.add(box(1.5, 0.035, 0.03, M.yellow, -0.2, 0.08, z)); trage.add(box(0.04, 0.12, 0.03, M.yellow, 0.5, 0.06, z)); }
  trage.add(box(1.60, 0.16, 0.50, M.yellow, -0.1, -0.12, 0));
  trage.add(box(0.05, 0.05, 0.56, M.black, -1.0, 0.08, 0)); trage.add(box(0.05, 0.05, 0.56, M.black, 1.0, 0.08, 0));
  const legs = new THREE.Group();
  for (const x of [-0.75, 0.55]) for (const z of [0.2, -0.2]) { legs.add(box(0.05, 0.52, 0.05, M.yellow, x, -0.45, z)); legs.add(cyl(0.06, 0.04, 8, M.black, x, -0.72, z, 'z')); }
  legs.add(box(1.4, 0.04, 0.44, M.yellow, -0.1, -0.68, 0));
  trage.add(legs);
  v.addItem('trage', trage, { pos: V3(-1.95, 1.10, -0.05), apply: () => { legs.visible = false; } },
    { pos: V3(-5.0, 0.76, -0.05), apply: () => { legs.visible = true; } });
  const pat = person(M, 'lie', { top: new THREE.MeshStandardMaterial({ color: 0x7f8b97, flatShading: true }), legs: M.blue, hair: M.darkgrey });
  pat.add(box(1.0, 0.04, 0.5, M.sheet, -0.45, 0.14, 0)); // Decke
  v.addItem('patient', pat, { pos: V3(-0.05, 0.25, 0) });
  trage.add(pat);

  // ---------- Rucksäcke (Fach rechts) & Fächer
  const ruck = (mat) => { const g = bag(M, mat, 0.28, 0.52, 0.30); return g; };
  v.addItem('notfallrucksack', ruck(M.red), { pos: V3(-0.20, 0.88, 0.88) }, { pos: V3(0.6, 0, 1.75), rot: [0, 0.4, 0] });
  v.addItem('sauerstoffrucksack', ruck(M.blue), { pos: V3(-0.20, 0.88, 0.52) }, { pos: V3(0.1, 0, 1.85), rot: [0, -0.3, 0] });
  const chair = new THREE.Group();
  chair.add(box(0.05, 1.05, 0.46, M.alu, 0, 0.52, 0)); chair.add(box(0.36, 0.05, 0.46, M.alu, 0.18, 0.40, 0));
  chair.add(box(0.03, 0.55, 0.42, M.green, 0.03, 0.70, 0)); chair.add(box(0.33, 0.03, 0.42, M.green, 0.18, 0.44, 0));
  for (const z of [0.2, -0.2]) chair.add(cyl(0.05, 0.03, 8, M.black, 0.0, 0.05, z, 'z'));
  v.addItem('tragestuhl', chair, { pos: V3(-2.25, 0.88, 0.95), rot: [0, Math.PI / 2, 0] }, { pos: V3(-2.25, 0, 1.9), rot: [0, -0.5, 0] });
  const vak = new THREE.Group(); vak.add(box(0.28, 1.15, 0.38, M.blue, 0, 0.58, 0)); vak.add(box(0.29, 0.05, 0.39, M.yellow, 0, 0.9, 0)); vak.add(box(0.29, 0.05, 0.39, M.green, 0, 0.5, 0));
  v.addItem('vakuummatratze', vak, { pos: V3(-2.20, 1.15, -0.86) }, { pos: V3(-2.25, 0, -1.9), rot: [0, 0, Math.PI / 2 - 0.0] });
  v.items.vakuummatratze.outPose.pos.y = 0.15;
  v.addItem('notfallkoffer', bag(M, M.orange, 0.45, 0.30, 0.25), { pos: V3(-2.35, 0.88, -0.86) }, { pos: V3(-2.9, 0, -1.6) });

  // ---------- Personen
  const crew = { top: M.red, legs: M.darkgrey, stripe: M.neon };
  v.addItem('fahrer', person(M, 'sit', crew), { pos: V3(1.90, 1.03, -0.48) });
  v.addItem('beifahrer', person(M, 'sit', crew), { pos: V3(1.90, 1.03, 0.48) });
  v.addItem('betreuer', person(M, 'sit', crew), { pos: V3(-0.63, 1.39, -0.05), rot: [0, Math.PI, 0] });

  // ---------- Airbags
  const ab = (r, x, y, z, sx, sy, sz) => { const m = new THREE.Mesh(new THREE.IcosahedronGeometry(r, 1), M.airbag); m.position.set(x, y, z); m.scale.set(sx, sy, sz); v.airbags.add(m); };
  ab(0.23, 2.03, 1.33, -0.48, 0.45, 1, 1); ab(0.32, 2.16, 1.42, 0.48, 0.65, 0.8, 1.2);
  for (const s of [1, -1]) v.airbags.add(box(0.75, 0.34, 0.04, M.airbag, 1.78, 1.72, s * (HC - 0.07)));

  // ---------- echte Lichtquellen
  const spot = (z) => { const l = new THREE.SpotLight(0xfff1dc, 70, 32, 0.45, 0.5, 1.3); l.position.set(3.52, 1.0, z); l.target.position.set(10, 0, z * 1.2); R.add(l, l.target); v.realLights.head.push(l); };
  spot(0.7); spot(-0.7);
  const pl = (arr, col, i, x, y, z, d = 9) => { const l = new THREE.PointLight(col, i, d, 1.6); l.position.set(x, y, z); l.userData.i = i; R.add(l); arr.push(l); };
  pl(v.realLights.blue, 0x2a5cff, 18, 1.4, 3.15, 1.0); pl(v.realLights.blue, 0x2a5cff, 18, 1.4, 3.15, -1.0);
  pl(v.realLights.blue, 0x2a5cff, 10, 3.75, 0.85, 0.3); pl(v.realLights.blue, 0x2a5cff, 10, 3.75, 0.85, -0.3);
  pl(v.realLights.blue, 0x2a5cff, 14, -3.4, 3.15, 1.0); pl(v.realLights.blue, 0x2a5cff, 14, -3.4, 3.15, -1.0);
  pl(v.realLights.brake, 0xff1a10, 6, -3.7, 1.2, 0, 4);
  pl(v.realLights.ind, 0xff8a00, 3, 3.6, 1.0, 0.9, 3); pl(v.realLights.ind, 0xff8a00, 3, 3.6, 1.0, -0.9, 3);
  pl(v.realLights.ind, 0xff8a00, 3, -3.6, 0.86, 1.05, 3); pl(v.realLights.ind, 0xff8a00, 3, -3.6, 0.86, -1.05, 3);

  // ---------- Wrack: Frontalaufprall Fahrerseite
  v.wreckParams = {
    deform(p) {
      const xc = 1.72; if (p.x <= xc || p.y > 2.3) return false;
      const h = hash3(p.x, p.y, p.z), h2 = hash3(p.z, p.x, p.y);
      const left = smooth(0.9, -0.9, p.z), d = p.x - xc, t = smooth(xc, 3.5, p.x);
      if (p.y > 0.9 && p.x > 2.5) p.y += 0.18 * Math.sin(Math.PI * smooth(2.6, 3.5, p.x)) * (0.4 + 0.6 * left);
      p.x = xc + d * (0.80 - 0.32 * left) + (h - 0.5) * 0.07 * t;
      p.y += (h2 - 0.5) * 0.05 * t; p.z += (h - 0.5) * 0.05 * t;
      return true;
    },
    extra(on) {
      const w = R.getObjectByName('rad_vl'); w.position.x = on ? XF - 0.3 : XF; w.rotation.set(on ? 0.14 : 0, Math.PI + (on ? 0.5 : 0), 0);
    },
  };
  v.update(0);
  return v;
}
