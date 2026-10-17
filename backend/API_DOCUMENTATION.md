# Bus Tracker API Contract

This file is the complete integration contract for the frontend AI. Implement only the endpoints and payloads documented here. The backend is a Spring Boot application.

## 1. Connection and client rules

- Base URL: use the deployed server origin, for example `https://<server-host>`. During local development use `http://localhost:8080`.
- REST base path: `/api`.
- REST payload format: JSON (`Content-Type: application/json`) unless noted otherwise.
- All endpoints are currently publicly accessible. The backend does not enforce role authorization yet.
- Authentication is cookie-based HTTP session authentication, not JWT. After login, retain the `JSESSIONID` cookie and send it on later requests. For browser `fetch`, use `credentials: "include"` on login, logout, and any request that must carry the session cookie. Native clients must use a cookie jar.
- CORS currently allows all origins.
- Passwords are currently stored as plain text in the local file-based H2 database. Do not display or log passwords.
- **Temporary driver testing login:** drivers may authenticate with `busId` (or `busNumber`) and `phoneNumber`; no phone verification or OTP is performed. Password login remains supported for compatibility.
- Live bus locations are held in memory and are lost when the server restarts. Accounts and schedules persist in H2.
- Error responses are JSON when produced by an endpoint. Unexpected framework errors may use Spring's default error shape.

Common error shape:

```json
{
  "status": "ERROR",
  "message": "Human-readable error message"
}
```

## 2. Authentication

Roles are exactly `DRIVER` and `PASSENGER` (case-insensitive in requests; responses are uppercase).

### 2.1 Create an account: `POST /api/auth/signup`

Creates a persistent account. A driver uses `busId` as the username. A passenger uses `username`.

Passenger request:

```json
{
  "username": "passenger1",
  "password": "password123",
  "role": "PASSENGER",
  "displayName": "Passenger Name"
}
```

Driver signup request:

```json
{
  "busId": "GA-03-X-1001",
  "phoneNumber": "+919876543210",
  "password": "123456",
  "role": "DRIVER",
  "displayName": "Driver Name",
  "busCategory": "KTCL"
}
```

Request fields:

| Field | Type | Required | Rules |
|---|---|---:|---|
| `role` | string | yes | `DRIVER` or `PASSENGER` |
| `password` | string | passenger; optional for driver | Required for passenger accounts; retained for driver password-login compatibility |
| `username` | string | passenger | Must not be blank |
| `busId` | string | driver | Used as the driver's username; must not be blank |
| `phoneNumber` | string | driver | Stored as supplied and used for temporary driver login; no verification is performed |
| `displayName` | string | no | Defaults to `Passenger` or `Driver` |
| `busCategory` | string | no | `KTCL` or `Private` |

The backend trims `username`, `busId`, and `displayName`. For a driver, `busId` takes precedence if both `busId` and `username` are sent.

Success: `201 Created`

```json
{
  "status": "SUCCESS",
  "message": "Account created successfully",
  "username": "passenger1",
  "role": "PASSENGER"
}
```

Errors:

- `400 Bad Request`: missing/blank passenger password, missing username/busId, missing driver phoneNumber, or invalid role.
- `409 Conflict`: username/bus ID already exists.

### 2.2 Log in: `POST /api/auth/login`

Creates an HTTP session and returns a `Set-Cookie: JSESSIONID=...` response header. The frontend must retain the cookie.

Passenger request:

```json
{
  "username": "passenger1",
  "password": "password123",
  "role": "PASSENGER"
}
```

Driver password login request (legacy-compatible):

```json
{
  "busId": "GA-03-X-1001",
  "password": "123456",
  "role": "DRIVER"
}
```

Temporary driver phone login request:

```json
{
  "busId": "GA-03-X-1001",
  "phoneNumber": "+919876543210",
  "role": "DRIVER"
}
```

For temporary phone login, `busId` (or `busNumber`) and `phoneNumber` are required; do not send a password. The backend accepts any nonblank values and creates a driver session without checking the bus number or phone number against the database. No OTP or other phone verification is performed. `role` is optional, but should be `DRIVER`. This behavior is for testing only.

The backend also accepts `phone` as an alias for `phoneNumber`.

For password login, `username` or `busId` and `password` are required. The login identifier may also be sent as `busNumber`; `username` takes precedence, then `busId`, then `busNumber`.

Success: `200 OK` for a passenger:

```json
{
  "status": "SUCCESS",
  "username": "passenger1",
  "role": "PASSENGER",
  "displayName": "Passenger Name"
}
```

Success: `200 OK` for a driver:

```json
{
  "status": "SUCCESS",
  "username": "GA-03-X-1001",
  "role": "DRIVER",
  "displayName": "Driver Name",
  "busId": "GA-03-X-1001",
  "driverName": "Driver Name",
  "busCategory": "KTCL"
}
```

Errors:

- `400 Bad Request`: password or username/busId is missing.
- `401 Unauthorized`: credentials are invalid or the requested role does not match.

Driver accounts created by the admin schedule endpoints remain valid through password login. They can use phone login only if a phone number has been stored for that driver.

### 2.3 Log out: `POST /api/auth/logout`

Invalidates the current HTTP session. Send the session cookie with the request.

Success: `200 OK`

```json
{
  "status": "SUCCESS",
  "message": "Logged out successfully"
}
```

Calling logout without an active session is safe and returns the same response.

## 3. Passenger bus-location APIs

These endpoints are public and return JSON.

### 3.1 Active bus IDs: `GET /api/buses/active`

Returns the set of bus IDs that have received at least one valid location update since server startup.

Response: `200 OK`

```json
[
  "GA-03-X-1001",
  "GA-03-X-2002"
]
```

The array may be empty.

### 3.2 Current live locations: `GET /api/buses/locations`

Returns the latest in-memory location for each active bus.

Response: `200 OK`

```json
{
  "busId": "GA-03-X-1001",
  "busNumber": "1001",
  "latitude": 15.4901,
  "longitude": 73.8278,
  "startPoint": "Panaji",
  "destination": "Margao",
  "routeTaken": "Panaji - Margao",
  "busCategory": "KTCL"
}
```

### 3.3 Initial map locations: `GET /api/buses/all-scheduled-locations`

Returns at most one location record per scheduled bus. If the bus is live, the latest live location is returned. Otherwise its schedule start coordinates are returned and the schedule destination.

Response: `200 OK`

```json
[
  {
    "busId": "GA-03-X-1001",
    "busNumber": "1001",
    "latitude": 15.4901,
    "longitude": 73.8278,
    "startPoint": "Panaji",
    "destination": "Margao",
    "routeTaken": "Panaji - Margao",
    "busCategory": "KTCL"
  }
]
```

The array may be empty. If the backend cannot read schedules, it returns `500 Internal Server Error` with no guaranteed response body.

### 3.4 Live SSE stream: `GET /api/buses/locations/stream`

Open a long-lived HTTP Server-Sent Events connection. This is an on-demand stream that only sends updates for buses requested by the client.

**Query Parameters:**
| Parameter | Type | Required | Description |
|---|---|---:|---|
| `clientId` | string | yes | A unique identifier for the client's session. Used to manage bus tracking requests for this specific connection. |

**Example:**
```http
GET /api/buses/locations/stream?clientId=my-unique-session-id
Accept: text/event-stream
```

Do not parse this response as one JSON document. Each SSE event has an event name and JSON data.

#### Events
Each live update for a tracked bus:
```text
event: location-update
data: {"busId":"GA-03-X-1001","busNumber":"1001","latitude":15.491,"longitude":73.829,"startPoint":"Panaji","destination":"Margao","routeTaken":"Panaji - Margao","busCategory":"KTCL"}
```

Bus removal/stop event:
```text
event: bus-stopped
data: [{"busId":"GA-03-X-1001","status":"STOPPED"}]
```

Heartbeat, sent approximately every 15 seconds:
```text
event: heartbeat
data: "keep-alive"
```

**Client Behavior:**
1. Connect to the stream with a `clientId`.
2. Use the **Track Bus** and **Untrack Bus** endpoints (see below) to manage which buses to receive updates for.
3. The server will send a `location-update` immediately after a bus is tracked if its location is known.
4. The connection will be closed by the server if there are no more buses being tracked for this `clientId`.
5. Reconnect after failure using approximately 5 seconds.

### 3.5 Track Bus: `POST /api/buses/track`

Request to start receiving location updates for a specific bus on an active SSE stream.

**Query Parameters:**
| Parameter | Type | Required | Description |
|---|---|---:|---|
| `clientId` | string | yes | The same ID used to open the SSE stream. |
| `busId` | string | yes | The ID of the bus to track. |

**Success:** `200 OK`
```json
{
  "status": "SUCCESS",
  "message": "Tracking bus: GA-03-X-1001"
}
```

### 3.6 Untrack Bus: `POST /api/buses/untrack`

Request to stop receiving location updates for a specific bus. If no more buses are tracked, the associated SSE connection will be closed.

**Query Parameters:**
| Parameter | Type | Required | Description |
|---|---|---:|---|
| `clientId` | string | yes | The same ID used to open the SSE stream. |
| `busId` | string | yes | The ID of the bus to stop tracking. |

**Success:** `200 OK`
```json
{
  "status": "SUCCESS",
  "message": "Stopped tracking bus: GA-03-X-1001"
}
```

## 4. Driver location upload over STOMP

The driver frontend must first log in, then connect to the WebSocket endpoint. The backend does not currently verify the session or bus ownership on WebSocket messages, so the client must use the logged-in driver's `busId`.

### 4.1 STOMP endpoint

- Native/WebSocket endpoint: `ws://<server-host>/ws` (or `wss://<server-host>/ws` over HTTPS).
- SockJS endpoint: `<http-or-https-origin>/ws` using SockJS negotiation.
- STOMP application destination prefix: `/app`.
- STOMP broker/topic prefix: `/topic`.

### 4.2 Publish location updates

Send a STOMP `SEND` frame to:

```text
/app/update-location
```

JSON body:

```json
{
  "busId": "GA-03-X-1001",
  "busNumber": "1001",
  "latitude": 15.4901,
  "longitude": 73.8278,
  "startPoint": "Panaji",
  "destination": "Margao",
  "routeTaken": "Panaji - Margao",
  "busCategory": "KTCL"
}
```

Fields:

| Field | Type | Required | Description |
|---|---|---:|---|
| `busId` | string | yes | Driver's assigned bus ID; blank IDs are ignored |
| `busNumber` | string | no | Bus number |
| `latitude` | number | yes | Current latitude |
| `longitude` | number | yes | Current longitude |
| `startPoint` | string | no | Starting location |
| `destination` | string | no | Current trip destination; may be null |
| `routeTaken` | string | no | Route taken |
| `busCategory` | string | no | `KTCL` or `Private` |

There is no REST response to a STOMP send. A null message or blank `busId` is ignored. A valid message replaces the bus's current in-memory location and triggers the SSE/topic notifications below.

### 4.3 Stop location sharing: `POST /api/buses/stop-sharing`

Drivers use this endpoint to explicitly stop sharing their location (e.g., end of shift or reached destination).

Request body:

```json
{
  "busId": "GA-03-X-1001"
}
```

Success: `200 OK`

```json
{
  "status": "SUCCESS",
  "message": "Location sharing stopped for bus: GA-03-X-1001"
}
```

Errors: `400` if `busId` is missing; `404` if the bus is not currently active.

### 4.4 Subscribe to backend topics (optional)

These topics are available for compatibility with the existing web interface:

| Topic | Payload |
|---|---|
| `/topic/bus-locations` | One `BusLocationUpdate` object (all updates) |
| `/topic/bus-stopped` | `{ "busId": "...", "status": "STOPPED" }` |
| `/topic/bus/{busId}` | One `BusLocationUpdate` object |
| `/topic/active-buses` | JSON array of active bus ID strings |
| `/topic/schedule-added` | One `BusSchedule` object |
| `/topic/schedule-deleted` | `{ "scheduleId": "...", "busId": "..." }` |
| `/topic/bus-deleted` | `{ "scheduleId": "...", "busId": "..." }` |

Passenger clients may use SSE or subscribe to `/topic/bus-locations` for live updates.

## 5. Admin schedule APIs

These endpoints are currently public; no admin login or role check is enforced. They persist schedules in H2 and are intended for the admin frontend.

### 5.1 List schedules: `GET /api/admin/schedules`

Response: `200 OK`, an array of `BusSchedule` objects. The array may be empty.

### 5.2 Create schedule: `POST /api/admin/schedules`

Request body:

```json
{
  "scheduleId": "SCH-12345678",
  "busId": "GA-03-X-1001",
  "driverName": "Driver Name",
  "driverEmail": "driver@example.com",
  "routeName": "Panaji - Margao",
  "startLocation": "Panaji",
  "destinationLocation": "Margao",
  "startLatitude": 15.4901,
  "startLongitude": 73.8278,
  "departureTime": "08:30",
  "arrivalTime": "10:00",
  "operatingDays": "MON,TUE,WED,THU,FRI",
  "busType": "Express",
  "busCategory": "KTCL"
}
```

Fields and types:

| Field | Type | Required | Notes |
|---|---|---:|---|
| `scheduleId` | string | no | Generated as `SCH-XXXXXXXX` when omitted or blank |
| `busId` | string | yes | Must not be blank |
| `busNumber` | string | no | Bus number |
| `driverName` | string | no | Defaults to `Assigned Driver` for credentials |
| `driverEmail` | string | yes | Must not be blank; used for generated credentials email |
| `routeName` | string | no | Free text |
| `startLocation` | string | no | Free text |
| `destinationLocation` | string | no | Free text |
| `startLatitude` | number | no | Starting latitude |
| `startLongitude` | number | no | Starting longitude |
| `departureTime` | string | no | Recommended `HH:mm` |
| `arrivalTime` | string | no | Recommended `HH:mm` |
| `operatingDays` | string | no | Example: `MON,TUE,WED` |
| `busType` | string | no | Free text |
| `busCategory` | string | no | `KTCL` or `Private` |

The backend generates a six-digit driver password, saves/replaces the driver credential record, and attempts to email the credentials. Email failure does not change the HTTP success response.

Success: `200 OK`

```json
{
  "status": "SUCCESS",
  "message": "Schedule created and login credentials emailed to driver!",
  "schedule": {
    "scheduleId": "SCH-12345678",
    "busId": "GA-03-X-1001",
    "busNumber": "1001",
    "driverName": "Driver Name",
    "driverEmail": "driver@example.com",
    "routeName": "Panaji - Margao",
    "startLocation": "Panaji",
    "destinationLocation": "Margao",
    "startLatitude": 15.4901,
    "startLongitude": 73.8278,
    "departureTime": "08:30",
    "arrivalTime": "10:00",
    "operatingDays": "MON,TUE,WED,THU,FRI",
    "busType": "Express",
    "busCategory": "KTCL"
  }
}
```

Errors: `400` when `busId` or `driverEmail` is missing; `500` for an unexpected persistence or processing failure.

### 5.3 Update schedule: `PUT /api/admin/schedules/{scheduleId}`

The path `scheduleId` identifies the schedule and remains authoritative. The request body uses the same fields as create. `driverEmail` is required. The backend updates the existing record, generates a new six-digit driver password, replaces the driver credential record for the saved bus ID, and attempts to email the new credentials.

Success: `200 OK` with the same `{ status, message, schedule }` shape as create. Errors: `400` for missing `driverEmail`, `404` when the path schedule ID does not exist, and `500` for unexpected failures.

### 5.4 Delete schedule: `DELETE /api/admin/schedules/{scheduleId}`

Deletes the persistent schedule and removes its active in-memory location.

Success: `200 OK`

```json
{
  "status": "SUCCESS",
  "message": "Schedule deleted successfully!"
}
```

Not found: `404 Not Found`

```json
{
  "status": "ERROR",
  "message": "Schedule ID not found in database."
}
```

## 6. Shared response models

### `BusLocationUpdate`

```typescript
interface BusLocationUpdate {
  busId: string;
  busNumber: string | null;
  latitude: number;
  longitude: number;
  startPoint: string | null;
  destination: string | null;
  routeTaken: string | null;
  busCategory: string | null;
}
```

### `BusSchedule`

```typescript
interface BusSchedule {
  scheduleId: string;
  busId: string | null;
  busNumber: string | null;
  driverName: string | null;
  driverEmail: string | null;
  routeName: string | null;
  startLocation: string | null;
  destinationLocation: string | null;
  startLatitude: number | null;
  startLongitude: number | null;
  departureTime: string | null;
  arrivalTime: string | null;
  operatingDays: string | null;
  busType: string | null;
  busCategory: string | null;
}
```

### `PassengerBusStatus`

```typescript
interface PassengerBusStatus {
  busId: string;
  busNumber: string | null;
  latitude: number;
  longitude: number;
  startPoint: string | null;
  destination: string | null;
  routeTaken: string | null;
  busCategory: string | null;
  nextStopName: string | null;
  distanceToNextStopKm: number;
  etaMinutes: number;
}
```

## 7. Required frontend flows

### Passenger

1. Sign up with `POST /api/auth/signup` using role `PASSENGER`, or log in with `POST /api/auth/login`.
2. Load `/api/buses/all-scheduled-locations` for the initial map.
3. Open `/api/buses/locations/stream` and apply the SSE events.
4. Optionally refresh `/api/buses/active` or `/api/buses/locations` for a non-streaming snapshot.
5. Call `/api/auth/logout` when the user signs out.

### Driver

1. Sign up with role `DRIVER` and provide `busId` plus `phoneNumber`, or use credentials created by the admin schedule flow.
2. Temporarily log in with `POST /api/auth/login` using `busId` and `phoneNumber` without OTP verification. Password login remains supported.
3. Connect to `/ws` using STOMP/SockJS.
4. Publish the logged-in driver's location to `/app/update-location` at the desired interval.
5. Call `POST /api/buses/stop-sharing` when the trip ends (e.g., reached destination) or when the driver manually stops sharing.
6. Disconnect the WebSocket when the trip ends or the driver signs out.
7. Call `/api/auth/logout` when the user signs out.

### Admin

1. Use the schedule endpoints under `/api/admin/schedules`.
2. Treat the generated driver password as delivered by the backend email flow; it is not returned in the HTTP response.
3. Subscribe to `/topic/schedule-added` and `/topic/schedule-deleted` only if real-time admin UI updates are needed.
