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
		var connectedAtRaw string
		var disconnectedAtRaw sql.NullString
		if err := rows.Scan(
			&connection.ID,
			&connection.DeviceID,
			&connection.Room,
			&connection.Identity,
			&connection.Role,
			&connectedAtRaw,
			&disconnectedAtRaw,
			&connection.Status,
		); err != nil {
			return nil, fmt.Errorf("scan webrtc connection: %w", err)
		}
		if err := scanSQLiteTime(connectedAtRaw, &connection.ConnectedAt); err != nil {
			return nil, fmt.Errorf("scan webrtc connected_at: %w", err)
		}
		disconnectedAt, err := scanNullableSQLiteTime(disconnectedAtRaw)
		if err != nil {
			return nil, fmt.Errorf("scan webrtc disconnected_at: %w", err)
		}
		connection.DisconnectedAt = disconnectedAt
		connections = append(connections, connection)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate webrtc connections: %w", err)
	}
	return connections, nil
}
