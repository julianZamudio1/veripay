import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService, mensajeError } from '../../core/api.service';
import { Tablero, Transaccion } from '../../core/models';

@Component({
  selector: 'app-tablero',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, RouterLink],
  template: `
    <h1>Tablero</h1>
    <p class="subtitulo">Resumen de la operación de hoy</p>

    @if (error()) {
      <div class="aviso error">{{ error() }}</div>
    }

    @if (datos(); as d) {
      <div class="rejilla indicadores">
        <div class="tarjeta">
          <span class="etiqueta">Saldo administrado</span>
          <strong>{{ d.saldoTotal | currency: 'MXN' : 'symbol-narrow' }}</strong>
          <span class="ayuda">{{ d.cuentas }} cuentas</span>
        </div>
        <div class="tarjeta">
          <span class="etiqueta">Operaciones hoy</span>
          <strong>{{ d.transaccionesHoy | number }}</strong>
          <span class="ayuda">{{ d.volumenHoy | currency: 'MXN' : 'symbol-narrow' }} de volumen</span>
        </div>
        <div class="tarjeta">
          <span class="etiqueta">Clientes</span>
          <strong>{{ d.clientesTotal | number }}</strong>
          <span class="ayuda">{{ porcentajeVerificados(d) | number: '1.0-0' }}% con identidad verificada</span>
        </div>
        <div class="tarjeta">
          <span class="etiqueta">KYC pendiente</span>
          <strong>{{ d.clientesPendientes + d.clientesRechazados }}</strong>
          <a routerLink="/clientes" class="ayuda">Revisar clientes →</a>
        </div>
      </div>

      <div class="tarjeta">
        <h2>Estado de verificación</h2>
        <div class="barra-kyc" role="img"
             [attr.aria-label]="d.clientesVerificados + ' verificados, ' + d.clientesPendientes + ' pendientes, ' + d.clientesRechazados + ' rechazados'">
          <span class="seg ok" [style.flex-grow]="d.clientesVerificados"></span>
          <span class="seg pend" [style.flex-grow]="d.clientesPendientes"></span>
          <span class="seg rech" [style.flex-grow]="d.clientesRechazados"></span>
        </div>
        <div class="leyenda">
          <span><i class="ok"></i> Verificados {{ d.clientesVerificados }}</span>
          <span><i class="pend"></i> Pendientes {{ d.clientesPendientes }}</span>
          <span><i class="rech"></i> Rechazados {{ d.clientesRechazados }}</span>
        </div>
      </div>
    }

    <div class="tarjeta">
      <div class="encabezado">
        <h2>Últimos movimientos</h2>
        <a routerLink="/transferencias">Ver todos</a>
      </div>
      <div class="tabla-contenedor">
        <table>
          <thead>
            <tr><th>Fecha</th><th>Tipo</th><th>Origen</th><th>Destino</th><th class="num">Monto</th></tr>
          </thead>
          <tbody>
            @for (t of movimientos(); track t.id) {
              <tr>
                <td>{{ t.creadoEn | date: 'short' }}</td>
                <td><span class="chip" [class]="t.tipo">{{ t.tipo }}</span></td>
                <td class="mono">{{ t.clabeOrigen ?? '—' }}</td>
                <td class="mono">{{ t.clabeDestino ?? '—' }}</td>
                <td class="num">{{ t.monto | currency: 'MXN' : 'symbol-narrow' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="vacio">Sin movimientos</td></tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: `
    .indicadores .tarjeta { display: flex; flex-direction: column; gap: 4px; margin: 0; }
    .indicadores { margin-bottom: 20px; }
    .etiqueta { color: var(--texto-suave); font-size: .85rem; }
    .indicadores strong { font-size: 1.6rem; font-variant-numeric: tabular-nums; }
    .barra-kyc { display: flex; height: 14px; border-radius: 7px; overflow: hidden; gap: 2px; background: var(--borde); }
    .seg { flex-basis: 0; }
    .ok { background: var(--ok); } .pend { background: var(--alerta); } .rech { background: var(--error); }
    .leyenda { display: flex; gap: 18px; flex-wrap: wrap; margin-top: 10px; font-size: .88rem; color: var(--texto-suave); }
    .leyenda i { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 4px; }
  `
})
export class TableroPage implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly datos = signal<Tablero | null>(null);
  protected readonly movimientos = signal<Transaccion[]>([]);
  protected readonly error = signal('');

  ngOnInit(): void {
    forkJoin({ tablero: this.api.tablero(), movs: this.api.movimientos(null, 0, 8) }).subscribe({
      next: ({ tablero, movs }) => {
        this.datos.set(tablero);
        this.movimientos.set(movs.contenido);
      },
      error: (e) => this.error.set(mensajeError(e))
    });
  }

  protected porcentajeVerificados(d: Tablero): number {
    return d.clientesTotal ? (d.clientesVerificados * 100) / d.clientesTotal : 0;
  }
}
