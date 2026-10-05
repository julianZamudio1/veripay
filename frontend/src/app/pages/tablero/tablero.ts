import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ApiService, mensajeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ClabePipe } from '../../core/formato';
import { Guilloche } from '../../core/guilloche';
import { Icono } from '../../core/icono';
import { Tablero, Transaccion } from '../../core/models';
import { Movimiento } from '../../core/movimiento';
import { Sello } from '../../core/sello';

@Component({
  selector: 'app-tablero',
  host: { class: 'entrada' },
  imports: [CurrencyPipe, DatePipe, DecimalPipe, RouterLink, Icono, ClabePipe, Guilloche, Movimiento, Sello],
  template: `
    <header class="saludo">
      <h1>Hola, {{ primerNombre() }}</h1>
      <p class="subtitulo">{{ hoy | date: "EEEE d 'de' MMMM" }}</p>
    </header>

    @if (error()) {
      <div class="aviso error" role="alert"><app-icono nombre="alerta" />{{ error() }}</div>
    }

    <div class="composicion" [attr.aria-busy]="!datos()">
      <section class="saldo" aria-labelledby="t-saldo">
        <app-guilloche class="textura" variante="banda" [densidad]="5" />
        <h2 id="t-saldo">Saldo administrado</h2>
        @if (datos(); as d) {
          <p class="cifra num">{{ entero(d.saldoTotal) | currency: 'MXN' : 'symbol-narrow' : '1.0-0' }}<span class="centavos">{{ centavos(d.saldoTotal) }}</span></p>
          <dl class="datos-saldo">
            <div><dt>Cuentas activas</dt><dd class="num">{{ d.cuentas }}</dd></div>
            <div><dt>Operaciones hoy</dt><dd class="num">{{ d.transaccionesHoy | number }}</dd></div>
            <div><dt>Volumen hoy</dt><dd class="num">{{ d.volumenHoy | currency: 'MXN' : 'symbol-narrow' }}</dd></div>
          </dl>
        } @else {
          <span class="esqueleto oscuro" style="width: 55%; height: 52px; margin: 8px 0 26px"></span>
          <span class="esqueleto oscuro" style="width: 80%"></span>
        }
        <div class="microtexto" aria-hidden="true"></div>
      </section>

      <section class="tarjeta identidad" aria-labelledby="t-kyc">
        <div class="encabezado">
          <h2 id="t-kyc">Identidad verificada</h2>
          <app-sello [tamano]="44" />
        </div>
        @if (datos(); as d) {
          <p class="cifra-media num">{{ porcentaje(d) | number: '1.0-0' }}<span>%</span></p>
          <p class="ayuda">{{ d.clientesVerificados }} de {{ d.clientesTotal }} clientes con KYC aprobado</p>
          <div class="barra-kyc" role="img"
               [attr.aria-label]="d.clientesVerificados + ' verificados, ' + d.clientesPendientes + ' pendientes, ' + d.clientesRechazados + ' rechazados'">
            <span class="seg ok" [style.flex-grow]="d.clientesVerificados"></span>
            <span class="seg pend" [style.flex-grow]="d.clientesPendientes"></span>
            <span class="seg rech" [style.flex-grow]="d.clientesRechazados"></span>
          </div>
          <ul class="leyenda">
            <li><i class="ok"></i>Verificados<strong class="num">{{ d.clientesVerificados }}</strong></li>
            <li><i class="pend"></i>Pendientes<strong class="num">{{ d.clientesPendientes }}</strong></li>
            <li><i class="rech"></i>Rechazados<strong class="num">{{ d.clientesRechazados }}</strong></li>
          </ul>
          @if (d.clientesPendientes + d.clientesRechazados > 0) {
            <a routerLink="/clientes" class="btn secundario chico ancho">{{ d.clientesPendientes + d.clientesRechazados === 1 ? 'Resolver 1 pendiente' : 'Resolver ' + (d.clientesPendientes + d.clientesRechazados) + ' pendientes' }}<app-icono nombre="flechaDer" [tamano]="16" /></a>
          }
        } @else {
          <span class="esqueleto" style="width: 40%; height: 40px; margin: 6px 0 12px"></span>
          <span class="esqueleto" style="width: 90%"></span>
        }
      </section>
    </div>

    <section class="tarjeta" aria-labelledby="t-movs">
      <div class="encabezado">
        <h2 id="t-movs">Últimos movimientos</h2>
        <a routerLink="/transferencias" class="enlace">Ver todos<app-icono nombre="flechaDer" [tamano]="14" /></a>
      </div>
      <div class="tabla-contenedor">
        <table>
          <thead>
            <tr><th scope="col">Movimiento</th><th scope="col">Origen</th><th scope="col">Destino</th><th scope="col">Fecha</th><th scope="col" class="num">Monto</th></tr>
          </thead>
          <tbody>
            @if (cargando()) {
              @for (i of [1, 2, 3, 4]; track i) {
                <tr><td colspan="5"><span class="esqueleto"></span></td></tr>
              }
            } @else {
              @for (t of movimientos(); track t.id) {
                <tr>
                  <td><app-movimiento [tipo]="t.tipo" /></td>
                  <td class="mono" [class.secundario-texto]="!t.clabeOrigen">{{ t.clabeOrigen | clabe }}</td>
                  <td class="mono" [class.secundario-texto]="!t.clabeDestino">{{ t.clabeDestino | clabe }}</td>
                  <td class="secundario-texto">{{ t.creadoEn | date: 'd MMM, HH:mm' }}</td>
                  <td class="monto">{{ t.monto | currency: 'MXN' : 'symbol-narrow' }}</td>
                </tr>
              } @empty {
                <tr><td colspan="5" class="vacio"><app-guilloche [densidad]="2" detalle="simple" /><p>Todavía no hay movimientos.</p></td></tr>
              }
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `
    .saludo .subtitulo { margin-bottom: 24px; }
    .saludo .subtitulo::first-letter { text-transform: uppercase; }
    .composicion { display: grid; grid-template-columns: minmax(0, 1.75fr) minmax(280px, 1fr); gap: 20px; margin-bottom: 20px; }

    .saldo { position: relative; overflow: hidden; isolation: isolate; border-radius: var(--r-tarjeta);
             background: var(--tinta); color: var(--tinta-claro); padding: 26px 28px 22px; display: flex; flex-direction: column; }
    .saldo h2 { color: var(--tinta-texto); font-weight: 500; font-size: .92rem; margin: 0; }
    .textura { position: absolute; z-index: -1; inset: auto -4% -10px -4%; height: 150px; color: rgb(196 242 90 / .2); }
    .cifra { font-size: clamp(2.6rem, 5vw, 4rem); font-weight: 600; letter-spacing: -.045em; line-height: 1; margin: 18px 0 28px; }
    .centavos { font-size: .45em; color: var(--tinta-texto); letter-spacing: -.01em; margin-left: 2px; vertical-align: .9em; }
    .datos-saldo { display: grid; grid-template-columns: repeat(3, auto); justify-content: start; gap: 8px 40px; margin: 0 0 22px; }
    .datos-saldo dt { color: var(--tinta-texto); font-size: .8rem; }
    .datos-saldo dd { margin: 2px 0 0; font-weight: 600; font-size: 1.05rem; }
    .saldo .microtexto { color: rgb(196 242 90 / .28); margin-top: auto; }
    .esqueleto.oscuro { background: linear-gradient(90deg, #141917 0%, #1e2522 50%, #141917 100%); background-size: 200% 100%; }

    .identidad { margin: 0; display: flex; flex-direction: column; }
    .identidad .encabezado { align-items: center; }
    .cifra-media { font-size: 2.6rem; font-weight: 600; letter-spacing: -.04em; line-height: 1; margin: 14px 0 6px; }
    .cifra-media span { font-size: .5em; color: var(--texto-suave); margin-left: 2px; }
    .barra-kyc { display: flex; height: 8px; border-radius: 4px; overflow: hidden; gap: 3px; margin: 16px 0 14px; }
    .seg { flex-basis: 0; }
    .ok { background: var(--ok); } .pend { background: var(--alerta); } .rech { background: var(--error); }
    .leyenda { list-style: none; padding: 0; margin: 0 0 18px; display: grid; gap: 6px; font-size: .88rem; color: var(--texto-suave); }
    .leyenda li { display: flex; align-items: center; }
    .leyenda strong { margin-left: auto; color: var(--texto); }
    .leyenda i { width: 8px; height: 8px; border-radius: 2px; margin-right: 9px; }
    .ancho { width: 100%; margin-top: auto; }
    .enlace { display: inline-flex; align-items: center; gap: 5px; text-decoration: none; font-weight: 500; font-size: .9rem; }

    @media (max-width: 1080px) { .composicion { grid-template-columns: 1fr; } }
    @media (max-width: 560px) {
      .saldo { padding: 22px 20px 18px; }
      .textura { height: 54px; bottom: -6px; }
      .datos-saldo { grid-template-columns: 1fr 1fr; gap: 12px 20px; }
    }
  `
})
export class TableroPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  protected readonly hoy = new Date();
  protected readonly datos = signal<Tablero | null>(null);
  protected readonly movimientos = signal<Transaccion[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal('');

  ngOnInit(): void {
    forkJoin({ tablero: this.api.tablero(), movs: this.api.movimientos(null, 0, 7) }).subscribe({
      next: ({ tablero, movs }) => {
        this.datos.set(tablero);
        this.movimientos.set(movs.contenido);
        this.cargando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeError(e));
        this.cargando.set(false);
      }
    });
  }

  protected primerNombre(): string {
    return this.auth.usuario()?.nombre.split(' ')[0] ?? '';
  }

  protected porcentaje(d: Tablero): number {
    return d.clientesTotal ? (d.clientesVerificados * 100) / d.clientesTotal : 0;
  }

  protected entero(monto: number): number {
    return Math.trunc(monto);
  }

  /** ".50" para mostrar los centavos en menor jerarquía junto al entero */
  protected centavos(monto: number): string {
    return '.' + Math.round((monto % 1) * 100).toString().padStart(2, '0');
  }
}
