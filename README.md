# ms-andesstay-report

Reporteria de AndesStay (puerto **8087**): KPIs calculados al momento, **sin base de datos**. Consulta por REST a `ms-andesstay-reservations` (8081) y `ms-andesstay-audit` (8086) propagando el JWT del usuario. Spring Boot 4.1.1 · Java 17 · Resource Server JWT (Microsoft Entra ID).

```
Frontend --> API Gateway --> BFF :8080 --> report :8087 --GET /api/reservations--------------> reservations :8081
                                                         --GET /api/audit/reservations/{id}/timeline--> audit :8086
```

## Ejecutar

```powershell
# Requiere reservations (y opcionalmente audit) corriendo
$env:RESERVATIONS_URL="http://localhost:8081"
$env:AUDIT_URL="http://localhost:8086"
.\mvnw.cmd spring-boot:run
```

| Variable | Default | Propiedad |
|---|---|---|
| `RESERVATIONS_URL` | `http://localhost:8081` | `andesstay.services.reservations.url` |
| `AUDIT_URL` | `http://localhost:8086` | `andesstay.services.audit.url` |

En Docker usar el nombre del contenedor (ej. `RESERVATIONS_URL=http://ms-andesstay-reservations:8081`). Timeouts: conexion 2 s, lectura 10 s (`andesstay.services.connect-timeout` / `read-timeout`). No hay secretos: ver `.env.example`.

## Endpoints

| Metodo | Ruta | Descripcion | Codigos |
|---|---|---|---|
| GET | `/api/report/kpis?range=last7d` | KPIs del rango | 200, 400, 503 |
| GET | `/api/report/top-units?range=last7d&limit=5` | Unidades con mas reservas | 200, 400, 503 |

- `range`: `last24h`, `last7d` (por defecto) o `last30d`, contado hacia atras desde ahora. Otro valor -> **400** con los valores validos.
- `limit`: 1 a 50 (por defecto 5). Fuera de rango -> **400**.

**KPIs** (`service/KpiCalculator`; el rango se aplica sobre `creadaEn` de la reserva):

| Campo | Calculo |
|---|---|
| `totalReservas` | reservas creadas en el rango |
| `reservasPorHora` | cantidad por hora de `creadaEn` (`yyyy-MM-ddTHH:00`, orden cronologico) |
| `reservasPorEstado` | cantidad por estado actual |
| `ocupacionActiva` | reservas en `EN_ESTADIA` ahora (todas, sin filtro de rango) |
| `tiempoCicloPromedioHoras` | promedio de horas entre `RESERVA_CREADA` y el `CAMBIO_ESTADO` a `CHECKOUT`, segun el timeline de audit de cada reserva del rango en `CHECKOUT` |
| `ingresosConfirmados` | suma de `precioTotal` en `CONFIRMADA`, `CHECKIN_PENDIENTE`, `EN_ESTADIA` y `CHECKOUT` (sin `CANCELADA` ni `CREADA`) |
| `avisos` | KPIs que no se pudieron calcular y por que |

**top-units**: agrupa las reservas del rango por `unidadId` (las que no tienen unidad no cuentan), de mayor a menor: `{range, desde, hasta, limit, unidades:[{unidadId, alojamientoNombre, reservas}]}`.

**Fallas**:
- reservations no responde (caido, timeout o 5xx): **503** `"ms-andesstay-reservations no responde; no se pueden calcular los reportes. Intente mas tarde"`.
- audit no responde: el resto de los KPIs se calcula igual; `tiempoCicloPromedioHoras` va `null` con el aviso `"tiempoCicloPromedioHoras no calculado: ms-andesstay-audit no responde"`.
- 4xx de reservations (ej. 401 por token vencido): se devuelve con el mismo codigo y cuerpo.

```bash
curl "http://localhost:8087/api/report/kpis?range=last24h" -H "Authorization: Bearer $TOKEN_ADMIN"
```

## Roles

| Endpoint | Admin | Operador_Dominio | Cliente_Dominio | Auditor |
|---|---|---|---|---|
| GET `/api/report/kpis` y `/top-units` | si | no | no | no |

`@PreAuthorize("hasRole('Admin')")`; otro rol -> **403**. Como el token de Admin se propaga, reservations devuelve todas las reservas y audit permite leer los timelines.

## Tests

```powershell
.\mvnw.cmd clean package
```

Sin llamadas reales a otros servicios:

- `KpiCalculatorTest`: cada KPI con datos de prueba (rango, por hora, por estado, ocupacion, ingresos, tiempo de ciclo, top unidades).
- `ReportServiceTest`: clientes simulados; rango con reloj fijo, audit caido -> null + aviso, reservations caido -> 503, 4xx propagado.
- `RestClientsTest`: `MockRestServiceServer`; URL configurada y JWT propagado en `Authorization`.
- `ReportControllerSecurityTest`: solo Admin (403 el resto, 401 sin token), `range`/`limit` invalidos -> 400, 503 con mensaje.
