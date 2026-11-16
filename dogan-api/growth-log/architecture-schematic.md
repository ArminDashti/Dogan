# Architecture (schematic)

Client â†’ dogan-api (:8080) â†’ SQLite file on api volume + LiveKit on external network t3-net.
Deploy scripts build `dogan-api:latest` and `docker compose -p dogan up -d`.
