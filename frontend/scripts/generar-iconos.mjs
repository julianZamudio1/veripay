// Genera src/app/core/iconos.generados.ts con los trazos oficiales de Phosphor Icons (MIT)
// solo para los iconos que usa VeriPay. Ejecutar: npm run iconos
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { join } from 'node:path';

// El paquete no exporta su package.json, así que se resuelve desde node_modules del proyecto
const raiz = fileURLToPath(new URL('../node_modules/@phosphor-icons/core/assets', import.meta.url));

/** nombre en VeriPay → [nombre en Phosphor, peso] */
const ICONOS = {
  tablero: ['squares-four', 'regular'],
  clientes: ['users-three', 'regular'],
  cuentas: ['credit-card', 'regular'],
  transferencias: ['arrows-left-right', 'regular'],
  auditoria: ['scroll', 'regular'],
  salir: ['sign-out', 'regular'],
  menu: ['list', 'regular'],
  cerrar: ['x', 'regular'],
  mas: ['plus', 'bold'],
  buscar: ['magnifying-glass', 'regular'],
  identificacion: ['identification-card', 'regular'],
  selfie: ['user-focus', 'regular'],
  check: ['check', 'bold'],
  checkCirculo: ['check-circle', 'fill'],
  alerta: ['warning-circle', 'fill'],
  flechaIzq: ['arrow-left', 'regular'],
  flechaDer: ['arrow-right', 'regular'],
  escudo: ['shield-check', 'regular'],
  billetera: ['wallet', 'regular'],
  actividad: ['pulse', 'regular'],
  reloj: ['clock', 'regular'],
  candado: ['lock', 'regular'],
  candadoAbierto: ['lock-open', 'regular'],
  huella: ['fingerprint', 'regular'],
  sello: ['seal-check', 'fill'],
  banco: ['bank', 'regular'],
  entrada: ['arrow-down-left', 'bold'],
  salida: ['arrow-up-right', 'bold'],
  ver: ['eye', 'regular'],
  ocultar: ['eye-slash', 'regular'],
  siguiente: ['caret-right', 'bold']
};

const lineas = Object.entries(ICONOS).map(([nombre, [archivo, peso]]) => {
  const sufijo = peso === 'regular' ? '' : `-${peso}`;
  const svg = readFileSync(join(raiz, peso, `${archivo}${sufijo}.svg`), 'utf8');
  const interior = svg.replace(/^<svg[^>]*>/, '').replace(/<\/svg>\s*$/, '').trim();
  return `  ${nombre}: '${interior.replace(/'/g, "\\'")}'`;
});

const salida = `// ARCHIVO GENERADO por scripts/generar-iconos.mjs. No editar a mano.
// Trazos de Phosphor Icons (https://phosphoricons.com), licencia MIT. viewBox 0 0 256 256.
export const ICONOS = {
${lineas.join(',\n')}
} as const;

export type NombreIcono = keyof typeof ICONOS;
`;

writeFileSync(new URL('../src/app/core/iconos.generados.ts', import.meta.url), salida);
console.log(`iconos.generados.ts: ${lineas.length} iconos`);
