import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { EtiquetaPipe } from './formato';
import { Icono, NombreIcono } from './icono';
import { TipoTransaccion } from './models';

/** Tipo de movimiento con flecha de dirección: entra dinero, sale dinero o se mueve entre cuentas. */
@Component({
  selector: 'app-movimiento',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icono, EtiquetaPipe],
  template: `
    <span class="mov" [class]="tipo()">
      <span class="flecha-mov"><app-icono [nombre]="icono()" [tamano]="14" /></span>{{ tipo() | etiqueta }}
    </span>
  `
})
export class Movimiento {
  readonly tipo = input.required<TipoTransaccion>();
  protected readonly icono = computed<NombreIcono>(() =>
    ({ DEPOSITO: 'entrada', RETIRO: 'salida', TRANSFERENCIA: 'transferencias' } as const)[this.tipo()]);
}
