# Wiser Hub API Documentation

This directory contains technical specifications and references for the local REST APIs exposed by the Drayton Wiser HeatHub.

---

## Documentation Index

- **[Wiser REST API v1 Documentation](file:///workspace/docs/wiser-api-v1.md)**: Specifications for the legacy v1 REST API (`/data/domain/`, `/data/network/`), covering heating, hot water, smart plugs, and monolithic schedule management.
- **[Wiser REST API v2 Documentation](file:///workspace/docs/wiser-api-v2.md)**: Specifications for the modernized v2 REST API (`/data/v2/`), covering modular domain data, dedicated schedule management (Heating, OnOff, Level), OpenTherm telemetry, shutters, lights, underfloor heating, and full CRUD (`GET`, `POST`, `PATCH`, `DELETE`).

---

## V1 vs V2 Comparison

| Feature | V1 API | V2 API |
| :--- | :--- | :--- |
| **Base URL** | `http://{hub}/data/` | `http://{hub}/data/v2/` |
| **Domain State** | `GET /data/domain/` (monolithic, includes schedules) | `GET /data/v2/domain/` (modular, schedules separated) |
| **Schedules** | Inside `/data/domain/` under `"Schedule"` array | Dedicated endpoint: `GET /data/v2/schedules/` grouped by type |
| **Schedule Control** | `PATCH /data/domain/Schedule/{id}` | Full CRUD under `/data/v2/schedules/` (`GET`, `POST`, `PATCH`, `DELETE`) |
| **Room Boost** | Simulated via `Manual` override with duration | Dedicated `"Boost"` type with `IncreaseSetPointBy` delta |
| **Cancel Overrides** | Room-level with zeroed parameters | Per-room (`Type: "None"`) and global (`CancelUserOverrides`) |
| **OpenTherm Telemetry** | Not available | `GET /data/v2/opentherm/` |
| **Device Types** | Hub, iTRV, RoomStat, HotWater, SmartPlug | + Shutters, Lights, Electric Actuators, UFH, Moments |
| **Supported Methods** | `GET`, `PATCH` | `GET`, `POST`, `PATCH`, `DELETE` |

---

## Role of Wiser Home REST Proxy

The **Wiser Home REST Proxy** sits between your home automation systems (e.g. Home Assistant, Node-RED, scripts) and the local HeatHub:

1. **Transparent Routing**: Listens on `/data/*` and forwards requests seamlessly to both V1 (`/data/domain/`) and V2 (`/data/v2/domain/`, `/data/v2/schedules/`, `/data/v2/opentherm/`) endpoints.
2. **Authentication Injection**: Injects the required `Secret` header into every request.
3. **Response Caching**: Caches successful `GET` queries to reduce load on the hub's embedded CPU, and invalidates the cache on state mutations (`POST`, `PATCH`, and `DELETE`).
4. **Header Sanitization**: Strips hop-by-hop headers (`Connection`, `Content-Length`, `Transfer-Encoding`, etc.) to prevent connection drops.

---

## Credits & Acknowledgments

The documentation and endpoint mappings here are based on the reverse-engineering and open-source contributions of the Wiser home automation community:

- **Angelo Santagata** ([@asantaga](https://github.com/asantaga)) for creating the original Python integration and documentation in [asantaga/wiserheatingapi](https://github.com/asantaga/wiserheatingapi) and [asantaga/wiserHomeAssistantPlatform](https://github.com/asantaga/wiserHomeAssistantPlatform).
- **Mark Parker** ([@msp1974](https://github.com/msp1974)) for re-architecting the v2 library, schedule management, and expanded device models in [msp1974/wiserHeatAPIv2](https://github.com/msp1974/wiserHeatAPIv2).
- The **Knightnet Knowledgebase** for detailing the initial HeatHub secret retrieval process ([knightnet.org.uk](https://it.knightnet.org.uk/kb/nr-qa/drayton-wiser-heating-control/)).
