import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AltaCliente, ApiError, Cliente, Cuenta, EstadoKyc, EventoAuditoria, Pagina, Tablero, Transaccion, Verificacion
} from './models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // ---- Tablero
  tablero(): Observable<Tablero> {
    return this.http.get<Tablero>('api/tablero');
  }

  // ---- Clientes / KYC
  clientes(texto: string, estado: EstadoKyc | '', pagina: number, tamano = 10): Observable<Pagina<Cliente>> {
    let params = new HttpParams().set('pagina', pagina).set('tamano', tamano);
    if (texto) params = params.set('texto', texto);
    if (estado) params = params.set('estado', estado);
    return this.http.get<Pagina<Cliente>>('api/clientes', { params });
  }

  cliente(id: number): Observable<Cliente> {
    return this.http.get<Cliente>(`api/clientes/${id}`);
  }

  altaCliente(datos: AltaCliente): Observable<Cliente> {
    return this.http.post<Cliente>('api/clientes', datos);
  }

  verificar(id: number, identificacion: File, selfie: File): Observable<Verificacion> {
    const form = new FormData();
    form.append('identificacion', identificacion);
    form.append('selfie', selfie);
    return this.http.post<Verificacion>(`api/clientes/${id}/verificaciones`, form);
  }

  verificaciones(id: number): Observable<Verificacion[]> {
    return this.http.get<Verificacion[]>(`api/clientes/${id}/verificaciones`);
  }

  // ---- Cuentas
  cuentas(clienteId?: number): Observable<Cuenta[]> {
    const params = clienteId ? new HttpParams().set('clienteId', clienteId) : undefined;
    return this.http.get<Cuenta[]>('api/cuentas', { params });
  }

  abrirCuenta(clienteId: number): Observable<Cuenta> {
    return this.http.post<Cuenta>(`api/cuentas/cliente/${clienteId}`, null);
  }

  cambiarBloqueo(cuentaId: number, bloquear: boolean): Observable<Cuenta> {
    return this.http.post<Cuenta>(`api/cuentas/${cuentaId}/${bloquear ? 'bloqueo' : 'desbloqueo'}`, null);
  }

  // ---- Transacciones
  depositar(clabe: string, monto: number, concepto: string, clave: string): Observable<Transaccion> {
    return this.http.post<Transaccion>('api/transacciones/depositos', { clabe, monto, concepto },
      { headers: this.idempotencia(clave) });
  }

  retirar(clabe: string, monto: number, concepto: string, clave: string): Observable<Transaccion> {
    return this.http.post<Transaccion>('api/transacciones/retiros', { clabe, monto, concepto },
      { headers: this.idempotencia(clave) });
  }

  transferir(clabeOrigen: string, clabeDestino: string, monto: number, concepto: string,
             clave: string): Observable<Transaccion> {
    return this.http.post<Transaccion>('api/transacciones/transferencias',
      { clabeOrigen, clabeDestino, monto, concepto }, { headers: this.idempotencia(clave) });
  }

  movimientos(cuentaId: number | null, pagina: number, tamano = 15): Observable<Pagina<Transaccion>> {
    let params = new HttpParams().set('pagina', pagina).set('tamano', tamano);
    if (cuentaId) params = params.set('cuentaId', cuentaId);
    return this.http.get<Pagina<Transaccion>>('api/transacciones', { params });
  }

  // ---- Auditoría
  auditoria(pagina: number, tamano = 20): Observable<Pagina<EventoAuditoria>> {
    const params = new HttpParams().set('pagina', pagina).set('tamano', tamano);
    return this.http.get<Pagina<EventoAuditoria>>('api/auditoria', { params });
  }

  private idempotencia(clave: string): HttpHeaders {
    return new HttpHeaders({ 'Idempotency-Key': clave });
  }
}

/** Extrae un mensaje legible de un error HTTP del backend. */
export function mensajeError(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as ApiError | null;
    if (body?.campos && Object.keys(body.campos).length) {
      return Object.entries(body.campos).map(([campo, msg]) => `${campo}: ${msg}`).join(' · ');
    }
    if (body?.mensaje) return body.mensaje;
    if (err.status === 0) return 'No hay conexión con el servidor';
  }
  return 'Ocurrió un error inesperado';
}
