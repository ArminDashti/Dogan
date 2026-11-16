package store

import (
	"database/sql"
	"fmt"

	"github.com/dogan/dogan-server/internal/models"
)

func scanWebRTCConnections(rows *sql.Rows) ([]models.WebRTCConnectionRecord, error) {
	connections := make([]models.WebRTCConnectionRecord, 0)
	for rows.Next() {
		var connection models.WebRTCConnectionRecord
		var disconnectedAt sql.NullTime
		if err := rows.Scan(
			&connection.ID,
			&connection.DeviceID,
			&connection.Room,
			&connection.Identity,
			&connection.Role,
			&connection.ConnectedAt,
			&disconnectedAt,
			&connection.Status,
		); err != nil {
			return nil, fmt.Errorf("scan webrtc connection: %w", err)
		}
		if disconnectedAt.Valid {
			disconnectedAtValue := disconnectedAt.Time
			connection.DisconnectedAt = &disconnectedAtValue
		}
		connections = append(connections, connection)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate webrtc connections: %w", err)
	}
	return connections, nil
}
