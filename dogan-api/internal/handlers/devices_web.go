package handlers

import (
	"errors"
	"fmt"
	"net/http"
	"strconv"
	"time"

	"github.com/dogan/dogan-server/internal/models"
	"github.com/dogan/dogan-server/internal/store"
	"github.com/gin-gonic/gin"
)

type DevicesWebHandler struct {
	metricsStore store.MetricsStore
	frameStore   store.FrameStore
	actionStore  store.ActionStore
	deviceStore  store.DeviceStore
}

func NewDevicesWebHandler(
	metricsStore store.MetricsStore,
	frameStore store.FrameStore,
	actionStore store.ActionStore,
	deviceStore store.DeviceStore,
) *DevicesWebHandler {
	return &DevicesWebHandler{
		metricsStore: metricsStore,
		frameStore:   frameStore,
		actionStore:  actionStore,
		deviceStore:  deviceStore,
	}
}

func (handler *DevicesWebHandler) GetDeviceTelemetry(context *gin.Context) {
	deviceID := context.Param("id")
	if deviceID == "" {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: "device id is required"})
		return
	}

	metrics, err := handler.metricsStore.GetLatestMetrics(deviceID)
	if err != nil {
		if errors.Is(err, store.ErrMetricsNotFound) {
			context.JSON(http.StatusNotFound, models.ErrorResponse{Error: "no telemetry found for device"})
			return
		}
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to retrieve telemetry"})
		return
	}

	context.JSON(http.StatusOK, toTelemetrySnapshot(metrics))
}

func (handler *DevicesWebHandler) GetDeviceMetrics(context *gin.Context) {
	deviceID := context.Param("id")
	if deviceID == "" {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: "device id is required"})
		return
	}

	from, to := metricsRangeBounds(context.Query("range"))
	history, err := handler.metricsStore.ListMetricsHistory(deviceID, from, to, 500)
	if err != nil {
		if errors.Is(err, store.ErrMetricsNotFound) {
			context.JSON(http.StatusNotFound, models.ErrorResponse{Error: "no metrics found for device"})
			return
		}
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to retrieve metrics"})
		return
	}

	deviceName, _ := handler.deviceStore.GetDeviceName(deviceID)
	if deviceName == "" {
		deviceName = deviceID
	}

	response := models.DeviceMetricsHistory{
		DeviceID:   deviceID,
		DeviceName: deviceName,
		History:    make([]models.MetricReading, 0, len(history)),
	}
	latest := history[0]
	current := toMetricReading(latest)
	response.Current.BatteryPercent = current.BatteryPercent
	response.Current.BatteryTemperatureCelsius = current.BatteryTemperatureCelsius
	response.Current.NoiseDb = current.NoiseDb
	response.Current.Jolt = current.Jolt
	response.Current.SignalStrength = current.SignalStrength
	response.Current.NetworkType = current.NetworkType
	response.Current.ServerPhoneLatencyMs = current.ServerPhoneLatencyMs
	response.Current.RecordedAt = latest.RecordedAt

	for _, item := range history {
		response.History = append(response.History, toMetricReading(item))
	}

	context.JSON(http.StatusOK, response)
}

func (handler *DevicesWebHandler) CaptureDeviceFrame(context *gin.Context) {
	deviceID := context.Param("id")
	if deviceID == "" {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: "device id is required"})
		return
	}

	_, _ = handler.actionStore.CreateAction(models.PhoneActionRecord{
		DeviceID:   deviceID,
		ActionType: "capture",
		Payload:    map[string]any{"source": "web"},
		SentAt:     time.Now().UTC(),
		Status:     "pending",
	})

	frame, err := handler.frameStore.GetLastFrame(deviceID)
	if err != nil {
		if errors.Is(err, store.ErrFrameNotFound) {
			context.JSON(http.StatusAccepted, models.CaptureResponse{
				ImageID:    "",
				CapturedAt: time.Now().UTC(),
			})
			return
		}
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to retrieve frame"})
		return
	}

	baseURL := publicAPIBase(context)
	context.JSON(http.StatusOK, models.CaptureResponse{
		ImageID:    strconv.FormatInt(frame.ID, 10),
		URL:        fmt.Sprintf("%s/images/%d", baseURL, frame.ID),
		CapturedAt: frame.CapturedAt,
	})
}

func (handler *DevicesWebHandler) ListImages(context *gin.Context) {
	frames, err := handler.frameStore.ListFrames(100)
	if err != nil {
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to list images"})
		return
	}

	baseURL := publicAPIBase(context)
	images := make([]models.GalleryImage, 0, len(frames))
	for _, frame := range frames {
		imageURL := fmt.Sprintf("%s/images/%d", baseURL, frame.ID)
		images = append(images, models.GalleryImage{
			ID:           strconv.FormatInt(frame.ID, 10),
			URL:          imageURL,
			ThumbnailURL: imageURL,
			Caption:      frame.DeviceID,
			CapturedAt:   frame.CapturedAt,
			DeviceID:     frame.DeviceID,
		})
	}

	context.JSON(http.StatusOK, images)
}

func (handler *DevicesWebHandler) GetImage(context *gin.Context) {
	rawID := context.Param("id")
	imageID, err := strconv.ParseInt(rawID, 10, 64)
	if err != nil {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: "invalid image id"})
		return
	}

	frame, err := handler.frameStore.GetFrameByID(imageID)
	if err != nil {
		if errors.Is(err, store.ErrFrameNotFound) {
			context.JSON(http.StatusNotFound, models.ErrorResponse{Error: "image not found"})
			return
		}
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to retrieve image"})
		return
	}

	context.File(frame.Path)
}

func toTelemetrySnapshot(metrics models.DeviceMetricsRecord) models.DeviceTelemetrySnapshot {
	return models.DeviceTelemetrySnapshot{
		DeviceID:                  metrics.DeviceID,
		BatteryPercent:            floatOrZero(metrics.BatteryLevel),
		BatteryTemperatureCelsius: floatOrZero(metrics.TemperatureC),
		NoiseDb:                   floatOrZero(metrics.CabinNoiseRMS),
		Jolt:                      floatOrZero(metrics.Jolt),
		SignalStrength:            float64(intOrZero(metrics.SignalStrength)),
		NetworkType:               metrics.NetworkType,
		ServerPhoneLatencyMs:      float64(intOrZero(metrics.ServerLatencyMs)),
		ServerWebLatencyMs:        0,
		RecordedAt:                metrics.RecordedAt,
	}
}

func toMetricReading(item models.DeviceMetricsRecord) models.MetricReading {
	return models.MetricReading{
		Timestamp:                 item.RecordedAt,
		BatteryPercent:            floatOrZero(item.BatteryLevel),
		BatteryTemperatureCelsius: floatOrZero(item.TemperatureC),
		NoiseDb:                   floatOrZero(item.CabinNoiseRMS),
		Jolt:                      floatOrZero(item.Jolt),
		SignalStrength:            float64(intOrZero(item.SignalStrength)),
		NetworkType:               item.NetworkType,
		ServerPhoneLatencyMs:      float64(intOrZero(item.ServerLatencyMs)),
	}
}

func metricsRangeBounds(rangeKey string) (time.Time, time.Time) {
	now := time.Now().UTC()
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, time.UTC)
	switch rangeKey {
	case "yesterday":
		return today.AddDate(0, 0, -1), today
	case "this_week":
		weekday := int(today.Weekday())
		if weekday == 0 {
			weekday = 7
		}
		start := today.AddDate(0, 0, -(weekday - 1))
		return start, today.AddDate(0, 0, 1)
	case "last_week":
		weekday := int(today.Weekday())
		if weekday == 0 {
			weekday = 7
		}
		thisWeekStart := today.AddDate(0, 0, -(weekday - 1))
		return thisWeekStart.AddDate(0, 0, -7), thisWeekStart
	case "this_month":
		start := time.Date(now.Year(), now.Month(), 1, 0, 0, 0, 0, time.UTC)
		return start, today.AddDate(0, 0, 1)
	case "last_month":
		firstThisMonth := time.Date(now.Year(), now.Month(), 1, 0, 0, 0, 0, time.UTC)
		return firstThisMonth.AddDate(0, -1, 0), firstThisMonth
	default:
		return today, today.AddDate(0, 0, 1)
	}
}

func floatOrZero(value *float64) float64 {
	if value == nil {
		return 0
	}
	return *value
}

func intOrZero(value *int) int {
	if value == nil {
		return 0
	}
	return *value
}
