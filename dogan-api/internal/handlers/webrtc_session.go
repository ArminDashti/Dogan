package handlers

import (
	"errors"
	"log"
	"net/http"
	"time"

	"github.com/dogan/dogan-server/internal/mediamtx"
	"github.com/dogan/dogan-server/internal/models"
	"github.com/dogan/dogan-server/internal/store"
	"github.com/gin-gonic/gin"
)

type WebRTCSessionHandler struct {
	tokenIssuer *mediamtx.TokenIssuer
	webrtcStore store.WebRTCStore
	deviceStore store.DeviceStore
	iceOptions  mediamtx.IceServerOptions
}

func NewWebRTCSessionHandler(
	tokenIssuer *mediamtx.TokenIssuer,
	webrtcStore store.WebRTCStore,
	deviceStore store.DeviceStore,
	iceOptions mediamtx.IceServerOptions,
) *WebRTCSessionHandler {
	return &WebRTCSessionHandler{
		tokenIssuer: tokenIssuer,
		webrtcStore: webrtcStore,
		deviceStore: deviceStore,
		iceOptions:  iceOptions,
	}
}

func (handler *WebRTCSessionHandler) ensureConnectionAndDevice(
	deviceID string,
	tokenResponse mediamtx.TokenResponse,
) error {
	if err := handler.deviceStore.EnsureDevice(deviceID); err != nil {
		return err
	}
	_, err := handler.webrtcStore.SaveConnection(models.WebRTCConnectionRecord{
		DeviceID:    deviceID,
		Room:        tokenResponse.Path,
		Identity:    tokenResponse.Identity,
		Role:        mediamtx.RolePublisher,
		ConnectedAt: time.Now().UTC(),
		Status:      "active",
	})
	return err
}

func (handler *WebRTCSessionHandler) CreateSession(context *gin.Context) {
	if !handler.tokenIssuer.Enabled() {
		context.JSON(http.StatusServiceUnavailable, models.ErrorResponse{Error: "streaming is not configured"})
		return
	}

	var request models.WebRTCSessionRequest
	if err := context.ShouldBindJSON(&request); err != nil {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: "invalid request body"})
		return
	}

	tokenResponse, err := handler.tokenIssuer.IssueToken(mediamtx.TokenRequest{
		DeviceID: request.DeviceID,
		Identity: request.Identity,
		Role:     mediamtx.RolePublisher,
	})
	if err != nil {
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to issue stream token"})
		return
	}

	if err := handler.ensureConnectionAndDevice(request.DeviceID, tokenResponse); err != nil {
		log.Printf("webrtc session: save connection for device %s: %v", request.DeviceID, err)
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to record session"})
		return
	}

	context.JSON(http.StatusOK, models.WebRTCSessionResponse{
		SessionID:           tokenResponse.Path,
		Token:               tokenResponse.Token,
		URL:                 tokenResponse.WHIPURL,
		WHIPURL:             tokenResponse.WHIPURL,
		WebSocketPublishURL: streamPublishWebSocketURL(context),
		Room:                tokenResponse.Path,
		Path:                tokenResponse.Path,
		Identity:            tokenResponse.Identity,
		ExpiresAt:           tokenResponse.ExpiresAt,
		IceServers:          mediamtx.IceServers(handler.iceOptions, tokenResponse.Identity),
	})
}

type DevicesHandler struct {
	deviceStore store.DeviceStore
	tokenIssuer *mediamtx.TokenIssuer
	webrtcStore store.WebRTCStore
	iceOptions  mediamtx.IceServerOptions
}

func NewDevicesHandler(
	deviceStore store.DeviceStore,
	tokenIssuer *mediamtx.TokenIssuer,
	webrtcStore store.WebRTCStore,
	iceOptions mediamtx.IceServerOptions,
) *DevicesHandler {
	return &DevicesHandler{
		deviceStore: deviceStore,
		tokenIssuer: tokenIssuer,
		webrtcStore: webrtcStore,
		iceOptions:  iceOptions,
	}
}

func (handler *DevicesHandler) ListDevices(context *gin.Context) {
	devices, err := handler.deviceStore.ListDevices()
	if err != nil {
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to list devices"})
		return
	}

	context.JSON(http.StatusOK, devices)
}

func (handler *DevicesHandler) DeleteDevice(context *gin.Context) {
	deviceID := context.Param("id")
	if deviceID == "" {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: "device id is required"})
		return
	}

	if err := handler.deviceStore.DeleteDevice(deviceID); err != nil {
		if errors.Is(err, store.ErrDeviceNotFound) {
			context.JSON(http.StatusNotFound, models.ErrorResponse{Error: "device not found"})
			return
		}
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to delete device"})
		return
	}

	context.Status(http.StatusNoContent)
}

func (handler *DevicesHandler) GetDeviceStream(context *gin.Context) {
	if !handler.tokenIssuer.Enabled() {
		context.JSON(http.StatusServiceUnavailable, models.ErrorResponse{Error: "streaming is not configured"})
		return
	}

	deviceID := context.Param("id")
	if deviceID == "" {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: "device id is required"})
		return
	}

	tokenResponse, err := handler.tokenIssuer.IssueToken(mediamtx.TokenRequest{
		DeviceID: deviceID,
		Role:     mediamtx.RoleSubscriber,
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

	urlWarnings := mediamtx.ClientURLWarnings(tokenResponse.WHEPURL)
	if len(urlWarnings) > 0 {
		log.Printf("stream credentials for device %s: %v", deviceID, urlWarnings)
	}

	_, _ = handler.webrtcStore.SaveConnection(models.WebRTCConnectionRecord{
		DeviceID:    deviceID,
		Room:        tokenResponse.Path,
		Identity:    tokenResponse.Identity,
		Role:        mediamtx.RoleSubscriber,
		ConnectedAt: time.Now().UTC(),
		Status:      "active",
	})

	context.JSON(http.StatusOK, models.DeviceStreamResponse{
		DeviceID:    deviceID,
		Token:       tokenResponse.Token,
		URL:         tokenResponse.WHEPURL,
		WHEPURL:     tokenResponse.WHEPURL,
		HLSURL:      tokenResponse.HLSURL,
		Room:        tokenResponse.Path,
		Path:        tokenResponse.Path,
		Identity:    tokenResponse.Identity,
		ExpiresAt:   tokenResponse.ExpiresAt,
		IceServers:  mediamtx.IceServers(handler.iceOptions, tokenResponse.Identity),
		URLWarnings: urlWarnings,
	})
}
