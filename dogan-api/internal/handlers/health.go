package handlers

import (
	"net/http"
	"time"

	livekitauth "github.com/dogan/dogan-server/internal/livekit"
	"github.com/dogan/dogan-server/internal/models"
	"github.com/gin-gonic/gin"
)

type HealthHandler struct {
	liveKit *livekitauth.TokenIssuer
}

func NewHealthHandler(liveKit *livekitauth.TokenIssuer) *HealthHandler {
	return &HealthHandler{liveKit: liveKit}
}

func (handler *HealthHandler) GetHealth(context *gin.Context) {
	liveKitConfigured := handler.liveKit != nil && handler.liveKit.Enabled()
	context.JSON(http.StatusOK, models.HealthResponse{
		Status:            "ok",
		Timestamp:         time.Now().UTC(),
		LiveKitConfigured: liveKitConfigured,
	})
}
