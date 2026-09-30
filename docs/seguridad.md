# Seguridad

## Autenticación

1. El usuario envía usuario y contraseña a `POST /api/v1/auth/login`.
2. `AuthService` valida contra la tabla `usuario`. Las contraseñas se guardan con BCrypt.
3. Si coinciden, el servidor firma un JWT con HS256 que dura 120 minutos:

```json
{ "iss": "veripay", "sub": "analista", "nombre": "Ana Analista", "roles": ["ANALISTA"], "iat": 1790788513, "exp": 1790795713 }
```

4. El cliente envía `Authorization: Bearer <token>` en cada petición. Spring Security lo valida como OAuth2 Resource Server y convierte `roles` en autoridades `ROLE_*`.

La API no guarda sesiones (`STATELESS`). Por eso CSRF está desactivado: el navegador no adjunta el token de forma automática, lo hace el interceptor de Angular.

## Autorización

Cada operación de escritura declara sus roles con `@PreAuthorize`. La tabla completa está en [api.md](api.md#permisos-por-rol).

| Rol | Pensado para |
|---|---|
| `ADMIN` | Operación completa, incluido el bloqueo de cuentas |
| `ANALISTA` | Atención a clientes: alta, KYC, cuentas y movimientos |
| `AUDITOR` | Solo lectura, incluida la bitácora |

El frontend oculta las opciones que el rol no permite, pero la regla vive en el backend: una petición directa con otro rol recibe 403.

## Secretos y configuración

| Variable | Obligatoria | Uso |
|---|---|---|
| `VERIPAY_JWT_SECRETO` | Sí, fuera del perfil `dev` | Clave HMAC para firmar tokens. Mínimo 32 caracteres. Sin ella la aplicación no arranca. |
| `VERIPAY_ADMIN_PASSWORD` | No | Contraseña del usuario `admin` inicial. Si falta, el administrador no se crea. |
| `VERIPAY_DB_URL`, `VERIPAY_DB_USER`, `VERIPAY_DB_PASSWORD` | Con perfil `postgres` | Conexión a PostgreSQL |

El perfil `dev` trae valores fijos para trabajar en local (`Admin123!`, un secreto de desarrollo y usuarios demo). Esos valores son públicos porque están en el repositorio: **no actives `dev` en un servidor accesible desde internet.**

Genera un secreto con:

```bash
openssl rand -base64 48
```

## Datos personales

- Las imágenes del KYC se procesan en memoria y se descartan. La base guarda solo su huella SHA-256.
- La bitácora registra quién hizo cada operación, pero no guarda contraseñas ni tokens.
- Un login fallido guarda el nombre de usuario intentado (máximo 50 caracteres) y el tipo de error.

## Protecciones de entrada

| Riesgo | Protección |
|---|---|
| Imagen que declara millones de píxeles para agotar la memoria | Se leen las dimensiones antes de decodificar; más de 40 MP se rechaza. La decodificación usa submuestreo. |
| Archivos grandes | Límite de 5 MB por archivo y 12 MB por petición |
| Inyección SQL | Consultas JPQL con parámetros; nunca se concatena texto del usuario |
| Comodines en búsquedas | `%` y `_` se escapan antes del `LIKE` |
| XSS | Angular escapa todo el texto que muestra; el backend no genera HTML |
| Clickjacking | `X-Frame-Options: SAMEORIGIN` |
| Detalle interno en errores | Los 500 responden un mensaje genérico; la traza queda solo en el log |

## Cabeceras de respuesta

Spring Security agrega en cada respuesta `X-Content-Type-Options: nosniff`, `Cache-Control: no-store`, `X-Frame-Options: SAMEORIGIN` y, en un 401, `WWW-Authenticate: Bearer` (RFC 6750).

## CORS

Solo `http://localhost:4200` (el servidor de desarrollo de Angular) puede llamar al API desde otro origen. Cambia `veripay.cors.origenes` si publicas el frontend en otro dominio. Con el WAR completo no hace falta CORS: frontend y API comparten origen.

## Limitaciones conocidas

Están detalladas en [auditoria.md](auditoria.md#pendientes): no hay límite de intentos de login, los JWT no se pueden revocar antes de que expiren y la comparación biométrica es de demostración.
