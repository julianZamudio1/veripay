import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService, mensajeError } from '../../core/api.service';
import { Icono } from '../../core/icono';
import { curpValidator, esCurpValida, fechaDeCurp } from '../../core/validadores';

@Component({
  host: { class: 'entrada' },
  selector: 'app-cliente-nuevo',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, Icono],
  template: `
    <a routerLink="/clientes" class="volver"><app-icono nombre="flechaIzq" [tamano]="16" />Clientes</a>
    <h1>Nuevo cliente</h1>
    <p class="subtitulo">El cliente queda pendiente de verificación. Después podrás validar su identidad con una foto.</p>

    <form class="tarjeta formulario" [formGroup]="form" (ngSubmit)="guardar()" novalidate>
      @if (error()) {
        <div class="aviso error" role="alert"><app-icono nombre="alerta" />{{ error() }}</div>
      }

      <fieldset>
        <legend>Identidad</legend>
        <div class="rejilla-campos">
          <div class="campo ancho-2">
            <label for="curp">CURP<span class="requerido" aria-hidden="true">*</span></label>
            <div class="con-estado">
              <input id="curp" formControlName="curp" maxlength="18" class="mono" autocomplete="off"
                     placeholder="GOMA850312HDFRRN09" (input)="mayusculas('curp')"
                     aria-describedby="curp-ayuda" [attr.aria-invalid]="curpInvalida()" />
              @if (estadoCurp() === 'valida') {
                <span class="estado-campo ok"><app-icono nombre="checkCirculo" /></span>
              } @else if (estadoCurp() === 'invalida') {
                <span class="estado-campo error"><app-icono nombre="alerta" /></span>
              }
            </div>
            <span id="curp-ayuda" class="ayuda" [class.ayuda-error]="curpInvalida()" aria-live="polite">
              @switch (estadoCurp()) {
                @case ('valida') { CURP válida · nacimiento {{ form.controls.fechaNacimiento.value | date: 'longDate' : 'UTC' }} }
                @case ('invalida') { Revisa el formato y el dígito verificador }
                @default { {{ curpLongitud() }}/18 caracteres · se valida con el algoritmo de RENAPO }
              }
            </span>
          </div>
          <div class="campo">
            <label for="fecha">Fecha de nacimiento<span class="requerido" aria-hidden="true">*</span></label>
            <input id="fecha" type="date" formControlName="fechaNacimiento" aria-describedby="fecha-ayuda" />
            <span id="fecha-ayuda" class="ayuda">Se toma de la CURP</span>
          </div>
          <div class="campo">
            <label for="nombre">Nombre(s)<span class="requerido" aria-hidden="true">*</span></label>
            <input id="nombre" formControlName="nombre" autocomplete="given-name" />
            @if (mostrarError('nombre')) { <span class="ayuda-error">Escribe el nombre</span> }
          </div>
          <div class="campo">
            <label for="paterno">Apellido paterno<span class="requerido" aria-hidden="true">*</span></label>
            <input id="paterno" formControlName="apellidoPaterno" autocomplete="family-name" />
            @if (mostrarError('apellidoPaterno')) { <span class="ayuda-error">Escribe el apellido paterno</span> }
          </div>
          <div class="campo">
            <label for="materno">Apellido materno</label>
            <input id="materno" formControlName="apellidoMaterno" />
          </div>
          <div class="campo">
            <label for="rfc">RFC</label>
            <input id="rfc" formControlName="rfc" maxlength="13" class="mono" placeholder="GOMA850312AB1"
                   autocomplete="off" (input)="mayusculas('rfc')" />
            @if (mostrarError('rfc')) {
              <span class="ayuda-error">4 letras, 6 dígitos y 3 de homoclave</span>
            } @else {
              <span class="ayuda">Opcional</span>
            }
          </div>
        </div>
      </fieldset>

      <hr class="separador" />

      <fieldset>
        <legend>Contacto</legend>
        <div class="rejilla-campos">
          <div class="campo ancho-2">
            <label for="email">Correo electrónico<span class="requerido" aria-hidden="true">*</span></label>
            <input id="email" type="email" formControlName="email" autocomplete="email" placeholder="nombre@correo.mx" />
            @if (mostrarError('email')) { <span class="ayuda-error">Escribe un correo válido</span> }
          </div>
          <div class="campo">
            <label for="tel">Teléfono</label>
            <input id="tel" type="tel" formControlName="telefono" inputmode="numeric" maxlength="10"
                   autocomplete="tel-national" placeholder="5512345678" />
            @if (mostrarError('telefono')) {
              <span class="ayuda-error">Deben ser 10 dígitos</span>
            } @else {
              <span class="ayuda">10 dígitos, opcional</span>
            }
          </div>
        </div>
      </fieldset>

      <div class="acciones pie">
        <a class="btn secundario" routerLink="/clientes">Cancelar</a>
        <button class="btn" type="submit" [disabled]="guardando()">
          @if (guardando()) { Guardando… } @else { <app-icono nombre="check" />Dar de alta }
        </button>
      </div>
    </form>
  `,
  styles: `
    .formulario { max-width: 880px; padding: 24px; }
    .rejilla-campos { display: grid; gap: 0 16px; grid-template-columns: repeat(3, minmax(0, 1fr)); }
    .ancho-2 { grid-column: span 2; }
    .pie { justify-content: flex-end; border-top: 1px solid var(--borde); padding-top: 20px; margin-top: 4px; }
    @media (max-width: 720px) {
      .rejilla-campos { grid-template-columns: 1fr; }
      .ancho-2 { grid-column: auto; }
    }
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

  private readonly curp = toSignal(this.form.controls.curp.valueChanges, { initialValue: '' });
  protected readonly curpLongitud = computed(() => this.curp().length);
  /** Solo se juzga la CURP completa: mientras se escribe no se muestra error. */
  protected readonly estadoCurp = computed<'vacia' | 'valida' | 'invalida'>(() => {
    const c = this.curp();
    if (c.length < 18) return 'vacia';
    return esCurpValida(c) ? 'valida' : 'invalida';
  });
  protected readonly curpInvalida = computed(() => this.estadoCurp() === 'invalida');

  constructor() {
    this.form.controls.curp.valueChanges.pipe(takeUntilDestroyed()).subscribe((curp) => {
      const fecha = curp.length >= 17 ? fechaDeCurp(curp) : null;
      if (fecha) this.form.controls.fechaNacimiento.setValue(fecha);
    });
  }

  protected mostrarError(campo: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[campo];
    return c.invalid && c.touched;
  }

  protected mayusculas(campo: 'curp' | 'rfc'): void {
    const control = this.form.controls[campo];
    control.setValue(control.value.toUpperCase(), { emitEvent: campo === 'curp' });
  }

  guardar(): void {
    if (this.form.invalid) {
      // Muestra todos los errores y lleva el foco al primer campo con problema
      this.form.markAllAsTouched();
      document.querySelector<HTMLElement>('form .ng-invalid:not(form)')?.focus();
      return;
    }
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
