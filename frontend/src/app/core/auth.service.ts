import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { Rol, Sesion } from './models';

const CLAVE = 'veripay.sesion';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly sesion = signal<Sesion | null>(this.leer());

  readonly usuario = computed(() => this.sesion());
  readonly autenticado = computed(() => {
    const s = this.sesion();
    return !!s && new Date(s.expira).getTime() > Date.now();
  });

  get token(): string | null {
    return this.autenticado() ? this.sesion()!.token : null;
  }

  tieneRol(...roles: Rol[]): boolean {
    const s = this.sesion();
    return !!s && roles.includes(s.rol);
  }

  login(username: string, password: string): Observable<Sesion> {
    return this.http.post<Sesion>('api/auth/login', { username, password }).pipe(
      tap((s) => {
        this.sesion.set(s);
        this.guardar(s);
      })
    );
  }

  logout(): void {
    this.sesion.set(null);
    this.guardar(null);
    this.router.navigate(['/login']);
  }

  // El almacenamiento puede no estar disponible (modo privado); la sesión sigue en memoria.
  private leer(): Sesion | null {
    try {
      const raw = localStorage.getItem(CLAVE);
      return raw ? (JSON.parse(raw) as Sesion) : null;
    } catch {
      return null;
    }
  }

  private guardar(s: Sesion | null): void {
    try {
      if (s) {
        localStorage.setItem(CLAVE, JSON.stringify(s));
      } else {
        localStorage.removeItem(CLAVE);
      }
    } catch {
      /* sin almacenamiento persistente */
    }
  }
}
