# Auditoría técnica

**Fecha:** 30 de septiembre de 2026
**Alcance:** backend (Spring Boot), frontend (Angular), configuración y contrato REST.
**Resultado:** 13 fallas y 8 desviaciones del estilo REST corregidas. 50 pruebas de backend y 5 de frontend en verde.

## Cómo se hizo

1. Revisión del código capa por capa: controladores, servicios, repositorios, configuración y seguridad.
2. Ataques manuales contra el WAR en ejecución con `curl`: cuerpos mal formados, tipos inválidos, peticiones sin token, imágenes manipuladas y reutilización de claves de idempotencia.
3. Una prueba automatizada por cada falla, escrita para fallar con el código anterior.
4. Repetición de los mismos ataques contra la versión corregida.

## Fallas encontradas

Severidad: **Crítica** tumba el servicio o compromete dinero; **Alta** rompe una regla de seguridad o de negocio; **Media** da una respuesta incorrecta sin dañar datos; **Baja** afecta la calidad de la respuesta.

| ID | Severidad | Falla | Evidencia | Corrección | Prueba |
|---|---|---|---|---|---|
| A1 | Crítica | Un PNG de 875 KB que declara 30000×30000 px agota la memoria y la JVM termina. | Servidor caído, `hs_err_pid*.log`: *insufficient memory for the Java Runtime Environment*. | `ComparadorPerceptual.leer` lee solo la cabecera, rechaza más de 40 MP y decodifica con submuestreo. | `rechazaBombaDeDescompresionSinDecodificarla` |
| A2 | Alta | Subir el mismo archivo como identificación y selfie aprobaba el KYC con similitud 1.0. | `{"puntaje":1.0000,"aprobada":true}` | `KycService` compara las huellas SHA-256 y responde 422 `IMAGENES_IDENTICAS`. | `mismoArchivoDosVecesNoApruebaElKyc` |
| A3 | Alta | Reutilizar un `Idempotency-Key` en otra operación devolvía la operación vieja con 201. Una transferencia de $999 "salía bien" sin mover dinero. | Transferencia respondió `"tipo":"DEPOSITO"` con HTTP 201. | La clave solo se acepta si coinciden tipo, cuentas, monto y usuario. Si no, 422 `IDEMPOTENCIA_REUTILIZADA`. | `claveReutilizadaEnOtraOperacionSeRechaza`, `claveDeOtroUsuarioSeRechaza` |
| A4 | Alta | El secreto JWT y la contraseña del administrador tenían valores por defecto en `application.yml`, activos en cualquier perfil. Con el repositorio público, cualquiera podía firmar un token de ADMIN en producción. | Revisión de código. | Los valores de prueba viven solo en `application-dev.yml`. Fuera de dev la aplicación no arranca sin `VERIPAY_JWT_SECRETO` y no crea al administrador sin `VERIPAY_ADMIN_PASSWORD`. | Arranque con perfil `produccion`: sin secreto falla y nombra la variable; sin contraseña de admin arranca y `admin/Admin123!` recibe 401 |
| A5 | Alta | El perfil `postgres` cargaba los usuarios demo con contraseñas publicadas en el README. | Revisión de código. | `demo-datos` vale `false` por defecto. Para datos demo sobre PostgreSQL se usa `dev,postgres`. | Revisión de configuración |
| A6 | Media | JSON mal formado, enum inválido (`?estado=FOO`) e id no numérico respondían 500. | Tres respuestas `ERROR_INTERNO` con HTTP 500. | `GlobalExceptionHandler` extiende `ResponseEntityExceptionHandler`: los errores de Spring MVC responden 4xx. | `jsonMalFormadoResponde400`, `enumInvalidoEnQueryResponde400`, `idNoNumericoResponde400` |
| A7 | Media | El 401 del filtro de seguridad llegaba sin cuerpo, con un formato distinto al resto de errores. | `Content-Length: 0` | `RespuestasSeguridad` escribe `application/problem+json` y conserva `WWW-Authenticate: Bearer`. | `sinTokenResponde401ConCuerpoYCabeceraBearer` |
| A8 | Media | Un login con usuario deshabilitado o bloqueado lanzaba `DisabledException` y respondía 500. | Revisión de código: solo se atrapaba `BadCredentialsException`. | El manejador atiende cualquier `AuthenticationException` con 401. | `loginConPasswordIncorrectoResponde401` cubre credenciales inválidas; el caso de usuario deshabilitado no tiene prueba propia |
| A9 | Media | Los intentos de login fallidos no quedaban en la bitácora: el rollback borraba el evento. | Revisión de código. | `AuditoriaService.registrarAislado` usa `REQUIRES_NEW`. | `loginFallidoQuedaEnAuditoria` |
| A10 | Media | Dos aperturas de cuenta simultáneas podían rebasar el límite de 3 cuentas. Dos verificaciones simultáneas terminaban en 500 por el `@Version` del cliente. | Revisión de código: patrón *check-then-act* sin bloqueo. | `ClienteRepository.findByIdParaActualizar` (SELECT … FOR UPDATE) en apertura y KYC. Los conflictos de bloqueo restantes responden 409. | Cubierto por el bloqueo; sin prueba de carrera dedicada |
| A11 | Media | El KYC permitía reintentos ilimitados: alguien podía probar fotos hasta rebasar el umbral. | Revisión de código. | Máximo 3 rechazos por cliente en 24 horas; después, 422 `KYC_INTENTOS_AGOTADOS`. | `tresRechazosBloqueanNuevosIntentos` |
| A12 | Baja | Buscar `_` o `%` en clientes devolvía todos los registros: el texto se usaba como comodín de `LIKE`. | Revisión de código. | `ClienteService.buscar` escapa los comodines y la consulta declara `escape '!'`. | Revisión de código |
| A13 | Baja | `tamano=500` se recortaba a 100 sin avisar. | Revisión de código. | Validación `@Min/@Max`: fuera de rango responde 400. | `tamanoDePaginaFueraDeRangoResponde400` |

## Desviaciones del estilo REST

| ID | Antes | Después |
|---|---|---|
| R1 | Sin versión en la ruta: `/api/clientes` | `/api/v1/clientes`. Un cambio incompatible irá en `/api/v2` sin romper a los clientes actuales. |
| R2 | `POST` respondía 201 sin cabecera `Location` | Cada 201 incluye `Location` con la URL del recurso creado, y esa URL responde a `GET`. |
| R3 | Rutas con verbos: `POST /cuentas/{id}/bloqueo` y `/desbloqueo` | `PATCH /cuentas/{id}` con `{"estado":"BLOQUEADA"}`. Repetir la petición deja el mismo estado. |
| R4 | `POST /cuentas/cliente/{id}` | `POST /clientes/{id}/cuentas`: la cuenta es un subrecurso del cliente. |
| R5 | `PUT /clientes/{id}/contacto` para un cambio parcial | `PATCH /clientes/{id}`. `PUT` implica reemplazar el recurso completo. |
| R6 | Faltaban `GET /cuentas/{id}`, `GET /transacciones/{folio}` y `GET /clientes/{id}/verificaciones/{id}` | Existen, y son el destino de cada `Location`. |
| R7 | `GET /cuentas` devolvía una lista sin paginar | Respuesta paginada como el resto de colecciones, con filtros `clienteId` y `clabe`. |
| R8 | Duplicados (CURP, correo, KYC ya verificado) respondían 422; errores con formato propio | 409 Conflict para conflictos con el estado del recurso. Todos los errores usan RFC 9457 (`application/problem+json`). Un reintento idempotente se identifica con la cabecera `Idempotent-Replayed: true`. |

## Pendientes

Estas observaciones no se corrigieron. Quedan documentadas para decidirlas con el equipo.

| Tema | Riesgo | Propuesta |
|---|---|---|
| Sin límite de intentos de login | Ataque de fuerza bruta a contraseñas | Bucket4j o un filtro con contador por IP y usuario |
| JWT sin revocación | Un token robado vale hasta que expira (120 min) | Tokens de acceso de 15 min con *refresh token* revocable |
| Sin `ETag` / `If-Match` en `PATCH` | Dos analistas pueden sobrescribir el contacto del otro | Exponer `version` como `ETag` y exigir `If-Match` |
| Claves de idempotencia sin caducidad | La tabla crece sin límite | Tarea programada que libere claves de más de 24 h |
| Biometría de demostración | `ComparadorPerceptual` usa hashes perceptuales y no reconoce rostros. Una foto reencuadrada de la INE puede pasar. | Conectar un proveedor con reconocimiento facial y prueba de vida mediante `ComparadorBiometrico` |
| PostgreSQL y WildFly sin probar en esta auditoría | Las pruebas corren sobre H2 y Tomcat embebido | Agregar Testcontainers (PostgreSQL) y un despliegue de prueba en WildFly |
| Karma no termina solo con Edge en Windows | `ng test --watch=false` queda abierto tras reportar resultados | Usar Chrome, o migrar las pruebas a Jest o Vitest |
