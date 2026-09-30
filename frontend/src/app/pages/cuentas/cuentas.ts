import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService, mensajeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Cuenta } from '../../core/models';

@Component({
  selector: 'app-cuentas',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink],
  template: `
    <h1>Cuentas</h1>
    <p class="subtitulo">CLABE interbancaria con dígito de control · depósitos y retiros</p>

    @if (error()) {
      <div class="aviso error" role="alert">{{ error() }}</div>
    }
    @if (exito()) {
      <div class="aviso ok" role="status">{{ exito() }}</div>
    }

    @if (auth.tieneRol('ADMIN', 'ANALISTA')) {
      <form class="tarjeta operacion" [formGroup]="form" (ngSubmit)="operar()">
        <h2>Depósito / retiro</h2>
        <div class="fila">
          <div class="campo">
            <label for="tipo">Operación</label>
            <select id="tipo" formControlName="tipo">
              <option value="DEPOSITO">Depósito</option>
              <option value="RETIRO">Retiro</option>
            </select>
          </div>
          <div class="campo">
            <label for="cuenta">Cuenta</label>
            <select id="cuenta" formControlName="clabe">
              <option value="" disabled>Selecciona…</option>
              @for (k of cuentas(); track k.id) {
                <option [value]="k.clabe" [disabled]="k.estado === 'BLOQUEADA'">{{ k.titular }} · {{ k.clabe }}</option>
              }
            </select>
          </div>
          <div class="campo">
            <label for="monto">Monto (MXN)</label>
            <input id="monto" type="number" min="0.01" step="0.01" formControlName="monto" />
          </div>
          <div class="campo">
            <label for="concepto">Concepto</label>
            <input id="concepto" formControlName="concepto" maxlength="140" />
          </div>
          <button class="btn" type="submit" [disabled]="form.invalid || procesando()">Aplicar</button>
        </div>
      </form>
    }

    <div class="tarjeta">
      <div class="tabla-contenedor">
        <table>
          <thead>
            <tr><th>CLABE</th><th>Titular</th><th>Estado</th><th class="num">Saldo</th><th>Apertura</th>
              @if (auth.tieneRol('ADMIN')) { <th></th> }
            </tr>
          </thead>
          <tbody>
            @for (k of cuentas(); track k.id) {
              <tr>
                <td class="mono">{{ k.clabe }}</td>
                <td><a [routerLink]="['/clientes', k.clienteId]">{{ k.titular }}</a></td>
                <td><span class="chip" [class]="k.estado">{{ k.estado }}</span></td>
                <td class="num">{{ k.saldo | currency: 'MXN' : 'symbol-narrow' }}</td>
                <td>{{ k.creadoEn | date: 'mediumDate' }}</td>
                @if (auth.tieneRol('ADMIN')) {
                  <td>
                    <button class="btn chico" type="button" [class.secundario]="k.estado === 'BLOQUEADA'"
                            [class.peligro]="k.estado === 'ACTIVA'" (click)="alternarBloqueo(k)">
                      {{ k.estado === 'ACTIVA' ? 'Bloquear' : 'Desbloquear' }}
                    </button>
                  </td>
                }
              </tr>
            } @empty {
              <tr><td colspan="6" class="vacio">Aún no hay cuentas. Se abren desde el detalle de un cliente verificado.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </div>
  `,
  styles: `
    .fila { display: grid; grid-template-columns: 140px 2fr 1fr 1.4fr auto; gap: 12px; align-items: end; }
    .fila .campo { margin: 0; }
    @media (max-width: 900px) { .fila { grid-template-columns: 1fr; } }
  `
})
export class CuentasPage implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly cuentas = signal<Cuenta[]>([]);
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
