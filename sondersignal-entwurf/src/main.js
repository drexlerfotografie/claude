import * as THREE from 'three';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js';
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js';
import { UnrealBloomPass } from 'three/addons/postprocessing/UnrealBloomPass.js';
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js';
import { buildNEF } from './nef.js';
import { buildRTW } from './rtw.js';
import { countTris } from './core.js';

const W = innerWidth, H = innerHeight;
const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
renderer.setSize(W, H); renderer.setPixelRatio(1);
renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap;
document.body.appendChild(renderer.domElement);
const scene = new THREE.Scene();
const pmrem = new THREE.PMREMGenerator(renderer);
const envTex = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
const camera = new THREE.PerspectiveCamera(32, W / H, 0.05, 200);

// Boden: Betonpflaster
const cv = document.createElement('canvas'); cv.width = cv.height = 512; const g = cv.getContext('2d');
g.fillStyle = '#9a9c9e'; g.fillRect(0, 0, 512, 512);
for (let i = 0; i < 4000; i++) { g.fillStyle = `rgba(${Math.random() < .5 ? 0 : 255},${Math.random() < .5 ? 0 : 255},${Math.random() < .5 ? 0 : 255},0.03)`; g.fillRect(Math.random() * 512, Math.random() * 512, 3, 3); }
g.strokeStyle = 'rgba(40,40,40,0.35)'; g.lineWidth = 3; for (let i = 0; i <= 512; i += 128) { g.beginPath(); g.moveTo(i, 0); g.lineTo(i, 512); g.stroke(); g.beginPath(); g.moveTo(0, i); g.lineTo(512, i); g.stroke(); }
const gt = new THREE.CanvasTexture(cv); gt.wrapS = gt.wrapT = THREE.RepeatWrapping; gt.repeat.set(40, 40); gt.colorSpace = THREE.SRGBColorSpace; gt.anisotropy = 8;
const ground = new THREE.Mesh(new THREE.PlaneGeometry(160, 160), new THREE.MeshStandardMaterial({ map: gt, roughness: 0.95 }));
ground.rotation.x = -Math.PI / 2; ground.receiveShadow = true; scene.add(ground);

const hemi = new THREE.HemisphereLight(0xdfe9ff, 0x6b6660, 1.0); scene.add(hemi);
const sun = new THREE.DirectionalLight(0xfff4e6, 2.6); sun.position.set(8, 14, 9); sun.castShadow = true;
sun.shadow.mapSize.set(4096, 4096); Object.assign(sun.shadow.camera, { left: -9, right: 9, top: 9, bottom: -9, near: 1, far: 50 }); sun.shadow.bias = -0.0004; sun.shadow.normalBias = 0.02;
scene.add(sun);

const composer = new EffectComposer(renderer);
composer.addPass(new RenderPass(scene, camera));
const bloom = new UnrealBloomPass(new THREE.Vector2(W, H), 0.9, 0.5, 0.85); composer.addPass(bloom);
composer.addPass(new OutputPass());

const vehicles = { NEF: buildNEF(), RTW: buildRTW() };
for (const v of Object.values(vehicles)) {
  v.root.traverse(o => { if (o.isMesh) { o.castShadow = !(o.material.transparent); o.receiveShadow = true; } });
  scene.add(v.root); v.root.visible = false;
}

function stats(v) {
  const isExtra = (o) => { let p = o; while (p) { if (p.userData.extra) return true; p = p.parent; } return false; };
  const isInt = (o) => { let p = o; while (p) { if (p.userData.interior) return true; p = p.parent; } return false; };
  const body = countTris(v.root, o => !isExtra(o) && !isInt(o));
  const innen = countTris(v.root, o => !isExtra(o) && isInt(o));
  const items = {};
  for (const [k, it] of Object.entries(v.items)) items[k] = countTris(it.obj, o => { let p = o; while (p && p !== it.obj) { if (p.userData.extra) return false; p = p.parent; } return true; });
  items.airbags = countTris(v.airbags);
  return { fahrzeug: body, innenraum: innen, zubehoer: items };
}
window.stats = () => Object.fromEntries(Object.entries(vehicles).map(([k, v]) => [k, stats(v)]));

function reset(v) {
  Object.keys(v.doors).forEach(k => { v.setDoor(k, 0); v.removeDoor(k, false); });
  Object.keys(v.items).forEach(k => v.setItem(k, 'in'));
  v.setWreck(false, v.wreckParams);
  v.setLights({ blue: false, low: false, hazard: false, brake: false, interior: false });
}
window.show = (cfg) => {
  for (const [k, v] of Object.entries(vehicles)) { v.root.visible = k === cfg.vehicle; v.root.position.set(0, 0, 0); }
  const v = vehicles[cfg.vehicle];
  reset(v);
  if (cfg.wreck) v.setWreck(true, v.wreckParams);
  for (const [k, t] of Object.entries(cfg.doors || {})) v.setDoor(k, t);
  for (const k of cfg.removed || []) v.removeDoor(k, true);
  for (const [k, s] of Object.entries(cfg.items || {})) v.setItem(k, s);
  v.setLights(cfg.lights || {});
  v.setInterior(cfg.interior !== false);
  const night = !!cfg.night;
  scene.background = new THREE.Color(night ? 0x0b1020 : 0xcfd8e2);
  scene.fog = new THREE.Fog(scene.background, 25, 70);
  scene.environment = night ? null : envTex;
  hemi.intensity = night ? 0.12 : 0.8; sun.intensity = night ? 0.05 : 2.2;
  bloom.enabled = night; bloom.strength = 0.55; bloom.radius = 0.3; bloom.threshold = 0.7;
  renderer.toneMappingExposure = night ? 1.0 : 0.85;
  camera.fov = cfg.fov || 32; camera.position.set(...cfg.cam); camera.lookAt(...(cfg.target || [0, 1, 0])); camera.updateProjectionMatrix();
  composer.render();
  return true;
};
window.ready = true;
