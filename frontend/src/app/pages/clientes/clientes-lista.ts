import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { ApiService, mensajeError } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { EtiquetaPipe, InicialesPipe } from '../../core/formato';
import { Guilloche } from '../../core/guilloche';
import { Icono } from '../../core/icono';
import { Cliente, EstadoKyc, Pagina } from '../../core/models';

@Component({
  host: { class: 'entrada' },
  selector: 'app-clientes-lista',
  imports: [DatePipe, FormsModule, RouterLink, Icono, EtiquetaPipe, InicialesPipe, Guilloche],
  template: `
    <div class="encabezado">
      <div>
        <h1>Clientes</h1>
        <p class="subtitulo">Alta, validación de CURP y verificación de identidad</p>
      </div>
      @if (auth.tieneRol('ADMIN', 'ANALISTA')) {
        <a class="btn" routerLink="/clientes/nuevo"><app-icono nombre="mas" />Nuevo cliente</a>
      }
    </div>

    <section class="tarjeta" aria-labelledby="titulo-lista">
      <h2 id="titulo-lista" class="sr-only">Lista de clientes</h2>
      <div class="filtros" role="search">
        <div class="con-icono">
          <app-icono nombre="buscar" />
          <label for="buscar" class="sr-only">Buscar clientes</label>
          <input id="buscar" type="search" placeholder="Nombre, CURP o correo"
                 [ngModel]="texto" (ngModelChange)="busqueda$.next($event)" />
        </div>
        <label for="estado" class="sr-only">Estado de verificación</label>
        <select id="estado" [(ngModel)]="estado" (ngModelChange)="cargar(0)">
          <option value="">Todos los estados</option>
          <option value="PENDIENTE">Pendiente</option>
          <option value="VERIFICADO">Verificado</option>
          <option value="RECHAZADO">Rechazado</option>
        </select>
      </div>

      @if (error()) {
        <div class="aviso error" role="alert"><app-icono nombre="alerta" />{{ error() }}</div>
      }

      <div class="tabla-contenedor" [attr.aria-busy]="cargando()">
        <table>
          <thead>
            <tr><th scope="col">Cliente</th><th scope="col">CURP</th><th scope="col">Verificación</th><th scope="col">Alta</th></tr>
          </thead>
          <tbody>
            @if (cargando() && !pagina()) {
              @for (i of [1, 2, 3, 4, 5]; track i) {
                <tr><td colspan="4"><span class="esqueleto"></span></td></tr>
              }
            } @else {
              @for (c of pagina()?.contenido ?? []; track c.id) {
                <tr>
                  <td>
                    <div class="celda-nombre">
                      <span class="avatar" aria-hidden="true">{{ c.nombreCompleto | iniciales }}</span>
                      <div>
                        <a [routerLink]="['/clientes', c.id]">{{ c.nombreCompleto }}</a>
                        <div class="ayuda">{{ c.email }}</div>
                      </div>
                    </div>
                  </td>
                  <td class="mono">{{ c.curp }}</td>
                  <td><span class="chip" [class]="c.estadoKyc">{{ c.estadoKyc | etiqueta }}</span></td>
                  <td class="secundario-texto">{{ c.creadoEn | date: 'd MMM y' }}</td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="4" class="vacio">
                    <app-guilloche [densidad]="2" detalle="simple" />
                    @if (texto || estado) {
                      <p>Ningún cliente coincide con la búsqueda.</p>
                      <button class="btn secundario chico" type="button" (click)="limpiar()">Quitar filtros</button>
                    } @else {
                      <p>Aún no hay clientes registrados.</p>
                      @if (auth.tieneRol('ADMIN', 'ANALISTA')) {
                        <a class="btn chico" routerLink="/clientes/nuevo"><app-icono nombre="mas" [tamano]="16" />Dar de alta el primero</a>
                      }
                    }
                  </td>
                </tr>
              }
            }
          </tbody>
        </table>
      </div>

      @if (pagina(); as p) {
        @if (p.totalPaginas > 1) {
          <nav class="paginador" aria-label="Paginación">
            <span>{{ p.totalElementos }} clientes · página {{ p.pagina + 1 }} de {{ p.totalPaginas }}</span>
            <button class="btn secundario chico" [disabled]="p.pagina === 0" (click)="cargar(p.pagina - 1)">
              <app-icono nombre="flechaIzq" [tamano]="16" />Anterior
            </button>
            <button class="btn secundario chico" [disabled]="p.pagina + 1 >= p.totalPaginas" (click)="cargar(p.pagina + 1)">
              Siguiente<app-icono nombre="flechaDer" [tamano]="16" />
            </button>
          </nav>
        }
      }
    </section>
  `
})
export class ClientesLista implements OnInit {
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);

  protected readonly pagina = signal<Pagina<Cliente> | null>(null);
  protected readonly cargando = signal(true);
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
    this.cargando.set(true);
    this.api.clientes(this.texto, this.estado, pagina).subscribe({
      next: (p) => {
        this.pagina.set(p);
        this.error.set('');
        this.cargando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeError(e));
        this.cargando.set(false);
      }
    });
  }

  protected limpiar(): void {
    this.texto = '';
    this.estado = '';
    this.cargar(0);
  }
}
