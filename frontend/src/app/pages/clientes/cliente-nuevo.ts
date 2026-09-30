import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, mensajeError } from '../../core/api.service';
import { curpValidator, fechaDeCurp } from '../../core/validadores';

@Component({
  selector: 'app-cliente-nuevo',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <a routerLink="/clientes">← Clientes</a>
    <h1>Nuevo cliente</h1>
    <p class="subtitulo">La CURP se valida con el algoritmo de RENAPO; la fecha de nacimiento se toma de ella.</p>

    <form class="tarjeta" [formGroup]="form" (ngSubmit)="guardar()">
      @if (error()) {
        <div class="aviso error" role="alert">{{ error() }}</div>
      }

      <div class="dos-columnas">
        <div class="campo">
          <label for="curp">CURP *</label>
          <input id="curp" formControlName="curp" maxlength="18" class="mono" (input)="mayusculas('curp')" />
          @if (form.controls.curp.touched && form.controls.curp.errors?.['curp']) {
            <span class="ayuda-error">CURP inválida: revisa formato y dígito verificador</span>
          }
        </div>
        <div class="campo">
          <label for="rfc">RFC</label>
          <input id="rfc" formControlName="rfc" maxlength="13" class="mono" (input)="mayusculas('rfc')" />
          @if (form.controls.rfc.touched && form.controls.rfc.invalid) {
            <span class="ayuda-error">RFC de persona física: 4 letras, 6 dígitos y 3 de homoclave</span>
          }
        </div>
        <div class="campo">
          <label for="nombre">Nombre(s) *</label>
          <input id="nombre" formControlName="nombre" />
        </div>
        <div class="campo">
          <label for="paterno">Apellido paterno *</label>
          <input id="paterno" formControlName="apellidoPaterno" />
        </div>
        <div class="campo">
          <label for="materno">Apellido materno</label>
          <input id="materno" formControlName="apellidoMaterno" />
        </div>
        <div class="campo">
          <label for="fecha">Fecha de nacimiento *</label>
          <input id="fecha" type="date" formControlName="fechaNacimiento" />
          <span class="ayuda">Se llena automáticamente con la CURP</span>
        </div>
        <div class="campo">
          <label for="email">Correo electrónico *</label>
          <input id="email" type="email" formControlName="email" />
          @if (form.controls.email.touched && form.controls.email.invalid) {
            <span class="ayuda-error">Correo inválido</span>
          }
        </div>
        <div class="campo">
          <label for="tel">Teléfono (10 dígitos)</label>
          <input id="tel" formControlName="telefono" inputmode="numeric" maxlength="10" />
          @if (form.controls.telefono.touched && form.controls.telefono.invalid) {
            <span class="ayuda-error">Deben ser 10 dígitos</span>
          }
        </div>
      </div>

      <button class="btn" type="submit" [disabled]="form.invalid || guardando()">
        {{ guardando() ? 'Guardando…' : 'Dar de alta' }}
      </button>
    </form>
  `
})
export class ClienteNuevo {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  protected readonly guardando = signal(false);
  protected readonly error = signal('');

  protected readonly form = inject(FormBuilder).nonNullable.group({
    curp: ['', [Validators.required, curpValidator]],
    rfc: ['', Validators.pattern(/^[A-ZÑ&]{4}\d{6}[A-Z\d]{3}$/)],
    nombre: ['', [Validators.required, Validators.maxLength(80)]],
    apellidoPaterno: ['', [Validators.required, Validators.maxLength(80)]],
    apellidoMaterno: ['', Validators.maxLength(80)],
    fechaNacimiento: ['', Validators.required],
    email: ['', [Validators.required, Validators.email]],
    telefono: ['', Validators.pattern(/^\d{10}$/)]
  });

  constructor() {
    this.form.controls.curp.valueChanges.pipe(takeUntilDestroyed()).subscribe((curp) => {
      const fecha = curp.length >= 17 ? fechaDeCurp(curp) : null;
      if (fecha) this.form.controls.fechaNacimiento.setValue(fecha);
    });
  }

  protected mayusculas(campo: 'curp' | 'rfc'): void {
    const control = this.form.controls[campo];
    control.setValue(control.value.toUpperCase(), { emitEvent: campo === 'curp' });
  }

  guardar(): void {
    this.guardando.set(true);
    this.error.set('');
    const v = this.form.getRawValue();
    this.api.altaCliente({
      ...v,
      rfc: v.rfc || null,
      apellidoMaterno: v.apellidoMaterno || null,
      telefono: v.telefono || null
    }).subscribe({
      next: (c) => this.router.navigate(['/clientes', c.id]),
      error: (e) => {
        this.error.set(mensajeError(e));
        this.guardando.set(false);
      }
    });
  }
}
