# Wiser Hub REST API v2 Specification

> **Attribution & Acknowledgments**:  
> This documentation is based on the modular V2 architecture and implementation developed by **Mark Parker** ([msp1974/wiserHeatAPIv2](https://github.com/msp1974/wiserHeatAPIv2)) along with **Angelo Santagata** and the contributors to the [Wiser Home Assistant Integration](https://github.com/asantaga/wiserHomeAssistantPlatform).

---

## 1. Overview & Discovery

The V2 REST API provides a structured, modular interface to the Drayton Wiser HeatHub firmware. It breaks out schedules, network management, and OpenTherm telemetry into dedicated endpoints, expands support to modern Zigbee device families (shutters, lights, electric heating actuators, underfloor heating), and introduces full CRUD capabilities (POST, PATCH, DELETE).

### Hub Discovery via mDNS
The HeatHub broadcasts via Zeroconf / mDNS:
- **Service Type**: `_http._tcp.local.`
- **Hostname Pattern**: `WiserHeatXXXXXX.local.` (where `XXXXXX` is the last 6 characters of the hub MAC address).

### Authentication Header
Every HTTP request must include the `Secret` header:
```http
Secret: <YOUR_WISER_SECRET_KEY>
Content-Type: application/json
```

---

## 2. API Endpoints Overview

All V2 endpoints are prefixed with `/data/v2/`:

| Endpoint | Supported Methods | Description |
| :--- | :--- | :--- |
| `/data/v2/domain/` | `GET` | Core domain status (System, Rooms, Devices, Heating Channels, Actuators, Lights, Shutters, UFH). |
| `/data/v2/domain/Room` | `POST` | Create a new room entity. |
| `/data/v2/domain/Room/{roomId}` | `PATCH`, `DELETE` | Update room settings / overrides, or delete a room. |
| `/data/v2/domain/System` | `PATCH` | Update global settings (Away mode, Eco mode, Comfort mode, valve protection). |
| `/data/v2/domain/System/RequestPermitJoin` | `POST` | Put the hub into Zigbee pairing mode to join new devices. |
| `/data/v2/domain/{DeviceType}/{id}` | `PATCH` | Update settings on specific devices (`SmartPlug`, `Shutter`, `Light`, `HeatingActuator`, etc.). |
| `/data/v2/schedules/` | `GET` | Retrieve all schedules grouped by type (`Heating`, `OnOff`, `Level`). |
| `/data/v2/schedules/{type}/{id}` | `PATCH`, `DELETE` | Update an existing schedule or delete a schedule. |
| `/data/v2/schedules/Assign` | `POST`, `PATCH` | Create and assign a schedule (`POST`) or reassign existing schedules (`PATCH`). |
| `/data/v2/network/` | `GET` | Wi-Fi station status, signal strength (RSSI), channel, and mesh child nodes. |
| `/data/v2/network/Station` | `PATCH` | Configure Wi-Fi station parameters. |
| `/data/v2/opentherm/` | `GET` | Real-time OpenTherm boiler telemetry (modulation, flow/return temperatures, pressure). |

---

## 3. Data Representation

### Temperature Scaling
- Setpoints and current temperatures are integer tenths of a degree Celsius (`200` = `20.0 °C`).
- Temperature offsets and boost deltas use the same scaling (`20` = `+2.0 °C`).
- Minimum temperature: `50` (`5.0 °C`).
- Maximum temperature: `300` (`30.0 °C`).
- Special Off state: `-200` (`-20.0 °C`).

### Level Scaling (Shutters & Lights)
- Shutter position and light brightness levels are expressed as percentage integers (`0` to `100`).

---

## 4. Endpoint Specifications

### `GET /data/v2/domain/`
Returns the core domain state without schedules:
```json
{
  "System": {
    "PairingStatus": "Paired",
    "TimeZoneOffset": 0,
    "AutomaticDaylightSaving": true,
    "UnixTime": 1719733800,
    "EcoModeEnabled": true,
    "ComfortModeEnabled": true,
    "ValveProtectionEnabled": true,
    "AwayModeSetPointLimit": 105,
    "AwayModeAffectsHotWater": true,
    "DegradedModeSetpointThreshold": 180,
    "OpenThermConnectionStatus": "Connected"
  },
  "Room": [
    {
      "id": 1,
      "Name": "Living Room",
      "Mode": "Auto",
      "DemandType": "Modulating",
      "CurrentSetPoint": 200,
      "CalculatedTemperature": 195,
      "DisplayedSetPoint": 200,
      "RoomStatId": 3,
      "SmartValveIds": [2, 4],
      "HeatingActuatorIds": [],
      "ScheduleId": 1,
      "WindowDetectionActive": true
    }
  ],
  "Device": [
    {
      "id": 0,
      "ProductType": "Controller",
      "ModelIdentifier": "Hub",
      "ProductIdentifier": "Wiser HeatHub",
      "ProductModel": "WT734R1S0902"
    }
  ],
  "HeatingChannel": [...],
  "HotWater": [...],
  "SmartPlug": [...],
  "SmartValve": [...],
  "RoomStat": [...],
  "HeatingActuator": [...],
  "UnderFloorHeating": [...],
  "Shutter": [
    {
      "id": 10,
      "Name": "Office Blinds",
      "CurrentPercentage": 50,
      "TargetPercentage": 50,
      "ScheduleId": 12,
      "Mode": "Auto"
    }
  ],
  "Light": [
    {
      "id": 11,
      "Name": "Kitchen Ceiling",
      "CurrentPercentage": 80,
      "IsOn": true,
      "ScheduleId": 13,
      "Mode": "Auto"
    }
  ],
  "Moment": [...]
}
```

---

### `GET /data/v2/schedules/`
Returns schedules categorized by schedule family:
```json
{
  "Heating": [
    {
      "id": 1,
      "Name": "Living Room",
      "Type": "Heating",
      "Monday": {
        "SetPoints": [
          { "Time": 630, "DegreesC": 200 },
          { "Time": 830, "DegreesC": 150 },
          { "Time": 1700, "DegreesC": 210 },
          { "Time": 2230, "DegreesC": 150 }
        ]
      },
      "Tuesday": { ... }
    }
  ],
  "OnOff": [
    {
      "id": 2,
      "Name": "Hot Water",
      "Type": "OnOff",
      "Monday": {
        "SetPoints": [
          { "Time": 600, "State": "On" },
          { "Time": 730, "State": "Off" },
          { "Time": 1730, "State": "On" },
          { "Time": 2100, "State": "Off" }
        ]
      }
    }
  ],
  "Level": [
    {
      "id": 3,
      "Name": "Office Blinds",
      "Type": "Level",
      "Monday": {
        "Time": [700, 2000],
        "Level": [100, 0]
      }
    }
  ]
}
```
*Note: `Level` schedules use synchronized parallel arrays: `Time` contains activation times, and `Level` contains corresponding 0–100 percentage values.*

---

### `GET /data/v2/opentherm/`
Returns telemetry for OpenTherm-connected boilers:
```json
{
  "OperatingMode": "CentralHeating",
  "FlameStatus": "On",
  "ModulationLevel": 45,
  "WaterPressure": 1.5,
  "FlowTemperature": 58.2,
  "ReturnTemperature": 46.1,
  "DomesticHotWaterTemperature": 52.0,
  "FaultFlags": 0
}
```

---

## 5. Control Commands & Mutations

### Room Commands

#### Boost Room Temperature
Boost uses dedicated `"Boost"` type with a temperature increase delta (`IncreaseSetPointBy` in tenths of °C):
```http
PATCH /data/v2/domain/Room/{roomId}
Content-Type: application/json

{
  "RequestOverride": {
    "Type": "Boost",
    "DurationMinutes": 60,
    "IncreaseSetPointBy": 20
  }
}
```

#### Set Manual Room Temperature
```http
PATCH /data/v2/domain/Room/{roomId}
Content-Type: application/json

{
  "RequestOverride": {
    "Type": "Manual",
    "SetPoint": 210
  }
}
```

#### Set Manual Temperature for a Specific Duration
```http
PATCH /data/v2/domain/Room/{roomId}
Content-Type: application/json

{
  "RequestOverride": {
    "Type": "Manual",
    "DurationMinutes": 120,
    "SetPoint": 210
  }
}
```

#### Cancel Room Override
```http
PATCH /data/v2/domain/Room/{roomId}
Content-Type: application/json

{
  "RequestOverride": {
    "Type": "None"
  }
}
```

#### Create a New Room
```http
POST /data/v2/domain/Room
Content-Type: application/json

{
  "name": "Conservatory"
}
```

#### Delete a Room
```http
DELETE /data/v2/domain/Room/{roomId}
```

---

### System Commands

#### Cancel All Overrides Across All Rooms
```http
PATCH /data/v2/domain/System
Content-Type: application/json

{
  "RequestOverride": {
    "Type": "CancelUserOverrides"
  }
}
```

#### Enable / Disable Away Mode
```http
PATCH /data/v2/domain/System
Content-Type: application/json

{
  "RequestOverride": {
    "Type": 2
  }
}
```
*(Use `"Type": 0` to turn off Away mode).*

#### Update System Options
```http
PATCH /data/v2/domain/System
Content-Type: application/json

{
  "EcoModeEnabled": true,
  "ComfortModeEnabled": true,
  "ValveProtectionEnabled": true,
  "AwayModeSetPointLimit": 110,
  "AwayModeAffectsHotWater": true
}
```

#### Enable Device Pairing (Permit Join)
Put the hub into Zigbee pairing mode for 180 seconds:
```http
POST /data/v2/domain/System/RequestPermitJoin
Content-Type: application/json

{
  "PermitJoinDuration": 180
}
```

---

### Schedule Management (CRUD)

#### Update an Existing Schedule
```http
PATCH /data/v2/schedules/{type}/{id}
Content-Type: application/json

{
  "Monday": {
    "SetPoints": [
      { "Time": 600, "DegreesC": 210 },
      { "Time": 2200, "DegreesC": 150 }
    ]
  }
}
```
*(Where `{type}` is `heating`, `onoff`, or `level`).*

#### Create & Assign Schedule
```http
POST /data/v2/schedules/Assign
Content-Type: application/json

{
  "Assignments": [1, 2],
  "Heating": {
    "Name": "Bedrooms Schedule",
    "Type": "Heating",
    "Monday": {
      "SetPoints": [...]
    }
  }
}
```

#### Assign Schedule to Devices/Rooms
```http
PATCH /data/v2/schedules/Assign
Content-Type: application/json

{
  "Assignments": [1, 3]
}
```

#### Delete a Schedule
```http
DELETE /data/v2/schedules/{type}/{id}
```

---

### Actuator, Shutter, and Light Control

#### Control Shutter Position (0 - 100%)
```http
PATCH /data/v2/domain/Shutter/{shutterId}
Content-Type: application/json

{
  "TargetPercentage": 75
}
```

#### Control Light Brightness & Power
```http
PATCH /data/v2/domain/Light/{lightId}
Content-Type: application/json

{
  "CurrentPercentage": 60,
  "IsOn": true
}
```
