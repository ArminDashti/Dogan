package handlers

import (
	"net/http"
	"time"

	"github.com/dogan/dogan-server/internal/mediamtx"
	"github.com/dogan/dogan-server/internal/models"
	"github.com/gin-gonic/gin"
)

type HealthHandler struct {
	tokenIssuer *mediamtx.TokenIssuer
	webrtcURL   string
	hlsURL      string
	turnDomain  string
}

func NewHealthHandler(
	tokenIssuer *mediamtx.TokenIssuer,
	webrtcURL string,
	hlsURL string,
	turnDomain string,
) *HealthHandler {
	return &HealthHandler{
		tokenIssuer: tokenIssuer,
		webrtcURL:   webrtcURL,
		hlsURL:      hlsURL,
		turnDomain:  turnDomain,
	}
}

func (handler *HealthHandler) GetHealth(context *gin.Context) {
	streamingConfigured := handler.tokenIssuer != nil && handler.tokenIssuer.Enabled()
	response := models.HealthResponse{
		Status:              "ok",
		Timestamp:           time.Now().UTC(),
		StreamingConfigured: streamingConfigured,
		TurnDomain:          handler.turnDomain,
	}
	if streamingConfigured {
		response.StreamingWebRTCURL = handler.webrtcURL
		response.StreamingHLSURL = handler.hlsURL
		response.StreamingWarnings = append(
			mediamtx.ClientURLWarnings(handler.webrtcURL),
			prefixWarnings("hls", mediamtx.ClientURLWarnings(handler.hlsURL))...,
		)
	}
	context.JSON(http.StatusOK, response)
}

func prefixWarnings(prefix string, warnings []string) []string {
	prefixed := make([]string, 0, len(warnings))
	for _, warning := range warnings {
		prefixed = append(prefixed, prefix+": "+warning)
	}
	return prefixed
}
