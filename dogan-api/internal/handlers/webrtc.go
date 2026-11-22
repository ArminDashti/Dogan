package handlers

import (
	"net/http"
	"strconv"

	"github.com/dogan/dogan-server/internal/mediamtx"
	"github.com/dogan/dogan-server/internal/models"
	"github.com/dogan/dogan-server/internal/store"
	"github.com/gin-gonic/gin"
)

type WebRTCHandler struct {
	webrtcStore   store.WebRTCStore
	controlClient *mediamtx.ControlClient
}

func NewWebRTCHandler(webrtcStore store.WebRTCStore, controlClient *mediamtx.ControlClient) *WebRTCHandler {
	return &WebRTCHandler{
		webrtcStore:   webrtcStore,
		controlClient: controlClient,
	}
}

func (handler *WebRTCHandler) ListConnections(context *gin.Context) {
	deviceID := context.Query("device-id")

	limit := 50
	if limitRaw := context.Query("limit"); limitRaw != "" {
		parsedLimit, err := strconv.Atoi(limitRaw)
		if err == nil && parsedLimit > 0 {
			limit = parsedLimit
		}
	}

	connections, err := handler.webrtcStore.ListConnections(deviceID, limit)
	if err != nil {
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to list webrtc connections"})
		return
	}

	if handler.controlClient == nil {
		context.JSON(http.StatusOK, gin.H{
			"connections":            connections,
			"media_server_reachable": false,
		})
		return
	}

	paths, err := handler.controlClient.ListPaths()
	if err != nil {
		context.JSON(http.StatusOK, gin.H{
			"connections":            connections,
			"media_server_reachable": false,
		})
		return
	}

	pathsByName := make(map[string]mediamtx.PathInfo, len(paths))
	for _, path := range paths {
		pathsByName[path.Name] = path
	}
	for index := range connections {
		connection := connections[index]
		path, exists := pathsByName[connection.Room]
		live := exists && ((connection.Role == mediamtx.RoleSubscriber && path.ReadersCount > 0) ||
			(connection.Role != mediamtx.RoleSubscriber && path.Ready))
		connections[index].Live = &live
	}

	context.JSON(http.StatusOK, gin.H{
		"connections":            connections,
		"paths":                  paths,
		"media_server_reachable": true,
	})
}
