import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Guilloche } from './guilloche';
import { Icono } from './icono';

/**
 * Sello de "identidad verificada": lámina holográfica (degradado cónico) bajo un guilloché.
 * Al pasar el cursor la iridiscencia gira, como un holograma real al inclinarlo.
 */
@Component({
  selector: 'app-sello',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Guilloche, Icono],
  host: { role: 'img', '[attr.aria-label]': 'etiqueta()', '[style.--tam.px]': 'tamano()' },
  template: `
    <span class="lamina"></span>
    <app-guilloche class="lineas" [densidad]="2" detalle="simple" />
    <span class="centro"><app-icono nombre="check" [tamano]="tamano() * 0.34" /></span>
  `,
  styles: `
    @property --giro { syntax: '<angle>'; initial-value: 0deg; inherits: false; }
    :host { position: relative; display: inline-grid; place-items: center; width: var(--tam); height: var(--tam); flex-shrink: 0; }
    .lamina {
      position: absolute; inset: 0; border-radius: 50%;
      background: conic-gradient(from var(--giro), #c4f25a, #8fe3d1, #b9c7ff, #f2b8e6, #ffe08a, #c4f25a);
      transition: --giro 900ms cubic-bezier(.2, 0, 0, 1);
      box-shadow: inset 0 0 0 1px rgb(255 255 255 / .35), inset 0 -6px 14px rgb(0 0 0 / .18);
    }
    :host(:hover) .lamina { --giro: 160deg; }
    .lineas { position: absolute; inset: 6%; color: rgb(14 26 4 / .45); }
    .centro {
      position: relative; display: grid; place-items: center;
      width: 46%; height: 46%; border-radius: 50%;
      background: #0e1a04; color: #c4f25a;
      box-shadow: 0 0 0 2px rgb(255 255 255 / .55);
    }
    @media (prefers-reduced-motion: reduce) { .lamina { transition: none; } }
  `
})
export class Sello {
  readonly tamano = input(56);
  readonly etiqueta = input('Identidad verificada');
}
