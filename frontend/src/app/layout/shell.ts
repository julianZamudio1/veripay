import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { Rol } from '../core/models';

interface Opcion {
  ruta: string;
  etiqueta: string;
  roles?: Rol[];
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell" [class.menu-abierto]="menuAbierto()">
      <aside class="sidebar">
        <div class="marca">
          <span class="logo" aria-hidden="true">V</span>
          <span>VeriPay</span>
        </div>
        <nav>
          @for (op of opcionesVisibles(); track op.ruta) {
            <a [routerLink]="op.ruta" routerLinkActive="activo" (click)="menuAbierto.set(false)">{{ op.etiqueta }}</a>
          }
        </nav>
      </aside>

      <div class="contenido">
        <header class="barra">
          <button class="btn-menu" type="button" (click)="menuAbierto.set(!menuAbierto())" aria-label="Menú">☰</button>
          <div class="usuario">
            <div>
              <strong>{{ auth.usuario()?.nombre }}</strong>
              <small>{{ auth.usuario()?.rol }}</small>
            </div>
            <button class="btn secundario" type="button" (click)="auth.logout()">Salir</button>
          </div>
        </header>
        <main>
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styles: `
    .shell { display: grid; grid-template-columns: 232px 1fr; min-height: 100vh; }
    .sidebar { background: var(--sidebar); color: var(--sidebar-texto); padding: 20px 14px; position: sticky; top: 0; height: 100vh; }
    .marca { display: flex; align-items: center; gap: 10px; font-weight: 700; font-size: 1.15rem; padding: 4px 10px 22px; }
    .logo { width: 30px; height: 30px; border-radius: 8px; background: var(--acento); color: #fff; display: grid; place-items: center; }
    nav { display: flex; flex-direction: column; gap: 2px; }
    nav a { color: inherit; text-decoration: none; padding: 9px 12px; border-radius: 8px; opacity: .8; }
    nav a:hover { background: rgba(255,255,255,.06); opacity: 1; }
    nav a.activo { background: rgba(255,255,255,.1); opacity: 1; font-weight: 600; }
    .contenido { min-width: 0; display: flex; flex-direction: column; }
    .barra { display: flex; align-items: center; justify-content: flex-end; gap: 12px; padding: 12px 28px; border-bottom: 1px solid var(--borde); background: var(--superficie); }
    .usuario { display: flex; align-items: center; gap: 14px; }
    .usuario div { display: flex; flex-direction: column; text-align: right; line-height: 1.2; }
    .usuario small { color: var(--texto-suave); font-size: .75rem; }
    .btn-menu { display: none; margin-right: auto; background: none; border: 0; font-size: 1.4rem; color: var(--texto); cursor: pointer; }
    main { padding: 28px; max-width: 1200px; width: 100%; }
    @media (max-width: 800px) {
      .shell { grid-template-columns: 1fr; }
      .sidebar { position: fixed; z-index: 10; width: 240px; transform: translateX(-100%); transition: transform .2s; }
      .menu-abierto .sidebar { transform: none; }
      .btn-menu { display: block; }
      .barra, main { padding-left: 16px; padding-right: 16px; }
    }
  `
})
export class Shell {
  protected readonly auth = inject(AuthService);
  protected readonly menuAbierto = signal(false);

  private readonly opciones: Opcion[] = [
    { ruta: '/tablero', etiqueta: 'Tablero' },
    { ruta: '/clientes', etiqueta: 'Clientes y KYC' },
    { ruta: '/cuentas', etiqueta: 'Cuentas' },
    { ruta: '/transferencias', etiqueta: 'Transferencias' },
    { ruta: '/auditoria', etiqueta: 'Auditoría', roles: ['ADMIN', 'AUDITOR'] }
  ];

  protected readonly opcionesVisibles = computed(() => {
    return this.opciones.filter((op) => !op.roles || this.auth.tieneRol(...op.roles));
  });
}
