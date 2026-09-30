import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService, mensajeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Cuenta, Pagina, Transaccion } from '../../core/models';

@Component({
  selector: 'app-transferencias',
  imports: [CurrencyPipe, DatePipe, FormsModule, ReactiveFormsModule],
  template: `
    <h1>Transferencias</h1>
    <p class="subtitulo">Cada envío lleva una <span class="mono">Idempotency-Key</span>: si se reintenta, no se cobra dos veces.</p>

    @if (error()) {
      <div class="aviso error" role="alert">{{ error() }}</div>
    }
    @if (exito()) {
      <div class="aviso ok" role="status">{{ exito() }}</div>
    }

    @if (auth.tieneRol('ADMIN', 'ANALISTA')) {
      <form class="tarjeta" [formGroup]="form" (ngSubmit)="transferir()">
        <h2>Nueva transferencia</h2>
        <div class="dos-columnas">
          <div class="campo">
            <label for="origen">Cuenta origen</label>
            <select id="origen" formControlName="clabeOrigen">
              <option value="" disabled>Selecciona…</option>
              @for (k of cuentasActivas(); track k.id) {
                <option [value]="k.clabe">{{ k.titular }} · {{ k.clabe }} · {{ k.saldo | currency: 'MXN' : 'symbol-narrow' }}</option>
              }
            </select>
          </div>
          <div class="campo">
            <label for="destino">CLABE destino</label>
            <input id="destino" formControlName="clabeDestino" class="mono" maxlength="18" inputmode="numeric" list="clabes" />
            <datalist id="clabes">
              @for (k of cuentasActivas(); track k.id) {
                <option [value]="k.clabe">{{ k.titular }}</option>
              }
            </datalist>
            @if (form.controls.clabeDestino.touched && form.controls.clabeDestino.invalid) {
              <span class="ayuda-error">La CLABE debe tener 18 dígitos</span>
            }
          </div>
          <div class="campo">
            <label for="monto">Monto (MXN)</label>
            <input id="monto" type="number" min="0.01" max="50000" step="0.01" formControlName="monto" />
            <span class="ayuda">Límite por operación: $50,000.00</span>
          </div>
          <div class="campo">
            <label for="concepto">Concepto</label>
            <input id="concepto" formControlName="concepto" maxlength="140" />
          </div>
        </div>
        <button class="btn" type="submit" [disabled]="form.invalid || procesando()">
          {{ procesando() ? 'Enviando…' : 'Transferir' }}
        </button>
      </form>
    }

    <div class="tarjeta">
      <div class="encabezado">
        <h2>Movimientos</h2>
        <select [(ngModel)]="filtroCuenta" (ngModelChange)="cargarMovimientos(0)" aria-label="Filtrar por cuenta" class="filtro">
          <option [ngValue]="null">Todas las cuentas</option>
          @for (k of cuentas(); track k.id) {
            <option [ngValue]="k.id">{{ k.titular }} · {{ k.clabe }}</option>
          }
        </select>
      </div>
      <div class="tabla-contenedor">
        <table>
          <thead>
            <tr><th>Fecha</th><th>Folio</th><th>Tipo</th><th>Origen</th><th>Destino</th><th>Concepto</th><th class="num">Monto</th></tr>
          </thead>
          <tbody>
            @for (t of movimientos()?.contenido ?? []; track t.id) {
              <tr>
                <td>{{ t.creadoEn | date: 'short' }}</td>
                <td class="mono" [title]="t.folio">{{ t.folio.substring(0, 8) }}</td>
                <td><span class="chip" [class]="t.tipo">{{ t.tipo }}</span></td>
                <td class="mono">{{ t.clabeOrigen ?? '—' }}</td>
                <td class="mono">{{ t.clabeDestino ?? '—' }}</td>
                <td>{{ t.concepto ?? '' }}</td>
                <td class="num">{{ t.monto | currency: 'MXN' : 'symbol-narrow' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="vacio">Sin movimientos</td></tr>
            }
          </tbody>
        </table>
      </div>
      @if (movimientos(); as p) {
        @if (p.totalPaginas > 1) {
          <div class="paginador">
            <button class="btn secundario chico" [disabled]="p.pagina === 0" (click)="cargarMovimientos(p.pagina - 1)">Anterior</button>
            <span>Página {{ p.pagina + 1 }} de {{ p.totalPaginas }}</span>
            <button class="btn secundario chico" [disabled]="p.pagina + 1 >= p.totalPaginas" (click)="cargarMovimientos(p.pagina + 1)">Siguiente</button>
          </div>
        }
      }
    </div>
  `,
  styles: `.filtro { max-width: 360px; }`
})
export class TransferenciasPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly cuentas = signal<Cuenta[]>([]);
  protected readonly cuentasActivas = signal<Cuenta[]>([]);
  protected readonly movimientos = signal<Pagina<Transaccion> | null>(null);
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
      next: (c) => {
        this.cuentas.set(c);
        this.cuentasActivas.set(c.filter((k) => k.estado === 'ACTIVA'));
      },
      error: (e) => this.error.set(mensajeError(e))
    });
  }

  cargarMovimientos(pagina: number): void {
    this.api.movimientos(this.filtroCuenta, pagina).subscribe({
      next: (p) => this.movimientos.set(p),
      error: (e) => this.error.set(mensajeError(e))
    });
  }

  transferir(): void {
    const { clabeOrigen, clabeDestino, monto, concepto } = this.form.getRawValue();
    this.procesando.set(true);
    this.error.set('');
    this.exito.set('');
    this.api.transferir(clabeOrigen, clabeDestino, monto!, concepto, this.claveIdempotencia).subscribe({
      next: (t) => {
        this.exito.set(`Transferencia aplicada · folio ${t.folio}`);
        this.form.reset({ clabeOrigen, clabeDestino: '', monto: null, concepto: '' });
        this.procesando.set(false);
        this.cargarCuentas();
        this.cargarMovimientos(0);
      },
      error: (e) => {
        this.error.set(mensajeError(e));
        this.procesando.set(false);
      }
    });
  }
}
