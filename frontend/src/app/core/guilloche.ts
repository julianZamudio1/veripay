import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Guilloché generativo: los patrones de líneas finas de billetes, pasaportes e INE.
 * Cada capa es una hipotrocoide (curva de un círculo que rueda dentro de otro), la misma
 * matemática de las máquinas de grabado. Se calcula en código; no hay ilustraciones dibujadas a mano.
 */
function hipotrocoide(R: number, r: number, d: number, puntos: number, centro: number): string {
  const mcd = (a: number, b: number): number => (b === 0 ? a : mcd(b, a % b));
  const vueltas = r / mcd(R, r);            // revoluciones necesarias para cerrar la curva
  const total = 2 * Math.PI * vueltas;
  const k = (R - r) / r;
  let ruta = '';
  for (let i = 0; i <= puntos; i++) {
    const t = (i / puntos) * total;
    const x = centro + (R - r) * Math.cos(t) + d * Math.cos(k * t);
    const y = centro + (R - r) * Math.sin(t) - d * Math.sin(k * t);
    ruta += `${i === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`;
  }
  return ruta + 'Z';
}

/** Banda ondulada (bordes de billete): senoidales desfasadas que se cruzan. */
function onda(fase: number, amplitud: number, ancho: number, alto: number): string {
  let ruta = '';
  const pasos = 240;
  for (let i = 0; i <= pasos; i++) {
    const x = (i / pasos) * ancho;
    const y = alto / 2 + amplitud * Math.sin((x / ancho) * Math.PI * 12 + fase)
      + amplitud * 0.35 * Math.sin((x / ancho) * Math.PI * 31 + fase * 2);
    ruta += `${i === 0 ? 'M' : 'L'}${x.toFixed(1)} ${y.toFixed(1)}`;
  }
  return ruta;
}

@Component({
  selector: 'app-guilloche',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true', class: 'guilloche', '[class.animado]': 'animar()' },
  template: `
    @if (variante() === 'roseta') {
      <svg viewBox="0 0 240 240" xmlns="http://www.w3.org/2000/svg">
        @for (c of capas(); track $index) {
          <path [attr.d]="c" pathLength="1" [style.--i]="$index" />
        }
      </svg>
    } @else {
      <svg viewBox="0 0 1200 120" preserveAspectRatio="none" xmlns="http://www.w3.org/2000/svg">
        @for (c of capas(); track $index) {
          <path [attr.d]="c" pathLength="1" [style.--i]="$index" />
        }
      </svg>
    }
  `,
  styles: `
    :host { display: block; color: inherit; pointer-events: none; }
    svg { display: block; width: 100%; height: 100%; overflow: visible; }
    path { fill: none; stroke: currentColor; stroke-width: 1; vector-effect: non-scaling-stroke; }
    :host(.animado) path {
      stroke-dasharray: 1; stroke-dashoffset: 1;
      animation: trazar 2.6s cubic-bezier(.65, 0, .35, 1) forwards;
      animation-delay: calc(var(--i) * 180ms);
    }
    @keyframes trazar { to { stroke-dashoffset: 0; } }
    @media (prefers-reduced-motion: reduce) {
      :host(.animado) path { animation: none; stroke-dashoffset: 0; }
    }
  `
})
export class Guilloche {
  readonly variante = input<'roseta' | 'banda'>('roseta');
  /** Número de curvas superpuestas: más capas, más densidad. */
  readonly densidad = input(5);
  readonly animar = input(false);
  /** "fino": entramado denso para tamaños grandes · "simple": pocas vueltas, nítido a 24-64 px. */
  readonly detalle = input<'fino' | 'simple'>('fino');

  protected readonly capas = computed(() => {
    const n = this.densidad();
    if (this.variante() === 'banda') {
      return Array.from({ length: n }, (_, i) => onda((i / n) * Math.PI, 26 + i * 3, 1200, 120));
    }
    if (this.detalle() === 'simple') {
      // R=96 y r=36 cierran en 3 vueltas: una roseta de 8 pétalos que se lee bien en tamaños chicos
      return Array.from({ length: n }, (_, i) => hipotrocoide(96, 36, 44 + i * 10, 480, 120));
    }
    // R=96 y r=21 cierran la curva en 7 vueltas; variar d produce el entramado de grabado
    return Array.from({ length: n }, (_, i) => hipotrocoide(96, 21, 52 + i * 7, 1400, 120));
  });
}
