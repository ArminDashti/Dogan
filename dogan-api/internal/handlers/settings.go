package handlers

import (
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/dogan/dogan-server/internal/models"
	"github.com/dogan/dogan-server/internal/store"
	"github.com/gin-gonic/gin"
)

type SettingsHandler struct {
	settingsStore store.SettingsStore
}

func NewSettingsHandler(settingsStore store.SettingsStore) *SettingsHandler {
	return &SettingsHandler{settingsStore: settingsStore}
}

func (handler *SettingsHandler) GetSettings(context *gin.Context) {
	platform := context.Query("platform")
	deviceID := context.Query("device_id")
	if platform == "" && deviceID != "" {
		platform = "android"
	}
	if platform == "" {
		platform = "web"
	}

	settings, err := handler.settingsStore.GetSettings(platform)
	if err != nil {
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to retrieve settings"})
		return
	}

	if platform == "web" && deviceID == "" && context.Query("platform") == "" {
		context.JSON(http.StatusOK, handler.webSettingsResponse(settings))
		return
	}

	if deviceID != "" {
		flat := settingsToFlatMap(settings)
		flat["device_id"] = deviceID
		context.JSON(http.StatusOK, flat)
		return
	}

	if platform == "web" {
		context.JSON(http.StatusOK, handler.webSettingsResponse(settings))
		return
	}

	context.JSON(http.StatusOK, gin.H{"settings": settings})
}

func (handler *SettingsHandler) UpsertSetting(context *gin.Context) {
	var payload models.AppSettingPayload
	if err := context.ShouldBindJSON(&payload); err != nil {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: "invalid request body"})
		return
	}

	setting := models.AppSettingRecord{
		Platform:  payload.Platform,
		Key:       payload.Key,
		Value:     payload.Value,
		UpdatedAt: time.Now().UTC(),
	}

	if err := handler.settingsStore.UpsertSetting(setting); err != nil {
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to save setting"})
		return
	}

	context.JSON(http.StatusOK, setting)
}

func (handler *SettingsHandler) PatchWebSettings(context *gin.Context) {
	var payload models.WebSettingsPayload
	if err := context.ShouldBindJSON(&payload); err != nil {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: "invalid request body"})
		return
	}

	now := time.Now().UTC()
	upserts := make([]models.AppSettingRecord, 0, 4)

	if payload.NotificationsEnabled != nil {
		upserts = append(upserts, models.AppSettingRecord{
			Platform:  "web",
			Key:       "notifications_enabled",
			Value:     strconv.FormatBool(*payload.NotificationsEnabled),
			UpdatedAt: now,
		})
	}
	if payload.TemperatureUnit != nil {
		upserts = append(upserts, models.AppSettingRecord{
			Platform:  "web",
			Key:       "temperature_unit",
			Value:     *payload.TemperatureUnit,
			UpdatedAt: now,
		})
	}
	if payload.NoiseAlertThresholdDb != nil {
		upserts = append(upserts, models.AppSettingRecord{
			Platform:  "web",
			Key:       "noise_alert_threshold_db",
			Value:     fmt.Sprintf("%v", *payload.NoiseAlertThresholdDb),
			UpdatedAt: now,
		})
	}
	if payload.DefaultDeviceID != nil {
		upserts = append(upserts, models.AppSettingRecord{
			Platform:  "web",
			Key:       "default_device_id",
			Value:     *payload.DefaultDeviceID,
			UpdatedAt: now,
		})
	}
	upserts = append(upserts, sensorIntervalRecords(payload, now)...)

	for _, setting := range upserts {
		if err := handler.settingsStore.UpsertSetting(setting); err != nil {
			context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to save settings"})
			return
		}
	}

	settings, err := handler.settingsStore.GetSettings("web")
	if err != nil {
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to retrieve settings"})
		return
	}

	context.JSON(http.StatusOK, handler.webSettingsResponse(settings))
}

func (handler *SettingsHandler) webSettingsResponse(webSettings []models.AppSettingRecord) models.WebSettingsResponse {
	androidSettings, err := handler.settingsStore.GetSettings("android")
	if err != nil {
		androidSettings = nil
	}
	return toWebSettingsResponse(webSettings, androidSettings)
}

func toWebSettingsResponse(webSettings []models.AppSettingRecord, androidSettings []models.AppSettingRecord) models.WebSettingsResponse {
	response := models.WebSettingsResponse{
		NotificationsEnabled:   true,
		TemperatureUnit:        "celsius",
		NoiseAlertThresholdDb:  70,
		GpsIntervalSec:         60,
		BatteryIntervalSec:     60,
		TemperatureIntervalSec: 60,
		NoiseIntervalSec:       1,
		JoltIntervalSec:        1,
		NetworkTypeIntervalSec: 60,
		Sim1SignalIntervalSec:  30,
		Sim2SignalIntervalSec:  30,
		LatencyIntervalSec:     1,
		ImageIntervalSec:       5,
	}

	for _, setting := range webSettings {
		switch setting.Key {
		case "notifications_enabled", "notificationsEnabled":
			parsed, err := strconv.ParseBool(setting.Value)
			if err == nil {
				response.NotificationsEnabled = parsed
			}
		case "temperature_unit", "temperatureUnit":
			if setting.Value != "" {
				response.TemperatureUnit = setting.Value
			}
		case "noise_alert_threshold_db", "noiseAlertThresholdDb":
			parsed, err := strconv.ParseFloat(setting.Value, 64)
			if err == nil {
				response.NoiseAlertThresholdDb = parsed
			}
		case "default_device_id", "defaultDeviceId":
			response.DefaultDeviceID = setting.Value
		}
	}

	applySensorIntervals(&response, androidSettings)
	return response
}

func clampIntervalSec(seconds int) int {
	if seconds < 1 {
		return 1
	}
	if seconds > 3600 {
		return 3600
	}
	return seconds
}

func sensorIntervalRecords(payload models.WebSettingsPayload, now time.Time) []models.AppSettingRecord {
	pairs := []struct {
		key   string
		value *int
	}{
		{"gps_interval_sec", payload.GpsIntervalSec},
		{"battery_interval_sec", payload.BatteryIntervalSec},
		{"temperature_interval_sec", payload.TemperatureIntervalSec},
		{"noise_interval_sec", payload.NoiseIntervalSec},
		{"jolt_interval_sec", payload.JoltIntervalSec},
		{"network_type_interval_sec", payload.NetworkTypeIntervalSec},
		{"sim1_signal_interval_sec", payload.Sim1SignalIntervalSec},
		{"sim2_signal_interval_sec", payload.Sim2SignalIntervalSec},
		{"latency_interval_sec", payload.LatencyIntervalSec},
		{"image_interval_sec", payload.ImageIntervalSec},
	}
	records := make([]models.AppSettingRecord, 0, len(pairs))
	for _, pair := range pairs {
		if pair.value == nil {
			continue
		}
		records = append(records, models.AppSettingRecord{
			Platform:  "android",
			Key:       pair.key,
			Value:     strconv.Itoa(clampIntervalSec(*pair.value)),
			UpdatedAt: now,
		})
		if pair.key == "image_interval_sec" {
			seconds := clampIntervalSec(*pair.value)
			records = append(records,
				models.AppSettingRecord{
					Platform:  "android",
					Key:       "capture_interval_ms",
					Value:     strconv.Itoa(seconds * 1000),
					UpdatedAt: now,
				},
				models.AppSettingRecord{
					Platform:  "android",
					Key:       "watcher_image_upload_policy",
					Value:     "auto",
					UpdatedAt: now,
				},
			)
		}
	}
	return records
}

func applySensorIntervals(response *models.WebSettingsResponse, settings []models.AppSettingRecord) {
	for _, setting := range settings {
		parsed, err := strconv.Atoi(strings.TrimSpace(setting.Value))
		if err != nil {
			continue
		}
		parsed = clampIntervalSec(parsed)
		switch setting.Key {
		case "gps_interval_sec":
			response.GpsIntervalSec = parsed
		case "battery_interval_sec":
			response.BatteryIntervalSec = parsed
		case "temperature_interval_sec":
			response.TemperatureIntervalSec = parsed
		case "noise_interval_sec":
			response.NoiseIntervalSec = parsed
		case "jolt_interval_sec":
			response.JoltIntervalSec = parsed
		case "network_type_interval_sec":
			response.NetworkTypeIntervalSec = parsed
		case "sim1_signal_interval_sec":
			response.Sim1SignalIntervalSec = parsed
		case "sim2_signal_interval_sec":
			response.Sim2SignalIntervalSec = parsed
		case "latency_interval_sec":
			response.LatencyIntervalSec = parsed
		case "image_interval_sec":
			response.ImageIntervalSec = parsed
		}
	}
}
