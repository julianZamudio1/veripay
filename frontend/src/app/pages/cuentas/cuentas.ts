import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, mensajeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ClabePipe, EtiquetaPipe, InicialesPipe } from '../../core/formato';
import { Guilloche } from '../../core/guilloche';
import { Icono } from '../../core/icono';
import { Cuenta } from '../../core/models';

@Component({
  host: { class: 'entrada' },
  selector: 'app-cuentas',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink, Icono, EtiquetaPipe, ClabePipe, InicialesPipe, Guilloche],
  template: `
    <div class="encabezado">
      <div>
        <h1>Cuentas</h1>
        <p class="subtitulo">CLABE interbancaria con dígito de control · depósitos y retiros</p>
      </div>
      @if (cuentas().length) {
        <div class="total">
          <span class="ayuda">Saldo total</span>
          <strong class="num">{{ saldoTotal() | currency: 'MXN' : 'symbol-narrow' }}</strong>
        </div>
      }
    </div>

    @if (error()) {
      <div class="aviso error" role="alert"><app-icono nombre="alerta" />{{ error() }}</div>
    }
    @if (exito()) {
      <div class="aviso ok" role="status"><app-icono nombre="checkCirculo" />{{ exito() }}</div>
    }

    @if (auth.tieneRol('ADMIN', 'ANALISTA')) {
      <form class="tarjeta" [formGroup]="form" (ngSubmit)="operar()" novalidate aria-labelledby="t-operacion">
        <h2 id="t-operacion">Depósito o retiro</h2>
        <div class="fila">
          <div class="campo">
            <span class="etiqueta-grupo" id="tipo-etiqueta">Operación</span>
            <div class="segmentado" role="radiogroup" aria-labelledby="tipo-etiqueta">
              <label [class.activo]="form.controls.tipo.value === 'DEPOSITO'">
                <input type="radio" formControlName="tipo" value="DEPOSITO" />Depósito
              </label>
              <label [class.activo]="form.controls.tipo.value === 'RETIRO'">
                <input type="radio" formControlName="tipo" value="RETIRO" />Retiro
              </label>
            </div>
          </div>
          <div class="campo">
            <label for="cuenta">Cuenta</label>
            <select id="cuenta" formControlName="clabe">
              <option value="" disabled>Selecciona una cuenta</option>
              @for (k of cuentas(); track k.id) {
                <option [value]="k.clabe" [disabled]="k.estado === 'BLOQUEADA'">
                  {{ k.titular }} · {{ k.saldo | currency: 'MXN' : 'symbol-narrow' }}{{ k.estado === 'BLOQUEADA' ? ' (bloqueada)' : '' }}
                </option>
              }
            </select>
          </div>
          <div class="campo">
            <label for="monto">Monto</label>
            <div class="con-prefijo">
              <span aria-hidden="true">$</span>
              <input id="monto" type="number" min="0.01" step="0.01" formControlName="monto" class="num" placeholder="0.00" />
            </div>
          </div>
          <div class="campo">
            <label for="concepto">Concepto</label>
            <input id="concepto" formControlName="concepto" maxlength="140" placeholder="Opcional" />
          </div>
          <button class="btn" type="submit" [disabled]="form.invalid || procesando()">
            {{ procesando() ? 'Aplicando…' : (form.controls.tipo.value === 'DEPOSITO' ? 'Depositar' : 'Retirar') }}
          </button>
        </div>
      </form>
    }

    <section class="tarjeta" aria-labelledby="t-cuentas">
      <h2 id="t-cuentas" class="sr-only">Lista de cuentas</h2>
      <div class="tabla-contenedor">
        <table>
          <thead>
            <tr><th scope="col">Titular</th><th scope="col">CLABE</th><th scope="col">Estado</th><th scope="col" class="num">Saldo</th><th scope="col">Apertura</th>
              @if (auth.tieneRol('ADMIN')) { <th scope="col"><span class="sr-only">Acciones</span></th> }
            </tr>
          </thead>
          <tbody>
            @for (k of cuentas(); track k.id) {
              <tr>
                <td>
                  <div class="celda-nombre">
                    <span class="avatar" aria-hidden="true">{{ k.titular | iniciales }}</span>
                    <a [routerLink]="['/clientes', k.clienteId]">{{ k.titular }}</a>
                  </div>
                </td>
                <td class="mono">{{ k.clabe | clabe }}</td>
                <td><span class="chip" [class]="k.estado">{{ k.estado | etiqueta }}</span></td>
                <td class="monto">{{ k.saldo | currency: 'MXN' : 'symbol-narrow' }}</td>
                <td class="secundario-texto">{{ k.creadoEn | date: 'd MMM y' }}</td>
                @if (auth.tieneRol('ADMIN')) {
                  <td class="num">
                    <button class="btn chico secundario" type="button" [class.bloquear]="k.estado === 'ACTIVA'"
                            (click)="alternarBloqueo(k)" [attr.aria-label]="(k.estado === 'ACTIVA' ? 'Bloquear' : 'Desbloquear') + ' la cuenta de ' + k.titular">
                      <app-icono [nombre]="k.estado === 'ACTIVA' ? 'candado' : 'candadoAbierto'" [tamano]="16" />
                      {{ k.estado === 'ACTIVA' ? 'Bloquear' : 'Desbloquear' }}
                    </button>
                  </td>
                }
              </tr>
            } @empty {
              <tr><td colspan="6" class="vacio">
                <app-guilloche [densidad]="2" detalle="simple" />
                <p>Aún no hay cuentas. Se abren desde el detalle de un cliente con identidad verificada.</p>
                <a class="btn secundario chico" routerLink="/clientes">Ir a clientes</a>
              </td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `
    .bloquear:hover { background: var(--error-fondo); color: var(--error); border-color: color-mix(in srgb, var(--error) 35%, transparent); }
    .total { text-align: right; display: flex; flex-direction: column; }
    .total strong { font-size: 1.4rem; font-weight: 600; }
    .fila { display: grid; grid-template-columns: auto 1.6fr 1fr 1.3fr auto; gap: 14px; align-items: end; }
    .fila .campo { margin: 0; }
    .etiqueta-grupo { font-size: .85rem; font-weight: 500; }
    .segmentado { display: inline-flex; padding: 3px; gap: 3px; border-radius: var(--r-control); background: var(--superficie-2); border: 1px solid var(--borde); }
    .segmentado label { display: flex; align-items: center; min-height: 32px; padding: 0 14px; border-radius: 6px; cursor: pointer; font-weight: 500;
                        color: var(--texto-suave); transition: background-color var(--transicion), color var(--transicion); }
    .segmentado label.activo { background: var(--superficie); color: var(--texto); box-shadow: var(--sombra); }
    .segmentado label:focus-within { box-shadow: var(--anillo); }
    .segmentado input { position: absolute; opacity: 0; pointer-events: none; }
    @media (max-width: 1000px) { .fila { grid-template-columns: 1fr 1fr; } }
    @media (max-width: 560px) { .fila { grid-template-columns: 1fr; } .total { text-align: left; margin-bottom: 16px; } }
  `
})
export class CuentasPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly cuentas = signal<Cuenta[]>([]);
  protected readonly saldoTotal = computed(() => this.cuentas().reduce((s, k) => s + k.saldo, 0));
  protected readonly procesando = signal(false);
  protected readonly error = signal('');
  protected readonly exito = signal('');
  private claveIdempotencia = crypto.randomUUID();

  protected readonly form = inject(FormBuilder).nonNullable.group({
    tipo: ['DEPOSITO' as 'DEPOSITO' | 'RETIRO'],
    clabe: ['', Validators.required],
    monto: [null as number | null, [Validators.required, Validators.min(0.01), Validators.max(50000)]],
    concepto: ['']
  });

  constructor() {
    // Si cambia cualquier dato, es una operación distinta y necesita otra clave
    this.form.valueChanges.subscribe(() => (this.claveIdempotencia = crypto.randomUUID()));
  }

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.api.cuentas().subscribe({
      next: (c) => this.cuentas.set(c),
      error: (e) => this.error.set(mensajeError(e))
    });
  }

  operar(): void {
    const { tipo, clabe, monto, concepto } = this.form.getRawValue();
    this.procesando.set(true);
    this.error.set('');
    this.exito.set('');
    const peticion = tipo === 'DEPOSITO'
      ? this.api.depositar(clabe, monto!, concepto, this.claveIdempotencia)
      : this.api.retirar(clabe, monto!, concepto, this.claveIdempotencia);
    peticion.subscribe({
      next: (t) => {
        this.exito.set(`${tipo === 'DEPOSITO' ? 'Depósito' : 'Retiro'} aplicado · folio ${t.folio}`);
        this.claveIdempotencia = crypto.randomUUID();
        this.form.patchValue({ monto: null, concepto: '' });
        this.form.markAsUntouched();
        this.procesando.set(false);
        this.cargar();
      },
      error: (e) => {
        // Se conserva la misma clave: si el usuario reintenta, el servidor no duplica la operación
        this.error.set(mensajeError(e));
        this.procesando.set(false);
      }
    });
  }

  alternarBloqueo(k: Cuenta): void {
    this.api.cambiarEstadoCuenta(k.id, k.estado === 'ACTIVA' ? 'BLOQUEADA' : 'ACTIVA').subscribe({
      next: (actualizada) => this.cuentas.update((lista) => lista.map((c) => (c.id === actualizada.id ? actualizada : c))),
      error: (e) => this.error.set(mensajeError(e))
    });
  }
}
