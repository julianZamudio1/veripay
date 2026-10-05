import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService, mensajeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { ClabePipe } from '../../core/formato';
import { Guilloche } from '../../core/guilloche';
import { Movimiento } from '../../core/movimiento';
import { Icono } from '../../core/icono';
import { Cuenta, Pagina, Transaccion } from '../../core/models';

@Component({
  host: { class: 'entrada' },
  selector: 'app-transferencias',
  imports: [CurrencyPipe, DatePipe, FormsModule, ReactiveFormsModule, Icono, ClabePipe, Guilloche, Movimiento],
  template: `
    <h1>Transferencias</h1>
    <p class="subtitulo">Cada envío lleva una <span class="mono">Idempotency-Key</span>: si la red falla y se reintenta, no se cobra dos veces.</p>

    @if (error()) {
      <div class="aviso error" role="alert"><app-icono nombre="alerta" />{{ error() }}</div>
    }
    @if (exito()) {
      <div class="aviso ok" role="status"><app-icono nombre="checkCirculo" />{{ exito() }}</div>
    }

    @if (auth.tieneRol('ADMIN', 'ANALISTA')) {
      <section class="tarjeta envio" aria-labelledby="t-nueva">
        <h2 id="t-nueva">Nueva transferencia</h2>

        @if (!confirmando()) {
          <form [formGroup]="form" (ngSubmit)="revisar()" novalidate>
            <div class="rejilla-envio">
              <div class="campo">
                <label for="origen">Cuenta origen</label>
                <select id="origen" formControlName="clabeOrigen">
                  <option value="" disabled>Selecciona una cuenta</option>
                  @for (k of cuentasActivas(); track k.id) {
                    <option [value]="k.clabe">{{ k.titular }} · {{ k.saldo | currency: 'MXN' : 'symbol-narrow' }}</option>
                  }
                </select>
                @if (origen(); as o) {
                  <span class="ayuda mono">{{ o.clabe | clabe }}</span>
                } @else if (mostrarError('clabeOrigen')) {
                  <span class="ayuda-error">Elige la cuenta que envía</span>
                }
              </div>

              <div class="flecha" aria-hidden="true"><app-icono nombre="flechaDer" [tamano]="20" /></div>

              <div class="campo">
                <label for="destino">CLABE destino</label>
                <input id="destino" formControlName="clabeDestino" class="mono" maxlength="18" inputmode="numeric"
                       placeholder="18 dígitos" list="clabes" autocomplete="off" aria-describedby="destino-ayuda" />
                <datalist id="clabes">
                  @for (k of cuentasActivas(); track k.id) { <option [value]="k.clabe">{{ k.titular }}</option> }
                </datalist>
                <span id="destino-ayuda" [class]="mostrarError('clabeDestino') ? 'ayuda-error' : 'ayuda'" aria-live="polite">
                  @if (mostrarError('clabeDestino')) {
                    La CLABE tiene 18 dígitos
                  } @else if (destino(); as d) {
                    Titular: <strong>{{ d.titular }}</strong>
                  } @else if (form.controls.clabeDestino.value.length === 18) {
                    CLABE externa o no registrada
                  } @else {
                    Escribe o elige de la lista
                  }
                </span>
              </div>
            </div>

            <div class="rejilla-envio segunda">
              <div class="campo">
                <label for="monto">Monto</label>
                <div class="con-prefijo">
                  <span aria-hidden="true">$</span>
                  <input id="monto" type="number" min="0.01" max="50000" step="0.01" formControlName="monto"
                         class="num" placeholder="0.00" aria-describedby="monto-ayuda" />
                </div>
                <span id="monto-ayuda" [class]="mostrarError('monto') ? 'ayuda-error' : 'ayuda'">
                  @if (mostrarError('monto')) { Entre $0.01 y $50,000.00 } @else { MXN · límite de $50,000.00 por operación }
                </span>
              </div>
              <div class="campo concepto">
                <label for="concepto">Concepto</label>
                <input id="concepto" formControlName="concepto" maxlength="140" placeholder="Opcional, p. ej. Renta octubre" />
              </div>
            </div>

            <div class="acciones pie">
              <button class="btn" type="submit">Revisar transferencia<app-icono nombre="flechaDer" /></button>
            </div>
          </form>
        } @else {
          <div class="resumen" role="group" aria-labelledby="t-confirmar">
            <p id="t-confirmar" class="ayuda">Revisa los datos antes de enviar. Esta operación no se puede deshacer.</p>
            <p class="monto-grande num">{{ form.controls.monto.value | currency: 'MXN' : 'symbol-narrow' }}</p>
            <div class="ruta">
              <div>
                <span class="ayuda">De</span>
                <strong>{{ origen()?.titular }}</strong>
                <span class="mono ayuda">{{ form.controls.clabeOrigen.value | clabe }}</span>
              </div>
              <app-icono nombre="flechaDer" [tamano]="22" />
              <div>
                <span class="ayuda">Para</span>
                <strong>{{ destino()?.titular ?? 'Cuenta no registrada' }}</strong>
                <span class="mono ayuda">{{ form.controls.clabeDestino.value | clabe }}</span>
              </div>
            </div>
            @if (form.controls.concepto.value) {
              <p class="ayuda">Concepto: <strong>{{ form.controls.concepto.value }}</strong></p>
            }
            <div class="acciones pie">
              <button class="btn secundario" type="button" (click)="confirmando.set(false)" [disabled]="procesando()">Editar</button>
              <button class="btn" type="button" (click)="transferir()" [disabled]="procesando()">
                @if (procesando()) { Enviando… } @else { <app-icono nombre="check" />Confirmar y enviar }
              </button>
            </div>
          </div>
        }
      </section>
    }

    <section class="tarjeta" aria-labelledby="t-movs">
      <div class="encabezado">
        <h2 id="t-movs">Movimientos</h2>
        <label for="filtro" class="sr-only">Filtrar por cuenta</label>
        <select id="filtro" [(ngModel)]="filtroCuenta" (ngModelChange)="cargarMovimientos(0)" class="filtro">
          <option [ngValue]="null">Todas las cuentas</option>
          @for (k of cuentas(); track k.id) {
            <option [ngValue]="k.id">{{ k.titular }} · {{ k.clabe | clabe }}</option>
          }
        </select>
      </div>
      <div class="tabla-contenedor">
        <table>
          <thead>
            <tr><th scope="col">Fecha</th><th scope="col">Tipo</th><th scope="col">Origen</th><th scope="col">Destino</th>
              <th scope="col">Concepto</th><th scope="col">Folio</th><th scope="col" class="num">Monto</th></tr>
          </thead>
          <tbody>
            @for (t of movimientos()?.contenido ?? []; track t.id) {
              <tr>
                <td class="secundario-texto">{{ t.creadoEn | date: 'd MMM, HH:mm' }}</td>
                <td><app-movimiento [tipo]="t.tipo" /></td>
                <td class="mono" [class.secundario-texto]="!t.clabeOrigen">{{ t.clabeOrigen | clabe }}</td>
                <td class="mono" [class.secundario-texto]="!t.clabeDestino">{{ t.clabeDestino | clabe }}</td>
                <td>{{ t.concepto ?? '' }}</td>
                <td class="mono secundario-texto" [title]="t.folio">{{ t.folio.substring(0, 8) }}</td>
                <td class="monto">{{ t.monto | currency: 'MXN' : 'symbol-narrow' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="vacio"><app-guilloche [densidad]="2" detalle="simple" /><p>Sin movimientos para esta cuenta.</p></td></tr>
            }
          </tbody>
        </table>
      </div>
      @if (movimientos(); as p) {
        @if (p.totalPaginas > 1) {
          <nav class="paginador" aria-label="Paginación">
            <span>Página {{ p.pagina + 1 }} de {{ p.totalPaginas }}</span>
            <button class="btn secundario chico" [disabled]="p.pagina === 0" (click)="cargarMovimientos(p.pagina - 1)">
              <app-icono nombre="flechaIzq" [tamano]="16" />Anterior</button>
            <button class="btn secundario chico" [disabled]="p.pagina + 1 >= p.totalPaginas" (click)="cargarMovimientos(p.pagina + 1)">
              Siguiente<app-icono nombre="flechaDer" [tamano]="16" /></button>
          </nav>
        }
      }
    </section>
  `,
  styles: `
    .envio { max-width: 880px; }
    .rejilla-envio { display: grid; grid-template-columns: 1fr auto 1fr; gap: 0 16px; align-items: start; }
    .rejilla-envio.segunda { grid-template-columns: 1fr 1.4fr; }
    .flecha { padding-top: 34px; color: var(--texto-suave); }
    .pie { justify-content: flex-end; border-top: 1px solid var(--borde); padding-top: 16px; margin-top: 4px; }
    .filtro { max-width: 340px; }
    .resumen { text-align: center; }
    .monto-grande { font-size: 2.2rem; font-weight: 600; margin: 4px 0 18px; letter-spacing: -.02em; }
    .ruta { display: flex; align-items: center; justify-content: center; gap: 20px; margin-bottom: 16px; color: var(--texto-suave); }
    .ruta > div { display: flex; flex-direction: column; gap: 2px; min-width: 200px; padding: 14px; border-radius: var(--r-control); background: var(--superficie-2); }
    .ruta strong { color: var(--texto); }
    .resumen .pie { justify-content: center; }
    @media (max-width: 720px) {
      .rejilla-envio, .rejilla-envio.segunda { grid-template-columns: 1fr; }
      .flecha { display: none; }
      .ruta { flex-direction: column; }
    }
  `
})
export class TransferenciasPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly cuentas = signal<Cuenta[]>([]);
  protected readonly cuentasActivas = computed(() => this.cuentas().filter((k) => k.estado === 'ACTIVA'));
  protected readonly movimientos = signal<Pagina<Transaccion> | null>(null);
  protected readonly confirmando = signal(false);
  protected readonly procesando = signal(false);
  protected readonly error = signal('');
  protected readonly exito = signal('');
  protected filtroCuenta: number | null = null;
  private claveIdempotencia = crypto.randomUUID();

  protected readonly form = inject(FormBuilder).nonNullable.group({
    clabeOrigen: ['', Validators.required],
    clabeDestino: ['', [Validators.required, Validators.pattern(/^\d{18}$/)]],
    monto: [null as number | null, [Validators.required, Validators.min(0.01), Validators.max(50000)]],
    concepto: ['']
  });

  private readonly valores = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  protected readonly origen = computed(() => this.cuentas().find((k) => k.clabe === this.valores().clabeOrigen));
  protected readonly destino = computed(() => this.cuentas().find((k) => k.clabe === this.valores().clabeDestino));

  constructor() {
    // Si cambia cualquier dato, es una transferencia distinta y necesita otra clave
    this.form.valueChanges.subscribe(() => (this.claveIdempotencia = crypto.randomUUID()));
  }

  ngOnInit(): void {
    this.cargarCuentas();
    this.cargarMovimientos(0);
  }

  private cargarCuentas(): void {
    this.api.cuentas().subscribe({
      next: (c) => this.cuentas.set(c),
      error: (e) => this.error.set(mensajeError(e))
    });
  }

  cargarMovimientos(pagina: number): void {
    this.api.movimientos(this.filtroCuenta, pagina).subscribe({
      next: (p) => this.movimientos.set(p),
      error: (e) => this.error.set(mensajeError(e))
    });
  }

  protected mostrarError(campo: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[campo];
    return c.invalid && c.touched;
  }

  protected revisar(): void {
    this.exito.set('');
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.form.controls.clabeOrigen.value === this.form.controls.clabeDestino.value) {
      this.error.set('La cuenta origen y destino no pueden ser la misma');
      return;
    }
    this.error.set('');
    this.confirmando.set(true);
  }

  protected transferir(): void {
    const { clabeOrigen, clabeDestino, monto, concepto } = this.form.getRawValue();
    this.procesando.set(true);
    this.error.set('');
    this.api.transferir(clabeOrigen, clabeDestino, monto!, concepto, this.claveIdempotencia).subscribe({
      next: (t) => {
        this.exito.set(`Transferencia enviada · folio ${t.folio}`);
        this.form.reset({ clabeOrigen, clabeDestino: '', monto: null, concepto: '' });
        this.confirmando.set(false);
        this.procesando.set(false);
        this.cargarCuentas();
        this.cargarMovimientos(0);
      },
      error: (e) => {
        // Se conserva la clave: si el usuario reintenta, el servidor no duplica el cargo
        this.error.set(mensajeError(e));
        this.procesando.set(false);
      }
    });
  }
}
