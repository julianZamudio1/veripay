import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import {
  AltaCliente, ApiError, Cliente, Cuenta, EstadoCuenta, EstadoKyc, EventoAuditoria, Pagina, Tablero, Transaccion, Verificacion
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // ---- Tablero
  tablero(): Observable<Tablero> {
    return this.http.get<Tablero>('api/v1/tablero');
  }

  // ---- Clientes / KYC
  clientes(texto: string, estado: EstadoKyc | '', pagina: number, tamano = 10): Observable<Pagina<Cliente>> {
    let params = new HttpParams().set('pagina', pagina).set('tamano', tamano);
    if (texto) params = params.set('texto', texto);
    if (estado) params = params.set('estado', estado);
    return this.http.get<Pagina<Cliente>>('api/v1/clientes', { params });
  }

  cliente(id: number): Observable<Cliente> {
    return this.http.get<Cliente>(`api/v1/clientes/${id}`);
  }

  altaCliente(datos: AltaCliente): Observable<Cliente> {
    return this.http.post<Cliente>('api/v1/clientes', datos);
  }

  verificar(id: number, identificacion: File, selfie: File): Observable<Verificacion> {
    const form = new FormData();
    form.append('identificacion', identificacion);
    form.append('selfie', selfie);
    return this.http.post<Verificacion>(`api/v1/clientes/${id}/verificaciones`, form);
  }

  verificaciones(id: number): Observable<Verificacion[]> {
    return this.http.get<Verificacion[]>(`api/v1/clientes/${id}/verificaciones`);
  }

  // ---- Cuentas
  /** Hasta 100 cuentas (máximo por página del API); suficiente para los selectores de esta demo. */
  cuentas(clienteId?: number): Observable<Cuenta[]> {
    let params = new HttpParams().set('tamano', 100);
    if (clienteId) params = params.set('clienteId', clienteId);
    return this.http.get<Pagina<Cuenta>>('api/v1/cuentas', { params }).pipe(map((p) => p.contenido));
  }

  abrirCuenta(clienteId: number): Observable<Cuenta> {
    return this.http.post<Cuenta>(`api/v1/clientes/${clienteId}/cuentas`, null);
  }

  cambiarEstadoCuenta(cuentaId: number, estado: EstadoCuenta): Observable<Cuenta> {
    return this.http.patch<Cuenta>(`api/v1/cuentas/${cuentaId}`, { estado });
  }

  // ---- Transacciones
  depositar(clabe: string, monto: number, concepto: string, clave: string): Observable<Transaccion> {
    return this.http.post<Transaccion>('api/v1/transacciones/depositos', { clabe, monto, concepto },
      { headers: this.idempotencia(clave) });
  }

  retirar(clabe: string, monto: number, concepto: string, clave: string): Observable<Transaccion> {
    return this.http.post<Transaccion>('api/v1/transacciones/retiros', { clabe, monto, concepto },
      { headers: this.idempotencia(clave) });
  }

  transferir(clabeOrigen: string, clabeDestino: string, monto: number, concepto: string,
             clave: string): Observable<Transaccion> {
    return this.http.post<Transaccion>('api/v1/transacciones/transferencias',
      { clabeOrigen, clabeDestino, monto, concepto }, { headers: this.idempotencia(clave) });
  }

  movimientos(cuentaId: number | null, pagina: number, tamano = 15): Observable<Pagina<Transaccion>> {
    let params = new HttpParams().set('pagina', pagina).set('tamano', tamano);
    if (cuentaId) params = params.set('cuentaId', cuentaId);
    return this.http.get<Pagina<Transaccion>>('api/v1/transacciones', { params });
  }

  // ---- Auditoría
  auditoria(pagina: number, tamano = 20): Observable<Pagina<EventoAuditoria>> {
    const params = new HttpParams().set('pagina', pagina).set('tamano', tamano);
    return this.http.get<Pagina<EventoAuditoria>>('api/v1/auditoria', { params });
  }

  private idempotencia(clave: string): HttpHeaders {
    return new HttpHeaders({ 'Idempotency-Key': clave });
  }
}

/** Extrae un mensaje legible de un error del backend (formato RFC 9457). */
export function mensajeError(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as ApiError | null;
    if (body?.campos && Object.keys(body.campos).length) {
      return Object.entries(body.campos).map(([campo, msg]) => `${campo}: ${msg}`).join(' · ');
    }
    if (body?.detail) return body.detail;
    // Respuestas sin cuerpo RFC 9457: p. ej. un proxy, o un backend con otra versión del API
    switch (err.status) {
      case 0: return 'No hay conexión con el servidor';
      case 401: return 'Usuario o contraseña incorrectos, o tu sesión expiró';
      case 403: return 'No tienes permiso para esta operación';
      case 404: return 'El servidor no reconoce esta operación; verifica que el backend esté actualizado';
    }
    if (err.status >= 500) return 'El servidor tuvo un error; intenta de nuevo en unos minutos';
  }
  return 'Ocurrió un error inesperado';
}
