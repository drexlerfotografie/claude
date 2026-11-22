import * as THREE from 'three';
import { refine, edgeSet, skin, shrink, wallXY, plateXY, liveryUV, LiveryPainter, makeMaterials, box, cyl, wheel, person, Vehicle, mesh, grow, hash3, smooth, seat, bag, cabinetBlock } from './core.js';

const V3 = (x, y, z) => new THREE.Vector3(x, y, z);

export function buildNEF() {
  const S = { xmin: -2.68, L: 5.34, W: 1.93, H: 2.05 };
  const W = S.W, HW = W / 2;
  const v = new Vehicle('NEF');

  // ---------- Beklebung (Bayern NEF: Rot/Leuchtgelb-Karo)
  const P = new LiveryPainter(S);
  const RED = '#e1061c', YEL = '#d6f20f', GOLD = '#c9a227';
  P.sideRect(1.10, 2.7, 0.52, 1.30, RED);
  P.checker(-2.68, 1.10, 0.52, 1.92, 0.345, RED, YEL);
  P.sideRect(-2.7, 2.7, 0.49, 0.52, GOLD);
  P.sideText('NOTARZT', -0.08, 0.70, 0.16, '#16181c');
  P.endRect('front', -HW, HW, 0.0, 0.60, '#eef0f2');
  P.endRect('front', -HW, HW, 0.60, 1.30, RED);
  P.endRect('front', -0.58, 0.58, 0.63, 0.84, '#24272b');
  P.endRect('rear', -HW, HW, 0, 0.66, '#3a3d42');
  P.endChecker('rear', -HW, HW, 0.66, 1.92, 0.322, YEL, RED);
  const M = makeMaterials(P.texture()); v.M = M;
  const R = v.root;
  const add = (o, flags) => { if (flags) Object.assign(o.userData, flags); R.add(o); return o; };
  const addI = (o) => { v.interior.add(o); return o; };

  // ---------- Profil (x vorne +, y oben)
  const pts0 = [[2.47, 0.30], [2.57, 0.40], [2.60, 0.55], [2.58, 0.68], [2.53, 0.84], [2.38, 0.95], [2.12, 1.04], [1.84, 1.10], [1.58, 1.14],
    [1.22, 1.48], [0.88, 1.79], [0.66, 1.88], [0.20, 1.91], [-0.80, 1.92], [-1.80, 1.915], [-2.36, 1.90], [-2.50, 1.84], [-2.56, 1.60],
    [-2.59, 1.30], [-2.60, 1.00], [-2.60, 0.68], [-2.63, 0.56], [-2.62, 0.40], [-2.53, 0.30]];
  const NR = 3; const pts = refine(pts0, NR, new Set([8, 15, 20]));
  const so = { W, r: 0.12, k: 5, m: 7, crown: 0.025 };
  const uv = (g, o) => liveryUV(g, S, o);
  const sk = (i0, i1, extra = {}) => skin(pts, i0 * NR, i1 * NR, { ...so, ...extra });

  const bumperF = sk(0, 4); add(mesh(uv(bumperF.body), M.body));
  const hood = sk(4, 8, { crown: 0.03 });
  const ws = sk(8, 10, { glass: edgeSet([8, 9], NR) });
  add(mesh(uv(ws.body), M.body)); add(mesh(ws.glass, M.glass, { windshield: true }));
  const roof = sk(10, 15); add(mesh(uv(roof.body), M.body));
  const tgArcs = sk(15, 20, { only: 'arcs' }); add(mesh(uv(tgArcs.body), M.body));
  const tg = sk(15, 20, { only: 'mid', glass: edgeSet([16, 17, 18], NR) });
  const bumperR = sk(20, 23); add(mesh(uv(bumperR.body), M.body));

  // ---------- Seitenwände mit Ausschnitten
  const sp = shrink(pts, so.r);
  const arch = (xa) => { const a = []; for (let i = 0; i <= 10; i++) { const t = Math.PI - i / 10 * Math.PI; a.push([xa + 0.43 * Math.cos(t), 0.335 + 0.43 * Math.sin(t)]); } return a; };
  const XF = 1.55, XR = -1.65;
  const outline = [...sp, [sp[sp.length - 1][0], 0.31], ...arch(XR), ...arch(XF), [sp[0][0], 0.31]];
  const doorF = [[1.12, 0.38], [1.13, 0.98], [1.38, 1.03], [0.80, 1.66], [0.56, 1.68], [0.55, 0.38]];
  const winF = [[0.62, 1.10], [1.24, 1.10], [0.82, 1.58], [0.62, 1.60]];
  const doorS = [[0.49, 0.38], [0.49, 1.68], [-0.66, 1.68], [-0.66, 0.38]];
  const winS = [[0.42, 1.10], [0.42, 1.60], [-0.59, 1.60], [-0.59, 1.10]];
  const winQ = [[-0.76, 1.10], [-0.76, 1.62], [-2.30, 1.62], [-2.36, 1.10]];
  for (const s of [1, -1]) {
    add(mesh(uv(wallXY(outline, s > 0 ? [grow(doorF, 0.012), grow(doorS, 0.012), winQ] : [grow(doorF, 0.012), winS, winQ], s * HW, s)), M.body));
    if (s < 0) add(mesh(wallXY(winS, [], s * (HW - 0.012), s), M.glass));
    add(mesh(wallXY(winQ, [], s * (HW - 0.012), s), M.glass));
    // Vordertür
    const df = [mesh(uv(plateXY(doorF, [winF], s * HW, s), { innerZ: s }), M.body), mesh(wallXY(winF, [], s * (HW - 0.02), s), M.glass),
      box(0.12, 0.03, 0.025, M.black, 0.70, 1.02, s * (HW + 0.01)),
      box(0.40, 0.45, 0.02, M.darkgrey, 0.84, 0.78, s * (HW - 0.06))];
    v.pivot(s > 0 ? 'tuer_beifahrer' : 'tuer_fahrer', V3(1.12, 0, s * HW), df, { type: 'hinge', axis: 'y', angle: s * 1.15 });
    if (s < 0) continue;
    // Schiebetür
    const ds = [mesh(uv(plateXY(doorS, [winS], s * HW, s), { innerZ: s }), M.body), mesh(wallXY(winS, [], s * (HW - 0.02), s), M.glass),
      box(0.14, 0.03, 0.025, M.black, 0.36, 1.02, s * (HW + 0.01)), box(1.1, 0.025, 0.02, M.darkgrey, -0.08, 1.64, s * (HW + 0.006))];
    v.pivot('schiebetuer', V3(0, 0, 0), ds, { type: 'slide', out: V3(0, 0, s * 0.075), travel: V3(-1.12, 0, 0) });
    add(box(1.5, 0.03, 0.03, M.darkgrey, -1.25, 1.64, s * (HW + 0.004))); // Laufschiene
  }

  // Motorhaube, Heckklappe
  v.pivot('motorhaube', V3(1.58, 1.14, 0), [mesh(uv(hood.body), M.body)], { type: 'hinge', axis: 'z', angle: 0.95 });
  const tgParts = [mesh(uv(tg.body), M.body), mesh(tg.glass, M.glass),
    box(0.03, 0.03, 0.34, M.brake3, -2.50, 1.83, 0), box(0.02, 0.11, 0.50, M.plate, -2.612, 0.80, 0),
    box(0.03, 0.02, 0.40, M.black, -2.57, 1.30, 0.05, 0, 0.0, 0.3), box(0.05, 0.03, 0.24, M.steel, -2.615, 1.02, 0)];
  v.pivot('heckklappe', V3(-2.36, 1.90, 0), tgParts, { type: 'hinge', axis: 'z', angle: -1.45 });

  // ---------- Anbauteile außen
  add(box(4.95, 0.02, 1.86, M.black, -0.05, 0.315, 0));                                 // Unterboden
  add(box(0.04, 0.06, 1.30, M.darkgrey, 2.60, 0.47, 0));                                 // Stoßfänger-Lippe
  for (const y of [0.68, 0.73, 0.78]) add(box(0.02, 0.012, 1.08, M.black, 2.583, y, 0)); // Kühlergrill
  add(box(0.03, 0.08, 0.80, M.black, 2.605, 0.46, 0));                                  // Lufteinlass
  add(box(0.02, 0.11, 0.50, M.plate, 2.61, 0.56, 0));                                    // Kennzeichen
  for (const s of [1, -1]) {
    add(box(0.12, 0.10, 0.40, M.head, 2.48, 0.84, s * 0.64, 0, -s * 0.33, 0));          // Scheinwerfer
    add(box(0.10, 0.03, 0.10, M.ind, 2.43, 0.86, s * 0.84, 0, -s * 0.33, 0));           // Blinker vorn
    add(box(0.03, 0.05, 0.14, s > 0 ? M.blueA : M.blueB, 2.588, 0.755, s * 0.30));      // Frontblitzer Grill
    add(box(0.03, 0.04, 0.12, s > 0 ? M.blueB : M.blueA, 2.605, 0.52, s * 0.62));       // Frontblitzer Stoßfänger
    add(box(0.06, 0.03, 0.01, M.ind, 2.05, 0.80, s * (HW + 0.004)));                    // Seitenblinker
    // Spiegel
    add(box(0.10, 0.05, 0.12, M.black, 1.30, 1.17, s * (HW + 0.05)));
    add(box(0.10, 0.20, 0.24, M.black, 1.27, 1.25, s * (HW + 0.15)));
    add(box(0.02, 0.03, 0.12, M.ind, 1.31, 1.18, s * (HW + 0.15)));
    // Rückleuchten (in den Säulen)
    add(box(0.05, 0.30, 0.12, M.rearRed, -2.585, 1.36, s * 0.90));
    add(box(0.05, 0.10, 0.12, M.ind, -2.59, 1.14, s * 0.90));
    add(box(0.05, 0.08, 0.12, M.head, -2.595, 1.03, s * 0.90));
    add(box(0.04, 0.05, 0.12, M.rearRed, -2.63, 0.50, s * 0.75));
    // Blau hinten (Dachkante)
    add(cyl(0.055, 0.07, 8, s > 0 ? M.blueA : M.blueB, -2.30, 1.95, s * 0.78));
    // Radhausschalen
    for (const xa of [XF, XR]) {
      const g = new THREE.CylinderGeometry(0.43, 0.43, 0.30, 10, 1, true, Math.PI / 2, Math.PI); g.rotateX(Math.PI / 2);
      add(mesh(g, M.black)).position.set(xa, 0.335, s * (HW - 0.17));
    }
    // Räder
    for (const xa of [XF, XR]) {
      const w = wheel(M, 0.335, 0.22, { seg: 24, spokes: 7 }); w.position.set(xa, 0.335, s * 0.83); if (s < 0) w.rotation.y = Math.PI;
      w.name = (xa > 0 ? 'rad_v' : 'rad_h') + (s > 0 ? 'r' : 'l'); add(w);
    }
  }
  // Wischer, Dachbalken, Antennen
  add(box(0.03, 0.015, 0.62, M.black, 1.55, 1.18, -0.25, 0, 0.25, 0)); add(box(0.03, 0.015, 0.62, M.black, 1.55, 1.18, 0.40, 0, 0.25, 0));
  add(box(0.34, 0.06, 1.36, M.black, 0.45, 1.945, 0));
  [-0.48, -0.16, 0.16, 0.48].forEach((z, i) => add(box(0.30, 0.07, 0.29, i % 2 ? M.blueB : M.blueA, 0.45, 1.99, z)));
  add(box(0.06, 0.04, 1.2, M.work, 0.62, 1.95, 0));
  add(cyl(0.006, 0.35, 4, M.black, -0.6, 2.09, -0.3)); add(cyl(0.006, 0.25, 4, M.black, -0.9, 2.04, 0.2));
  add(box(0.30, 0.04, 0.6, M.darkgrey, 0.15, 1.935, 0)); // Funkantennensockel

  // ---------- Innenraum
  addI(box(3.2, 0.05, 1.80, M.floorNEF, -0.95, 0.425, 0));
  addI(box(0.9, 0.04, 1.80, M.black, 1.10, 0.40, 0));
  addI(box(0.50, 0.30, 1.84, M.darkgrey, 1.42, 0.96, 0));
  addI(box(0.40, 0.05, 1.84, M.black, 1.30, 1.13, 0, 0, 0, -0.25));
  addI(box(0.16, 0.10, 0.40, M.black, 1.15, 1.19, -0.45));
  addI(box(0.04, 0.16, 0.24, M.screen, 1.17, 1.16, 0.0, 0, 0, -0.3));
  addI(box(0.45, 0.30, 0.28, M.black, 0.92, 0.62, 0));
  const sw = new THREE.Mesh(new THREE.TorusGeometry(0.19, 0.022, 4, 14), M.black); sw.position.set(1.02, 1.08, -0.45); sw.rotation.set(0, Math.PI / 2, 0); sw.rotateX(0.45); addI(sw);
  addI(cyl(0.03, 0.3, 6, M.black, 1.12, 1.02, -0.45, 'x')).rotation.z = Math.PI / 2 + 0.45;
  for (const s of [1, -1]) { const st = seat(M, M.seatBlack); st.position.set(0.78, 0.76, s * 0.45); addI(st); }
  const st2 = seat(M, M.seatBlack); st2.position.set(-0.30, 0.76, -0.45); addI(st2);
  addI(cyl(0.07, 0.48, 8, M.red, -0.78, 0.70, -0.80));                                   // Feuerlöscher
  addI(cabinetBlock(M, 0.65, 0.62, 0.62, 1, 2, -0.25, 0.76, 0.56, 1, 'x'));               // Schrank rechts
  addI(box(0.66, 0.03, 0.64, M.white, -0.25, 1.085, 0.56)); addI(box(0.67, 0.02, 0.65, M.trimBlue, -0.25, 1.105, 0.56));
  addI(box(0.30, 0.18, 0.40, M.steel, -0.30, 1.21, 0.56, 0, 0, 0.3));                     // Ablagekorb
  addI(box(0.03, 1.40, 1.80, M.alu, -1.30, 1.15, 0));                                     // Trennwand Heckregal
  addI(box(1.20, 0.03, 1.78, M.alu, -1.92, 0.98, 0)); addI(box(1.20, 0.03, 0.88, M.alu, -1.92, 1.38, 0.45));
  addI(box(1.20, 0.03, 0.88, M.alu, -1.92, 1.42, -0.45));
  for (const z of [-0.88, 0.0, 0.88]) addI(box(1.18, 1.36, 0.025, M.alu, -1.92, 1.16, z));
  addI(box(0.06, 0.06, 1.8, M.ceiling, -0.6, 1.86, 0)); addI(box(0.06, 0.06, 1.8, M.ceiling, -1.9, 1.86, 0));
  // Auszug (Schublade im Heck)
  const drawer = [box(1.10, 0.04, 1.70, M.alu, -1.95, 0.50, 0), box(0.04, 0.12, 1.70, M.alu, -2.48, 0.55, 0),
    box(0.30, 0.10, 0.30, M.red, -1.95, 0.57, 0.55), box(0.40, 0.08, 0.45, M.orange, -1.85, 0.56, -0.40), box(0.5, 0.05, 0.25, M.yellow, -2.05, 0.55, 0.1)];
  v.pivot('auszug', V3(0, 0, 0), drawer, { type: 'drawer', travel: V3(-0.75, 0, 0) }).userData.interior = true;

  // ---------- Detailstufe
  // Motorraum
  add(box(0.45, 0.32, 0.70, M.darkgrey, 2.05, 0.78, 0)); add(box(0.40, 0.06, 0.55, M.black, 2.05, 0.78+0.19, 0));
  add(box(0.20, 0.18, 0.28, M.black, 2.05+0.05, 0.78+0.02, 0.55)); add(cyl(0.07, 0.14, 6, M.white, 2.05-0.05, 0.78+0.08, -0.55));
  for (let i = 0; i < 4; i++) addI(box(0.02, 0.05, 0.10, M.black, 1.18, 1.07, -0.75 + i * 0.5));            // Lüftungsdüsen
  addI(box(0.03, 0.10, 0.36, M.darkgrey, 1.20, 0.92, 0.45));                                             // Handschuhfach
  const bed = new THREE.Group(); bed.add(box(0.12, 0.10, 0.24, M.black, 0, 0, 0));
  for (let i = 0; i < 6; i++) bed.add(box(0.012, 0.025, 0.03, i < 2 ? M.blue : M.white, 0.065, 0.02 - (i % 2) * 0.04, -0.08 + Math.floor(i / 2) * 0.08));
  bed.position.set(1.05, 0.86, 0.0); addI(bed);                                                           // Sondersignal-Bedienteil
  addI(box(0.10, 0.06, 0.20, M.black, 1.02, 0.74, 0.0)); addI(box(0.006, 0.03, 0.10, M.screen, 1.075, 0.75, 0.0)); // Funkgerät
  addI(cyl(0.02, 0.18, 6, M.black, 0.98, 0.84, 0.10)); addI(cyl(0.035, 0.05, 6, M.darkgrey, 0.98, 0.94, 0.10)); // Schalthebel
  addI(box(0.12, 0.20, 0.30, M.darkgrey, 0.98, 1.28, 0.30)); addI(box(0.005, 0.13, 0.22, M.screen, 1.04, 1.30, 0.30)); // Tablet/Navi
  for (const s of [1, -1]) {
    addI(box(0.04, 0.03, 0.30, M.black, 0.80, 1.80, s * 0.70));                                           // Haltegriffe
    addI(box(0.40, 0.025, 0.10, M.ceiling, 0.70, 1.86, s * 0.20));                                         // Leselampe
    add(box(1.2, 0.04, 0.03, M.black, -0.05, 0.62, s * (HW + 0.012)));                                    // Schutzleiste
    add(box(0.5, 0.04, 0.03, M.black, 0.84, 0.62, s * (HW + 0.012)));
    add(box(0.05, 0.12, 0.10, M.darkgrey, -2.40, 0.50, s * 0.55));                                        // Stoßfänger-Ecken
    add(box(0.004, 0.17, 0.21, M.steel, 1.215, 1.25, s * (HW + 0.15)));                                   // Spiegelglas
    addI(box(0.60, 0.04, 0.10, M.darkgrey, 0.86, 0.90, s * (HW - 0.08)));                                  // Armlehne
  }
  add(box(0.04, 0.012, 0.08, M.darkgrey, -1.9, 0.92, -HW - 0.006));                                       // Tankdeckel
  add(cyl(0.03, 0.18, 8, M.steel, -2.55, 0.33, -0.55, 'x'));                                              // Auspuff
  add(box(0.08, 0.06, 0.10, M.darkgrey, -2.66, 0.36, 0));                                                // AHK
  // Lichtbalken: LED-Module
  [-0.48, -0.16, 0.16, 0.48].forEach(z => { for (let i = -1; i <= 1; i++) add(box(0.24, 0.02, 0.06, M.blueA, 0.45, 2.03, z + i * 0.08)); });
  add(box(0.36, 0.02, 1.38, M.darkgrey, 0.45, 1.915, 0));
  // Heckregal: Schubladen links unten
  for (let i = 0; i < 3; i++) addI(box(0.02, 0.12, 0.80, M.white, -2.52, 0.62 + i * 0.13, -0.45));
  for (let i = 0; i < 3; i++) addI(box(0.02, 0.03, 0.12, M.trimBlue, -2.535, 0.65 + i * 0.13, -0.45));
  addI(box(0.30, 0.03, 0.80, M.trimBlue, -2.38, 0.97, -0.45));
  addI(box(0.08, 0.30, 0.06, M.black, -2.00, 1.62, -0.85));                                              // Corpuls-Halterung
  for (const z of [-0.6, 0.6]) addI(box(0.02, 0.02, 0.5, M.yellow, -2.53, 1.38, z));                      // Spanngurte
  // Sitzbank-Schrank Schubladen
  for (let i = 0; i < 3; i++) addI(box(0.012, 0.012, 0.50, M.trimBlue, 0.085, 0.62 + i * 0.12, 0.56));

  // ---------- Geräte (rausnehmbar)
  const out = (x, z, ry = 0) => ({ pos: V3(x, 0.0, z), rot: [0, ry, 0] });
  v.addItem('notfallrucksack', bag(M, M.red, 0.30, 0.34, 0.52), { pos: V3(-1.90, 1.00, 0.45), rot: [0, 0, 0] }, out(-3.3, 1.2, 0.4));
  v.addItem('kinderrucksack', bag(M, M.blue, 0.30, 0.30, 0.48), { pos: V3(-1.90, 0.62, 0.45) }, out(-3.4, 0.5, -0.3));
  v.addItem('beatmung', bag(M, M.yellow, 0.28, 0.24, 0.48, false), { pos: V3(-1.90, 1.44, 0.45) }, out(-3.6, -0.3, 0.2));
  const defi = new THREE.Group(); defi.add(box(0.24, 0.30, 0.34, M.darkgrey, 0, 0.15, 0)); defi.add(box(0.01, 0.16, 0.22, M.screen, 0.125, 0.20, 0)); defi.add(box(0.06, 0.04, 0.2, M.black, 0, 0.32, 0)); defi.add(box(0.25, 0.08, 0.35, M.red, 0, 0.04, 0));
  v.addItem('defibrillator', defi, { pos: V3(-1.90, 1.00, -0.45), rot: [0, Math.PI, 0] }, out(-3.2, -1.1, Math.PI + 0.5));
  const o2 = new THREE.Group(); o2.add(cyl(0.07, 0.55, 8, M.white, 0, 0.28, 0)); o2.add(cyl(0.04, 0.08, 6, M.green, 0, 0.58, 0));
  v.addItem('sauerstoff', o2, { pos: V3(-2.10, 0.45, -0.70) }, out(-3.0, -1.5));
  const absaug = new THREE.Group(); absaug.add(box(0.22, 0.26, 0.30, M.white, 0, 0.13, 0)); absaug.add(cyl(0.05, 0.16, 8, M.frosted, 0.0, 0.33, 0.05));
  v.addItem('absaugpumpe', absaug, { pos: V3(-1.95, 1.44, -0.45), rot: [0, Math.PI, 0] }, out(-3.8, -0.9));

  // ---------- Personen
  const crew = { top: M.red, legs: M.darkgrey, stripe: M.neon };
  const p1 = person(M, 'sit', crew); v.addItem('fahrer', p1, { pos: V3(0.86, 0.82, -0.45) });
  const p2 = person(M, 'sit', crew); v.addItem('notarzt', p2, { pos: V3(0.86, 0.82, 0.45) });
  const p3 = person(M, 'sit', { top: M.neon, legs: M.darkgrey, stripe: M.steel }); v.addItem('person_fond', p3, { pos: V3(-0.22, 0.82, -0.45) });

  // ---------- Airbags
  const ab = (r, x, y, z, sx, sy, sz) => { const m = new THREE.Mesh(new THREE.IcosahedronGeometry(r, 1), M.airbag); m.position.set(x, y, z); m.scale.set(sx, sy, sz); v.airbags.add(m); };
  ab(0.22, 0.88, 1.12, -0.45, 0.45, 1, 1); ab(0.30, 1.02, 1.22, 0.45, 0.65, 0.8, 1.15);
  for (const s of [1, -1]) { v.airbags.add(box(2.9, 0.34, 0.04, M.airbag, -0.65, 1.50, s * (HW - 0.06))); ab(0.18, 0.55, 1.05, s * 0.78, 0.6, 1.2, 0.35); }

  // ---------- echte Lichtquellen (für Nachtbilder)
  const spot = (z) => { const l = new THREE.SpotLight(0xfff1dc, 60, 30, 0.45, 0.5, 1.3); l.position.set(2.55, 0.84, z); l.target.position.set(9, 0, z * 1.2); R.add(l, l.target); v.realLights.head.push(l); };
  spot(0.64); spot(-0.64);
  const pl = (arr, col, i, x, y, z, d = 8) => { const l = new THREE.PointLight(col, i, d, 1.6); l.position.set(x, y, z); l.userData.i = i; R.add(l); arr.push(l); };
  pl(v.realLights.blue, 0x2a5cff, 14, 0.45, 2.15, 0.6); pl(v.realLights.blue, 0x2a5cff, 14, 0.45, 2.15, -0.6);
  pl(v.realLights.blue, 0x2a5cff, 8, 2.8, 0.7, 0.3); pl(v.realLights.blue, 0x2a5cff, 8, 2.8, 0.7, -0.3);
  pl(v.realLights.blue, 0x2a5cff, 6, -2.6, 2.1, 0.7); pl(v.realLights.blue, 0x2a5cff, 6, -2.6, 2.1, -0.7);
  pl(v.realLights.brake, 0xff1a10, 5, -2.9, 1.2, 0, 4);
  pl(v.realLights.ind, 0xff8a00, 3, 2.7, 0.85, 0.85, 3); pl(v.realLights.ind, 0xff8a00, 3, 2.7, 0.85, -0.85, 3);
  pl(v.realLights.ind, 0xff8a00, 3, -2.8, 1.15, 0.9, 3); pl(v.realLights.ind, 0xff8a00, 3, -2.8, 1.15, -0.9, 3);

  // ---------- Wrack: Frontalaufprall versetzt (Fahrerseite)
  v.wreckParams = {
    deform(p) {
      const xc = 0.62; if (p.x <= xc) return false;
      const h = hash3(p.x, p.y, p.z), h2 = hash3(p.z, p.x, p.y);
      const left = smooth(0.9, -0.9, p.z); const d = p.x - xc;
      const k = 0.78 - 0.33 * left;
      const t = smooth(xc, 2.6, p.x);
      if (p.y > 0.8 && p.x > 1.5) p.y += 0.16 * Math.sin(Math.PI * smooth(1.5, 2.6, p.x)) * (0.4 + 0.6 * left);
      p.x = xc + d * k + (h - 0.5) * 0.06 * t;
      p.y += (h2 - 0.5) * 0.04 * t;
      p.z += (h - 0.5) * 0.04 * t + (p.z > 0 ? 0.03 : 0.02) * t;
      return true;
    },
    extra(on) {
      const w = R.getObjectByName('rad_vl'); if (!w) return;
      w.position.x = on ? XF - 0.25 : XF; w.rotation.set(on ? 0.12 : 0, Math.PI + (on ? 0.45 : 0), 0);
    },
  };
  v.static = true;
  v.update(0);
  return v;
}
