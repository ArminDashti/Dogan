# Device module

**Path:** `src/views/DeviceView.vue`

Single-handset details (no fleet stats or logs).

## Responsibilities

- Poll `GET /devices` every 5 seconds
- Honor Settings default device when present
- Show a definition list of fields for the selected device
- Offer a handset select when more than one device exists

## Dependencies

- `fetchDevices`, `fetchSettings`
- `AppLayout`, `ErrorAlert`

## Invariants

- Polling stops on component unmount
- `/dashboard` redirects to `/device`