import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { mensajeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Guilloche } from '../../core/guilloche';
import { Icono } from '../../core/icono';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, Icono, Guilloche],
  template: `
    <div class="pantalla">
      <section class="marca-panel" aria-label="VeriPay">
        <div class="logo-linea"><span class="logo"><app-guilloche [densidad]="2" detalle="simple" /></span>VeriPay</div>

        <app-guilloche class="roseta" [densidad]="7" [animar]="true" />

        <div class="mensaje">
          <h1>Identidad verificada,<br />dinero en movimiento.</h1>
          <p>Alta de clientes con CURP, verificación biométrica y transferencias con CLABE en un solo lugar.</p>
        </div>
        <div class="microtexto" aria-hidden="true"></div>
        <app-guilloche class="banda" variante="banda" [densidad]="4" />
      </section>

      <main class="formulario">
        <form [formGroup]="form" (ngSubmit)="entrar()" novalidate aria-labelledby="t-login">
          <h2 id="t-login">Inicia sesión</h2>
          <p class="subtitulo">Usa tu usuario de VeriPay o una cuenta de demostración.</p>

          @if (error()) {
            <div class="aviso error" role="alert"><app-icono nombre="alerta" />{{ error() }}</div>
          }

          <div class="campo">
            <label for="username">Usuario</label>
            <input id="username" formControlName="username" autocomplete="username" autocapitalize="none" spellcheck="false" />
          </div>
          <div class="campo">
            <label for="password">Contraseña</label>
            <div class="con-estado">
              <input id="password" [type]="verPassword() ? 'text' : 'password'" formControlName="password" autocomplete="current-password" />
              <button type="button" class="estado-campo ver" (click)="verPassword.set(!verPassword())"
                      [attr.aria-label]="verPassword() ? 'Ocultar contraseña' : 'Mostrar contraseña'" [attr.aria-pressed]="verPassword()">
                <app-icono [nombre]="verPassword() ? 'ocultar' : 'ver'" />
              </button>
            </div>
          </div>
          <button class="btn ancho" type="submit" [disabled]="cargando()">
            @if (cargando()) { Entrando… } @else { Entrar <app-icono nombre="flechaDer" /> }
          </button>

          <div class="demo">
            <p class="ayuda">Cuentas de demostración</p>
            <div class="acciones">
              @for (u of demo; track u.usuario) {
                <button type="button" class="btn secundario chico" (click)="usar(u.usuario, u.password)">{{ u.rol }}</button>
              }
            </div>
          </div>
        </form>
      </main>
    </div>
  `,
  styles: `
    .pantalla { min-height: 100dvh; display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(420px, 1fr); background: var(--fondo); }

    .marca-panel { position: relative; overflow: hidden; background: var(--tinta); color: var(--tinta-claro);
                   display: flex; flex-direction: column; justify-content: space-between; padding: 40px 48px; isolation: isolate; }
    /* Grano fino sobre el panel fijo (no se desplaza, no cuesta repintado) */
    .marca-panel::after { content: ""; position: absolute; inset: 0; z-index: -1; opacity: .07; pointer-events: none;
      background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='160' height='160'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.9' numOctaves='2' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E"); }
    .logo-linea { display: flex; align-items: center; gap: 11px; font-weight: 600; font-size: 1.1rem; letter-spacing: -.02em; }
    .logo { width: 34px; height: 34px; border-radius: 50%; background: var(--tinta-2); color: var(--lima); padding: 3px;
            display: grid; place-items: center; box-shadow: inset 0 0 0 1px rgb(196 242 90 / .25); }
    .logo app-guilloche { width: 100%; height: 100%; }
    .roseta { position: absolute; z-index: -1; width: min(78vh, 720px); aspect-ratio: 1; right: -14%; top: 50%;
              transform: translateY(-58%); color: rgb(196 242 90 / .55); }
    .mensaje { max-width: 30ch; }
    .mensaje h1 { font-size: clamp(2rem, 3.4vw, 3.1rem); line-height: 1.06; letter-spacing: -.035em; margin-bottom: 14px; }
    .mensaje p { color: var(--tinta-texto); margin: 0; font-size: 1rem; max-width: 42ch; }
    .microtexto { color: rgb(196 242 90 / .3); margin-top: 28px; }
    .banda { position: absolute; z-index: -1; left: 0; right: 0; bottom: 0; height: 90px; color: rgb(196 242 90 / .16); }

    .formulario { display: grid; place-items: center; padding: 40px 24px; }
    form { width: 100%; max-width: 380px; }
    h2 { font-size: 1.6rem; letter-spacing: -.03em; margin-bottom: 6px; }
    .subtitulo { margin-bottom: 26px; }
    .ancho { width: 100%; margin-top: 4px; }
    .ver { right: 4px; border: 0; background: transparent; color: var(--texto-suave); cursor: pointer;
           width: 34px; height: 34px; display: grid; place-items: center; border-radius: 8px; transition: color var(--t), background-color var(--t); }
    .ver:hover { color: var(--texto); background: var(--superficie-2); }
    .con-estado > input { padding-right: 46px; }
    .demo { margin-top: 28px; padding-top: 20px; border-top: 1px solid var(--borde); }
    .demo p { margin: 0 0 10px; }

    @media (max-width: 900px) {
      .pantalla { grid-template-columns: 1fr; }
      .marca-panel { min-height: 260px; padding: 24px 20px; }
      .roseta { width: 360px; right: -120px; top: 45%; }
      .mensaje h1 { font-size: 1.75rem; }
      .mensaje p, .microtexto { display: none; }
      .formulario { padding: 28px 16px 40px; }
    }
  `
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly cargando = signal(false);
  protected readonly error = signal('');
  protected readonly verPassword = signal(false);
  protected readonly demo = [
    { rol: 'Administrador', usuario: 'admin', password: 'Admin123!' },
    { rol: 'Analista', usuario: 'analista', password: 'Analista123!' },
    { rol: 'Auditor', usuario: 'auditor', password: 'Auditor123!' }
  ];

  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required]
  });

  protected usar(usuario: string, password: string): void {
    this.form.setValue({ username: usuario, password });
    this.error.set('');
  }

  entrar(): void {
    if (this.form.invalid) {
      this.error.set('Escribe usuario y contraseña');
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.cargando.set(true);
    this.error.set('');
    this.auth.login(username, password).subscribe({
      next: () => this.router.navigate(['/tablero']),
      error: (e) => {
        this.error.set(mensajeError(e));
        this.cargando.set(false);
      }
    });
  }
}
