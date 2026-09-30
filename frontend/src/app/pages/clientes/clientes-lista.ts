import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApiService, mensajeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { Cliente, EstadoKyc, Pagina } from '../../core/models';

@Component({
  selector: 'app-clientes-lista',
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <div class="encabezado">
      <div>
        <h1>Clientes</h1>
        <p class="subtitulo">Alta, validación de CURP y verificación biométrica (KYC)</p>
      </div>
      @if (auth.tieneRol('ADMIN', 'ANALISTA')) {
        <a class="btn" routerLink="/clientes/nuevo">+ Nuevo cliente</a>
      }
    </div>

    <div class="tarjeta">
      <div class="filtros">
        <input type="search" placeholder="Buscar por nombre, CURP o correo" aria-label="Buscar"
               [ngModel]="texto" (ngModelChange)="busqueda$.next($event)" />
        <select [(ngModel)]="estado" (ngModelChange)="cargar(0)" aria-label="Estado KYC">
          <option value="">Todos los estados</option>
          <option value="PENDIENTE">Pendiente</option>
          <option value="VERIFICADO">Verificado</option>
          <option value="RECHAZADO">Rechazado</option>
        </select>
      </div>

      @if (error()) {
        <div class="aviso error">{{ error() }}</div>
      }

      <div class="tabla-contenedor">
        <table>
          <thead>
            <tr><th>Nombre</th><th>CURP</th><th>Correo</th><th>KYC</th><th>Alta</th></tr>
          </thead>
          <tbody>
            @for (c of pagina()?.contenido ?? []; track c.id) {
              <tr>
                <td><a [routerLink]="['/clientes', c.id]">{{ c.nombreCompleto }}</a></td>
                <td class="mono">{{ c.curp }}</td>
                <td>{{ c.email }}</td>
                <td><span class="chip" [class]="c.estadoKyc">{{ c.estadoKyc }}</span></td>
                <td>{{ c.creadoEn | date: 'mediumDate' }}</td>
              </tr>
            } @empty {
              <tr><td colspan="5" class="vacio">No hay clientes que coincidan</td></tr>
            }
          </tbody>
        </table>
      </div>

      @if (pagina(); as p) {
        @if (p.totalPaginas > 1) {
          <div class="paginador">
            <button class="btn secundario chico" [disabled]="p.pagina === 0" (click)="cargar(p.pagina - 1)">Anterior</button>
            <span>Página {{ p.pagina + 1 }} de {{ p.totalPaginas }}</span>
            <button class="btn secundario chico" [disabled]="p.pagina + 1 >= p.totalPaginas" (click)="cargar(p.pagina + 1)">Siguiente</button>
          </div>
        }
      }
    </div>
  `
})
export class ClientesLista implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly pagina = signal<Pagina<Cliente> | null>(null);
  protected readonly error = signal('');
  protected readonly busqueda$ = new Subject<string>();
  protected texto = '';
  protected estado: EstadoKyc | '' = '';

  constructor() {
    this.busqueda$.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed()).subscribe((t) => {
      this.texto = t;
      this.cargar(0);
    });
  }

  ngOnInit(): void {
    this.cargar(0);
  }

  cargar(pagina: number): void {
    this.api.clientes(this.texto, this.estado, pagina).subscribe({
      next: (p) => {
        this.pagina.set(p);
        this.error.set('');
      },
      error: (e) => this.error.set(mensajeError(e))
    });
  }
}
