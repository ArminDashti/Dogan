package handlers

import (
	"errors"
	"net/http"
	"time"

	"github.com/dogan/dogan-server/internal/mediamtx"
	"github.com/dogan/dogan-server/internal/models"
	"github.com/dogan/dogan-server/internal/store"
	"github.com/gin-gonic/gin"
)

type StreamTokenHandler struct {
	tokenIssuer *mediamtx.TokenIssuer
	webrtcStore store.WebRTCStore
	iceOptions  mediamtx.IceServerOptions
}

func NewStreamTokenHandler(
	tokenIssuer *mediamtx.TokenIssuer,
	webrtcStore store.WebRTCStore,
	iceOptions mediamtx.IceServerOptions,
) *StreamTokenHandler {
	return &StreamTokenHandler{
		tokenIssuer: tokenIssuer,
		webrtcStore: webrtcStore,
		iceOptions:  iceOptions,
	}
}

func (handler *StreamTokenHandler) IssueToken(context *gin.Context) {
	if !handler.tokenIssuer.Enabled() {
		context.JSON(http.StatusServiceUnavailable, models.ErrorResponse{Error: "streaming is not configured"})
		return
	}

	var request models.StreamTokenRequest
	if err := context.ShouldBindJSON(&request); err != nil {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: "invalid request body"})
		return
	}

	role := defaultRole(request.Role)
	tokenResponse, err := handler.tokenIssuer.IssueToken(mediamtx.TokenRequest{
		DeviceID: request.DeviceID,
		Identity: request.Identity,
		Role:     role,
	})
	if err != nil {
		switch {
		case errors.Is(err, mediamtx.ErrInvalidRole):
			context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: err.Error()})
		default:
			context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to issue stream token"})
		}
		return
	}

	url := tokenResponse.WHEPURL
	if role == mediamtx.RolePublisher {
		url = tokenResponse.WHIPURL
	}

	_, _ = handler.webrtcStore.SaveConnection(models.WebRTCConnectionRecord{
		DeviceID:    request.DeviceID,
		Room:        tokenResponse.Path,
		Identity:    tokenResponse.Identity,
		Role:        role,
		ConnectedAt: time.Now().UTC(),
		Status:      "active",
	})

	context.JSON(http.StatusOK, models.StreamTokenResponse{
		Token:      tokenResponse.Token,
		URL:        url,
		Room:       tokenResponse.Path,
		Path:       tokenResponse.Path,
		Identity:   tokenResponse.Identity,
		Role:       role,
		WHIPURL:    tokenResponse.WHIPURL,
		WHEPURL:    tokenResponse.WHEPURL,
		HLSURL:     tokenResponse.HLSURL,
		ExpiresAt:  tokenResponse.ExpiresAt,
		IceServers: mediamtx.IceServers(handler.iceOptions, tokenResponse.Identity),
	})
}

type MediaMTXAuthHandler struct {
	tokenIssuer *mediamtx.TokenIssuer
}

func NewMediaMTXAuthHandler(tokenIssuer *mediamtx.TokenIssuer) *MediaMTXAuthHandler {
	return &MediaMTXAuthHandler{tokenIssuer: tokenIssuer}
}

func (handler *MediaMTXAuthHandler) Authorize(context *gin.Context) {
	var request mediamtx.AuthRequest
	if err := context.ShouldBindJSON(&request); err != nil {
		context.JSON(http.StatusUnauthorized, models.ErrorResponse{Error: "unauthorized"})
		return
	}

	if !handler.tokenIssuer.Authorize(request) {
		context.JSON(http.StatusUnauthorized, models.ErrorResponse{Error: "unauthorized"})
		return
	}

	context.Status(http.StatusOK)
}

func defaultRole(role string) string {
	if role == "" {
		return mediamtx.RoleSubscriber
	}
	return role
}
