import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { ICONOS, NombreIcono } from './iconos.generados';

export type { NombreIcono };

/** Icono de Phosphor (MIT). Los trazos salen del paquete oficial vía scripts/generar-iconos.mjs. */
@Component({
  selector: 'app-icono',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { style: 'display: inline-flex; line-height: 0; flex-shrink: 0' },
  template: `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256" fill="currentColor"
         aria-hidden="true" focusable="false"
         [attr.width]="tamano()" [attr.height]="tamano()" [innerHTML]="trazo()"></svg>
  `
})
export class Icono {
  readonly nombre = input.required<NombreIcono>();
  readonly tamano = input(18);
  private readonly sanitizer = inject(DomSanitizer);
  // Los trazos son constantes generadas desde el paquete, nunca datos del usuario: es seguro confiar en ellos
  protected readonly trazo = computed(() => this.sanitizer.bypassSecurityTrustHtml(ICONOS[this.nombre()]));
}
