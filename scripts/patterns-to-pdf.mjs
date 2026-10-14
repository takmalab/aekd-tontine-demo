// Convertit les motifs africains (frontend/public/assets/patterns/*.svg) en tracés
// simples pour les PDF du backend (backend/src/main/resources/branding/patterns/*.path).
//
// Le moteur PDF ne sait pas dessiner les arcs SVG : les arcs sont convertis en
// courbes de Bézier, les raccourcis (S, T) développés et toutes les coordonnées
// rendues absolues. Il ne reste que les commandes M, L, H, V, C, Q et Z,
// lues par PdfPattern.java.
//
// À relancer après toute modification d'un motif :
//   npm install --prefix <dossier-temporaire> svgpath@2.6.0
//   NODE_PATH=<dossier-temporaire>/node_modules node scripts/patterns-to-pdf.mjs
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const require = createRequire(join(process.cwd(), 'noop.js'));
const svgpath = require('svgpath');

const source = join(root, 'frontend', 'public', 'assets', 'patterns');
const target = join(root, 'backend', 'src', 'main', 'resources', 'branding', 'patterns');
mkdirSync(target, { recursive: true });

for (const name of ['aekd-masques', 'aekd-symboles']) {
  const svg = readFileSync(join(source, `${name}.svg`), 'utf8');
  const viewBox = svg.match(/viewBox="([^"]+)"/)[1];
  const d = svg.match(/\sd="([^"]+)"/)[1];
  const simple = svgpath(d).abs().unarc().unshort().round(1).toString();
  const unexpected = simple.match(/[^MLHVCQZ0-9\s.,eE+-]/g);
  if (unexpected) {
    throw new Error(`${name} : commandes inattendues ${[...new Set(unexpected)].join(' ')}`);
  }
  // Ligne 1 : viewBox ; ligne 2 : tracé (règle de remplissage evenodd).
  writeFileSync(join(target, `${name}.path`), `${viewBox}\n${simple}\n`);
  console.log(`${name}.path : ${(simple.length / 1024).toFixed(0)} ko`);
}
