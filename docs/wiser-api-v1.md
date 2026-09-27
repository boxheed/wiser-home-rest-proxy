# Wiser Hub REST API v1 Specification

> **Attribution & Acknowledgments**:  
> This documentation is based on the reverse-engineered Drayton Wiser Heating API library originally authored and documented by **Angelo Santagata** ([asantaga/wiserheatingapi](https://github.com/asantaga/wiserheatingapi)) and the community behind the [Wiser Home Assistant Integration](https://github.com/asantaga/wiserHomeAssistantPlatform), with foundational hub secret discovery documented on [Knightnet Knowledgebase](https://it.knightnet.org.uk/kb/nr-qa/drayton-wiser-heating-control/).

---

## 1. Overview & Authentication

The Wiser Home Hub (HeatHub) exposes a local REST API over HTTP (port 80). 

### Authentication Header
Every API request to the hub must include the `Secret` HTTP header:
```http
Secret: <YOUR_WISER_SECRET_KEY>
Content-Type: application/json
```

### Obtaining the Hub Secret
1. Press the **Setup** button on the physical HeatHub until the LED flashes.
2. The hub broadcasts a temporary Wi-Fi access point named `WiserHeatXXX` (or `WiserHeatXXXXXX`).
3. Connect to that Wi-Fi network from a computer or mobile device.
4. Issue an HTTP GET request to:
   ```bash
   curl http://192.168.8.1/secret
   ```
5. Store the returned secret string securely.
6. Press the **Setup** button again to return the hub to normal operational mode.

---

## 2. API Endpoints

In the V1 API, all endpoints are rooted under `/data/`:

| Endpoint | HTTP Method | Description |
| :--- | :--- | :--- |
| `/data/domain/` | `GET` | Fetches the complete, monolithic system state including system settings, rooms, devices, heating channels, hot water, smart plugs, and schedules. |
| `/data/network/` | `GET` | Fetches Wi-Fi network interface data, IP addresses, MAC address, signal strength, and mesh node info. |
| `/data/domain/Room/{roomId}` | `PATCH` | Updates room mode, target temperature, or applies an override / boost. |
| `/data/domain/HotWater/{hotWaterId}/` | `PATCH` | Updates hot water mode or applies hot water boost/override. |
| `/data/domain/SmartPlug/{smartPlugId}` | `PATCH` | Updates smart plug state (on, off, auto, manual). |
| `/data/domain/Schedule/{scheduleId}` | `PATCH` | Updates the weekly schedule for a room or hot water entity. |
| `/data/domain/System/RequestOverride` | `PATCH` | Applies system-wide overrides, such as Away mode. |
| `/data/domain/System` | `PATCH` | Updates system properties (e.g. EcoMode). |

---

## 3. Data Representation

### Temperature Scaling
The Wiser hub represents all heating temperatures internally in **tenths of a degree Celsius (0.1 °C)** as integers:
- `20.0 °C` = `200`
- `21.5 °C` = `215`
- `5.0 °C` (Minimum system temperature) = `50`
- `-20.0 °C` (Special "Off" state) = `-200`

### Time Representation
Times within schedules are represented as integer minutes from midnight or decimal military time (e.g. `06:30` is represented as `630`, `22:00` is represented as `2200`).

---

## 4. Endpoint Specifications

### `GET /data/domain/`
Returns the monolithic hub state:
```json
{
  "System": {
    "PairingStatus": "Paired",
    "TimeZoneOffset": 0,
    "AutomaticDaylightSaving": true,
    "UnixTime": 1719733800,
    "LocalDateAndTime": {
      "Year": 2026,
      "Month": "September",
      "Date": 27,
      "Day": "Sunday",
      "Time": 1730
    },
    "HeatingButtonOverrides": "None",
    "EcoModeEnabled": true,
    "AwayModeSetPointLimit": 105
  },
  "Room": [
    {
      "id": 1,
      "Name": "Living Room",
      "Mode": "Auto",
      "DemandType": "Modulating",
      "CurrentSetPoint": 200,
      "CalculatedTemperature": 195,
      "RoomStatId": 3,
      "SmartValveIds": [2, 4],
      "ScheduleId": 1
    }
  ],
  "Device": [
    {
      "id": 0,
      "ProductType": "Controller",
      "ModelIdentifier": "Hub",
      "SoftwareVersion": "3.15.0"
    },
    {
      "id": 2,
      "ProductType": "iTRV",
      "SerialNumber": "12345678",
      "BatteryVoltage": 28,
      "BatteryLevel": "Normal"
    }
  ],
  "HeatingChannel": [
    {
      "id": 1,
      "Name": "Channel 1",
      "RoomIds": [1],
      "HeatingRelayState": "Off",
      "IsSmartValvePreventingDemand": false
    }
  ],
  "HotWater": [
    {
      "id": 2,
      "WaterHeatingState": "Off",
      "Mode": "Auto",
      "ScheduleId": 3
    }
  ],
  "SmartPlug": [
    {
      "id": 5,
      "Name": "Lamp",
      "Mode": "Auto",
      "OutputState": "Off",
      "CurrentSummationDelivered": 120
    }
  ],
  "Schedule": [
    {
      "id": 1,
      "Type": "Heating",
      "Monday": {
        "SetPoints": [
          { "Time": 630, "DegreesC": 200 },
          { "Time": 830, "DegreesC": 150 },
          { "Time": 1700, "DegreesC": 210 },
          { "Time": 2230, "DegreesC": 150 }
        ]
      },
      "Tuesday": { "SetPoints": [...] },
      "Wednesday": { "SetPoints": [...] },
      "Thursday": { "SetPoints": [...] },
      "Friday": { "SetPoints": [...] },
      "Saturday": { "SetPoints": [...] },
      "Sunday": { "SetPoints": [...] }
    }
  ]
}
```

---

## 5. Control Commands & Mutations

### Set Room Temperature (Manual Mode)
```http
PATCH /data/domain/Room/{roomId}
Content-Type: application/json

{
  "RequestOverride": {
    "Type": "Manual",
    "SetPoint": 210
  }
}
```

### Boost Room Temperature
In V1, boosting was accomplished by specifying a manual override with a `DurationMinutes` parameter:
```http
PATCH /data/domain/Room/{roomId}
Content-Type: application/json

{
  "RequestOverride": {
    "Type": "Manual",
    "DurationMinutes": 60,
    "SetPoint": 220,
    "Originator": "App"
  }
}
```

### Cancel Boost / Revert to Schedule
```http
PATCH /data/domain/Room/{roomId}
Content-Type: application/json

{
  "RequestOverride": {
    "Type": "None",
    "DurationMinutes": 0,
    "SetPoint": 0,
    "Originator": "App"
  }
}
```

### Set Room Operating Mode
```http
PATCH /data/domain/Room/{roomId}
Content-Type: application/json

{
  "Mode": "Auto"
}
```
*(Acceptable values: `"Auto"`, `"Manual"`)*

### Set Away Mode (System Level)
```http
PATCH /data/domain/System/RequestOverride
Content-Type: application/json

{
  "Type": 2,
  "Mode": 2
}
```
*(To cancel away mode, set `"Type": 0, "Mode": 0`)*

### Set Hot Water State
```http
PATCH /data/domain/HotWater/{hotWaterId}/
Content-Type: application/json

{
  "RequestOverride": {
    "Type": "Manual",
    "SetPoint": 1100
  }
}
```

### Control Smart Plug
```http
PATCH /data/domain/SmartPlug/{smartPlugId}
Content-Type: application/json

{
  "RequestOutput": "On"
}
```

### Update Schedule
```http
PATCH /data/domain/Schedule/{scheduleId}
Content-Type: application/json

{
  "Monday": {
    "SetPoints": [
      { "Time": 700, "DegreesC": 210 },
      { "Time": 2200, "DegreesC": 160 }
    ]
  }
}
```

---

## 6. Limitations of the V1 API
- **Monolithic Payloads**: `/data/domain/` returns all rooms, devices, and schedules in one large JSON blob, placing high memory and CPU load on the embedded microcontroller.
- **Embedded Schedules**: Modifying or inspecting schedules requires parsing the monolithic domain payload rather than interacting with schedules directly.
- **No Support for Newer Devices**: V1 lacked endpoints and schemas for electric heating actuators, underfloor heating (UFH) controllers, smart shutters, dimmable lights, and scenes/moments.
- **Limited CRUD**: Creating, deleting, or reassigning schedules and rooms programmatically was not supported via dedicated REST methods.
