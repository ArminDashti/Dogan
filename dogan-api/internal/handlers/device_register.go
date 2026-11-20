package handlers

import (
	"net/http"

	"github.com/dogan/dogan-server/internal/models"
	"github.com/dogan/dogan-server/internal/store"
	"github.com/gin-gonic/gin"
)

type DeviceRegisterHandler struct {
	deviceStore store.DeviceStore
}

func NewDeviceRegisterHandler(deviceStore store.DeviceStore) *DeviceRegisterHandler {
	return &DeviceRegisterHandler{deviceStore: deviceStore}
}

// Register upserts the handset into the fleet list (devices table).
func (handler *DeviceRegisterHandler) Register(context *gin.Context) {
	var request models.DeviceRegisterRequest
	if err := context.ShouldBindJSON(&request); err != nil {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: "invalid request body"})
		return
	}

	if err := handler.deviceStore.EnsureDevice(request.DeviceID); err != nil {
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to register device"})
		return
	}

	context.Status(http.StatusNoContent)
}