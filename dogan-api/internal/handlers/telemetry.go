package handlers

import (
	"context"
	"log"
	"net/http"
	"time"

	"github.com/dogan/dogan-server/internal/cvclient"
	"github.com/dogan/dogan-server/internal/models"
	"github.com/dogan/dogan-server/internal/store"
	"github.com/gin-gonic/gin"
)

type TelemetryHandler struct {
	metricsStore store.MetricsStore
	frameStore   store.FrameStore
	cv           *cvclient.Client
	vision       *VisionHandler
	framesDir    string
}

func NewTelemetryHandler(
	metricsStore store.MetricsStore,
	frameStore store.FrameStore,
	cv *cvclient.Client,
	vision *VisionHandler,
	framesDir string,
) *TelemetryHandler {
	return &TelemetryHandler{
		metricsStore: metricsStore,
		frameStore:   frameStore,
		cv:           cv,
		vision:       vision,
		framesDir:    framesDir,
	}
}

func (handler *TelemetryHandler) SubmitTelemetry(context *gin.Context) {
	var payload models.TelemetryPayload
	if err := context.ShouldBindJSON(&payload); err != nil {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: "invalid request body"})
		return
	}

	recordedAt := store.NormalizeRecordedAt(payload.RecordedAt)
	batteryLevel := float64(payload.BatteryPercentage)
	tempC := payload.BatteryTemperatureC
	cabinNoise := payload.CabinNoiseRMS
	speed := payload.SpeedKmh
	ambient := payload.AmbientLightLux
	latency := payload.ServerLatencyMs
	signal := payload.NetworkSignalStrengthDbm

	sim1 := payload.Sim1SignalDbm
	if sim1 == nil {
		sim1 = &signal
	}
	sim2 := payload.Sim2SignalDbm

	var latitude *float64
	var longitude *float64
	if payload.GPSLocation != nil {
		lat := payload.GPSLocation.Latitude
		lon := payload.GPSLocation.Longitude
		latitude = &lat
		longitude = &lon
	}

	jolt := payload.Jolt
	record := models.DeviceMetricsRecord{
		DeviceID:         payload.DeviceID,
		BatteryLevel:     &batteryLevel,
		SignalStrength:   &signal,
		Sim1SignalDbm:    sim1,
		Sim2SignalDbm:    sim2,
		NetworkType:      payload.NetworkType,
		TemperatureC:     &tempC,
		Latitude:         latitude,
		Longitude:        longitude,
		CabinNoiseRMS:    &cabinNoise,
		GPSSignalQuality: payload.GPSSignalQuality,
		SpeedKmh:         &speed,
		AmbientLightLux:  &ambient,
		ServerLatencyMs:  &latency,
		DeviceIPAddress:  payload.DeviceIPAddress,
		Jolt:             &jolt,
		CPUUsagePercent:  payload.CPUUsagePercent,
		RAMUsagePercent:  payload.RAMUsagePercent,
		RecordedAt:       recordedAt,
		ReceivedAt:       time.Now().UTC(),
	}

	if err := handler.metricsStore.SaveMetrics(record); err != nil {
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to save telemetry"})
		return
	}

	handler.persistOptionalFrame(payload.DeviceID, recordedAt, payload.RearCameraFrameBase64)
	handler.persistOptionalFrame(payload.DeviceID, recordedAt, payload.FrontCameraFrameBase64)

	context.Status(http.StatusNoContent)
}

func (handler *TelemetryHandler) persistOptionalFrame(deviceID string, capturedAt time.Time, imageData string) {
	if imageData == "" {
		return
	}

	var framePath string
	var err error
	if handler.cv != nil && handler.cv.Enabled() {
		framePath, err = handler.cv.SaveFrame(context.Background(), deviceID, capturedAt, imageData)
		if err != nil {
			log.Printf("telemetry: dogan-cv save failed for device %s: %v", deviceID, err)
		}
	}
	if framePath == "" && handler.framesDir != "" {
		framePath, err = store.PersistFrameImage(handler.framesDir, deviceID, capturedAt, imageData)
		if err != nil {
			log.Printf("telemetry: local frame save failed for device %s: %v", deviceID, err)
			return
		}
	}
	if framePath == "" {
		log.Printf("telemetry: frame not stored for device %s (dogan-cv disabled and no frames dir)", deviceID)
		return
	}

	_ = handler.frameStore.SaveFrame(models.FrameRecord{
		DeviceID:   deviceID,
		Path:       framePath,
		CapturedAt: capturedAt,
		ReceivedAt: time.Now().UTC(),
	})
	if _, thumbErr := store.EnsureThumbnail(framePath); thumbErr != nil {
		log.Printf("telemetry: thumbnail for device %s: %v", deviceID, thumbErr)
	}

	if handler.vision != nil && handler.vision.Enabled() {
		var imageID *int64
		if last, err := handler.frameStore.GetLastFrame(deviceID); err == nil && last.Path == framePath {
			id := last.ID
			imageID = &id
		}
		handler.vision.AnalyzeFrameAsync(deviceID, framePath, imageID)
	}
}
