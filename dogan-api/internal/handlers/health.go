package handlers

import (
	"net/http"
	"time"

	livekitauth "github.com/dogan/dogan-server/internal/livekit"
	"github.com/dogan/dogan-server/internal/models"
	"github.com/gin-gonic/gin"
)

type HealthHandler struct {
	liveKit       *livekitauth.TokenIssuer
	clientURL     string
	turnDomain    string
}

func NewHealthHandler(liveKit *livekitauth.TokenIssuer, clientURL string, turnDomain string) *HealthHandler {
	return &HealthHandler{
		liveKit:    liveKit,
		clientURL:  clientURL,
		turnDomain: turnDomain,
	}
}

func (handler *HealthHandler) GetHealth(context *gin.Context) {
	liveKitConfigured := handler.liveKit != nil && handler.liveKit.Enabled()
	response := models.HealthResponse{
		Status:            "ok",
		Timestamp:         time.Now().UTC(),
		LiveKitConfigured: liveKitConfigured,
		TurnDomain:        handler.turnDomain,
	}
	if liveKitConfigured {
		response.LiveKitClientURL = handler.clientURL
		response.LiveKitWarnings = livekitauth.ClientURLWarnings(handler.clientURL)
	}
	context.JSON(http.StatusOK, response)
}
