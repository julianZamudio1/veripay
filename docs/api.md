# Referencia del API REST

La especificación OpenAPI completa está en `/veripay/v3/api-docs` y la interfaz interactiva en `/veripay/swagger-ui.html`. Este documento explica las convenciones y da un ejemplo por operación.

## Convenciones

| Tema | Regla |
|---|---|
| URL base | `http://localhost:8080/veripay/api/v1` |
| Versión | En la ruta (`/v1`). Los cambios incompatibles van en una versión nueva. |
| Formato | JSON en UTF-8. La verificación biométrica recibe `multipart/form-data`. |
| Nombres | Recursos en plural y en español (`clientes`, `cuentas`, `transacciones`). Campos en camelCase. |
| Fechas | ISO 8601. Instantes en UTC (`2026-09-30T17:16:39Z`); fechas sin hora como `1985-03-12`. |
| Dinero | Número decimal con 2 decimales, en MXN. |
| Autenticación | `Authorization: Bearer <token>` en todas las rutas menos el login. |

### Verbos y códigos de estado

| Verbo | Uso | Éxito |
|---|---|---|
| `GET` | Consultar un recurso o una colección | 200 |
| `POST` | Crear un recurso | 201 + cabecera `Location` |
| `PATCH` | Modificar parte de un recurso | 200 con el recurso actualizado |

| Código | Significado en este API |
|---|---|
| 400 | Petición mal formada: JSON inválido, tipo incorrecto, parámetro fuera de rango, imagen ilegible |
| 401 | Falta el token, expiró, o las credenciales del login no coinciden |
| 403 | El rol del usuario no permite la operación |
| 404 | El recurso no existe |
| 405 | La ruta existe pero no acepta ese verbo (la respuesta incluye `Allow`) |
| 409 | Conflicto con el estado actual: duplicado, KYC ya verificado, operación concurrente |
| 413 | Archivo mayor a 5 MB |
| 415 | `Content-Type` no soportado |
| 422 | La petición es válida pero viola una regla de negocio (saldo, límites, KYC) |
| 500 | Error no previsto. El servidor lo registra en el log con la ruta |

### Errores (RFC 9457)

Todos los errores responden con `Content-Type: application/problem+json`:

```json
{
  "type": "urn:veripay:problema:saldo-insuficiente",
  "title": "Unprocessable Entity",
  "status": 422,
  "detail": "Saldo insuficiente en la cuenta 646180189451851375 (disponible 2500.00)",
  "instance": "/veripay/api/v1/transacciones/retiros",
  "codigo": "SALDO_INSUFICIENTE",
  "timestamp": "2026-09-30T18:10:02.114Z"
}
```

Usa `codigo` para decidir qué hacer en el cliente: es estable. `detail` es texto para mostrar al usuario y puede cambiar.

Los errores de validación agregan `campos`:

```json
{
  "status": 400,
  "codigo": "VALIDACION",
  "detail": "La solicitud contiene datos inválidos",
  "campos": { "curp": "CURP inválida (formato o dígito verificador)" }
}
```

### Paginación

Las colecciones aceptan `pagina` (desde 0) y `tamano` (1 a 100) y responden:

```json
{ "contenido": [ ... ], "pagina": 0, "tamano": 20, "totalElementos": 57, "totalPaginas": 3 }
```

Un `tamano` fuera de rango responde 400.

### Idempotencia

Los `POST` de transacciones aceptan la cabecera `Idempotency-Key` (máximo 64 caracteres). Genera un UUID por operación y reenvíalo si la red falla:

| Situación | Respuesta |
|---|---|
| Clave nueva | 201; el servidor aplica la operación |
| Misma clave, mismos datos y mismo usuario | 201 con la transacción original y `Idempotent-Replayed: true`. No se mueve dinero otra vez. |
| Misma clave con otros datos o de otro usuario | 422 `IDEMPOTENCIA_REUTILIZADA` |
| Dos peticiones simultáneas con la misma clave | Una gana; la otra recibe 409 `IDEMPOTENCIA_EN_CURSO` |

## Autenticación

### `POST /auth/login`

```http
POST /veripay/api/v1/auth/login
Content-Type: application/json

{"username": "analista", "password": "Analista123!"}
```

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expira": "2026-09-30T20:15:13Z",
  "username": "analista",
  "nombre": "Ana Analista",
  "rol": "ANALISTA"
}
```

El token dura 120 minutos. Un login fallido responde 401 `CREDENCIALES` y queda en la bitácora como `LOGIN_FALLIDO`.

## Permisos por rol

| Operación | ADMIN | ANALISTA | AUDITOR |
|---|:-:|:-:|:-:|
| Consultar clientes, cuentas, transacciones y tablero | ✓ | ✓ | ✓ |
| Alta y edición de clientes, verificación KYC | ✓ | ✓ | |
| Abrir cuentas, depósitos, retiros, transferencias | ✓ | ✓ | |
| Bloquear y desbloquear cuentas | ✓ | | |
| Consultar la bitácora de auditoría | ✓ | | ✓ |

## Clientes

| Verbo y ruta | Descripción |
|---|---|
| `GET /clientes?texto=&estado=&pagina=&tamano=` | Busca por nombre, apellido, CURP o correo. `estado`: `PENDIENTE`, `VERIFICADO`, `RECHAZADO`. |
| `GET /clientes/{id}` | Un cliente |
| `POST /clientes` | Alta. El cliente queda en KYC `PENDIENTE`. |
| `PATCH /clientes/{id}` | Cambia correo y teléfono |
| `POST /clientes/{id}/verificaciones` | Verificación biométrica |
| `GET /clientes/{id}/verificaciones` | Historial de verificaciones, de la más reciente a la más antigua |
| `GET /clientes/{id}/verificaciones/{verificacionId}` | Una verificación |

### Alta

```http
POST /veripay/api/v1/clientes
Authorization: Bearer <token>
Content-Type: application/json

{
  "curp": "TOVE880130MPLRLL05",
  "rfc": null,
  "nombre": "Elena",
  "apellidoPaterno": "Torres",
  "apellidoMaterno": "Vega",
  "fechaNacimiento": "1988-01-30",
  "email": "elena.torres@correo.mx",
  "telefono": "2229876543"
}
```

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/veripay/api/v1/clientes/4
```

```json
{
  "id": 4,
  "curp": "TOVE880130MPLRLL05",
  "nombreCompleto": "Elena Torres Vega",
  "fechaNacimiento": "1988-01-30",
  "email": "elena.torres@correo.mx",
  "estadoKyc": "PENDIENTE",
  "creadoEn": "2026-09-30T17:15:13Z"
}
```

Errores posibles: 400 `VALIDACION`, 409 `CURP_DUPLICADA`, 409 `EMAIL_DUPLICADO`, 422 `CURP_FECHA`, 422 `MENOR_DE_EDAD`.

### Cambio de contacto

```http
PATCH /veripay/api/v1/clientes/4
Content-Type: application/json

{"email": "elena@nuevo.mx", "telefono": "2220001111"}
```

### Verificación biométrica

```bash
curl -X POST http://localhost:8080/veripay/api/v1/clientes/4/verificaciones \
  -H "Authorization: Bearer $TOKEN" \
  -F identificacion=@ine.jpg \
  -F selfie=@selfie.jpg
```

```json
{
  "id": 1,
  "puntaje": 0.9375,
  "umbral": 0.80,
  "aprobada": true,
  "huellaIdentificacion": "8d14e287...",
  "huellaSelfie": "622ec23d...",
  "realizadaPor": "analista",
  "creadoEn": "2026-09-30T17:16:39Z"
}
```

Con `aprobada: true` el cliente pasa a `VERIFICADO`; con `false`, a `RECHAZADO`. Errores posibles: 400 `ARGUMENTO_INVALIDO` (imagen ilegible o mayor a 40 MP), 409 `KYC_YA_VERIFICADO`, 422 `IMAGENES_IDENTICAS`, 422 `KYC_INTENTOS_AGOTADOS`.

## Cuentas

| Verbo y ruta | Descripción |
|---|---|
| `GET /cuentas?clienteId=&clabe=&pagina=&tamano=` | Lista paginada. Con `clabe` devuelve 0 o 1 elementos. |
| `GET /cuentas/{id}` | Una cuenta |
| `POST /clientes/{clienteId}/cuentas` | Abre una cuenta con CLABE nueva. Sin cuerpo. |
| `PATCH /cuentas/{id}` | Bloquea o desbloquea |

```json
{
  "id": 4,
  "clabe": "646180534285955584",
  "clienteId": 4,
  "titular": "Elena Torres Vega",
  "saldo": 300.00,
  "moneda": "MXN",
  "estado": "ACTIVA",
  "creadoEn": "2026-09-30T17:16:39Z"
}
```

Bloqueo:

```http
PATCH /veripay/api/v1/cuentas/4
Content-Type: application/json

{"estado": "BLOQUEADA"}
```

Enviar el mismo estado dos veces responde 200 sin cambios ni evento de auditoría. Errores al abrir: 422 `KYC_REQUERIDO`, 422 `LIMITE_CUENTAS`.

## Transacciones

| Verbo y ruta | Descripción |
|---|---|
| `POST /transacciones/depositos` | `{"clabe", "monto", "concepto"}` |
| `POST /transacciones/retiros` | `{"clabe", "monto", "concepto"}` |
| `POST /transacciones/transferencias` | `{"clabeOrigen", "clabeDestino", "monto", "concepto"}` |
| `GET /transacciones?cuentaId=&pagina=&tamano=` | Movimientos, del más reciente al más antiguo |
| `GET /transacciones/{folio}` | Una transacción por su folio (UUID) |
| `GET /tablero` | Indicadores del día (zona horaria de la Ciudad de México) |

```http
POST /veripay/api/v1/transacciones/transferencias
Authorization: Bearer <token>
Idempotency-Key: 4b8f0c9e-2f7a-4f0e-9a51-0d6c1f3e2a77
Content-Type: application/json

{
  "clabeOrigen": "646180975037779865",
  "clabeDestino": "646180534285955584",
  "monto": 300.00,
  "concepto": "Renta"
}
```

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/veripay/api/v1/transacciones/f111f1a4-9425-4758-bab3-039d937ea8dd
```

```json
{
  "id": 6,
  "folio": "f111f1a4-9425-4758-bab3-039d937ea8dd",
  "tipo": "TRANSFERENCIA",
  "clabeOrigen": "646180975037779865",
  "clabeDestino": "646180534285955584",
  "monto": 300.00,
  "concepto": "Renta",
  "estado": "APLICADA",
  "realizadaPor": "analista",
  "creadoEn": "2026-09-30T17:16:41Z"
}
```

Errores posibles: 404 `NO_ENCONTRADO` (CLABE inexistente), 409 `IDEMPOTENCIA_EN_CURSO`, 422 `SALDO_INSUFICIENTE`, `LIMITE_EXCEDIDO`, `MISMA_CUENTA`, `CUENTA_BLOQUEADA`, `KYC_REQUERIDO`, `IDEMPOTENCIA_REUTILIZADA`.

### Tablero

```json
{
  "clientesTotal": 4, "clientesPendientes": 1, "clientesVerificados": 3, "clientesRechazados": 0,
  "cuentas": 3, "saldoTotal": 25700.50, "transaccionesHoy": 5, "volumenHoy": 27950.50
}
```

## Auditoría

`GET /auditoria?pagina=&tamano=` devuelve los eventos del más reciente al más antiguo:

```json
{ "id": 42, "usuario": "analista", "accion": "TRANSFERENCIA", "entidad": "TRANSACCION",
  "entidadId": "f111f1a4-9425-4758-bab3-039d937ea8dd", "detalle": "Monto 300.00", "creadoEn": "..." }
```

## Catálogo de códigos de error

| Código | HTTP | Cuándo ocurre |
|---|---|---|
| `VALIDACION` | 400 | Un campo del cuerpo no cumple su regla; ver `campos` |
| `BAD_REQUEST` | 400 | JSON mal formado, tipo de parámetro incorrecto o parámetro fuera de rango |
| `ARGUMENTO_INVALIDO` | 400 | Imagen ilegible o demasiado grande; `Idempotency-Key` de más de 64 caracteres |
| `NO_AUTENTICADO` | 401 | Falta el token o no es válido |
| `CREDENCIALES` | 401 | Usuario o contraseña incorrectos |
| `ACCESO_DENEGADO` | 403 | El rol no permite la operación |
| `NO_ENCONTRADO` / `NOT_FOUND` | 404 | El recurso o la ruta no existen |
| `CURP_DUPLICADA`, `EMAIL_DUPLICADO` | 409 | Ya existe un cliente con ese dato |
| `KYC_YA_VERIFICADO` | 409 | El cliente ya pasó la verificación |
| `IDEMPOTENCIA_EN_CURSO` | 409 | Otra petición con la misma clave se está procesando |
| `CONFLICTO_CONCURRENCIA` | 409 | Otra operación modificó el recurso al mismo tiempo |
| `DUPLICADO` | 409 | Restricción única de la base de datos |
| `PAYLOAD_TOO_LARGE` | 413 | Un archivo supera 5 MB o la petición 12 MB |
| `CURP_INVALIDA`, `CURP_FECHA`, `MENOR_DE_EDAD` | 422 | Reglas del alta de cliente |
| `IMAGENES_IDENTICAS`, `KYC_INTENTOS_AGOTADOS` | 422 | Reglas de la verificación biométrica |
| `KYC_REQUERIDO`, `LIMITE_CUENTAS` | 422 | Reglas de apertura de cuenta |
| `SALDO_INSUFICIENTE`, `LIMITE_EXCEDIDO`, `MISMA_CUENTA`, `CUENTA_BLOQUEADA`, `IDEMPOTENCIA_REUTILIZADA` | 422 | Reglas de transacciones |
| `ERROR_INTERNO` | 500 | Error no previsto |
