import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { mensajeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  template: `
    <div class="pantalla">
      <form class="tarjeta" [formGroup]="form" (ngSubmit)="entrar()">
        <div class="marca"><span class="logo">V</span> VeriPay</div>
        <p class="subtitulo">Validación de identidad y pagos electrónicos</p>

        @if (error()) {
          <div class="aviso error" role="alert">{{ error() }}</div>
        }

        <div class="campo">
          <label for="username">Usuario</label>
          <input id="username" formControlName="username" autocomplete="username" />
        </div>
        <div class="campo">
          <label for="password">Contraseña</label>
          <input id="password" type="password" formControlName="password" autocomplete="current-password" />
        </div>
        <button class="btn" type="submit" [disabled]="form.invalid || cargando()">
          {{ cargando() ? 'Entrando…' : 'Entrar' }}
        </button>
        <p class="ayuda demo">Demo: admin / Admin123! · analista / Analista123! · auditor / Auditor123!</p>
      </form>
    </div>
  `,
  styles: `
    .pantalla { min-height: 100vh; display: grid; place-items: center; padding: 16px; }
    form { width: 100%; max-width: 380px; }
    .marca { display: flex; align-items: center; gap: 10px; font-weight: 700; font-size: 1.4rem; margin-bottom: 4px; }
    .logo { width: 34px; height: 34px; border-radius: 8px; background: var(--acento); color: #fff; display: grid; place-items: center; }
    .btn { width: 100%; margin-top: 6px; }
    .demo { margin: 16px 0 0; text-align: center; }
  `
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly cargando = signal(false);
  protected readonly error = signal('');

  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required]
  });

  entrar(): void {
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
