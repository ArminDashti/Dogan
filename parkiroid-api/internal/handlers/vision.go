package handlers

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/dogan/dogan-server/internal/models"
	"github.com/dogan/dogan-server/internal/opencode"
	"github.com/dogan/dogan-server/internal/store"
	"github.com/gin-gonic/gin"
)

type VisionHandler struct {
	client     *opencode.Client
	frameStore store.FrameStore
	visionStore store.VisionStore
	framesDir  string
}

func NewVisionHandler(client *opencode.Client, frameStore store.FrameStore, visionStore store.VisionStore, framesDir string) *VisionHandler {
	return &VisionHandler{
		client:      client,
		frameStore:  frameStore,
		visionStore: visionStore,
		framesDir:   framesDir,
	}
}

func (handler *VisionHandler) Enabled() bool {
	return handler != nil && handler.client != nil && handler.client.Enabled()
}

func (handler *VisionHandler) Analyze(context *gin.Context) {
	if !handler.Enabled() {
		context.JSON(http.StatusServiceUnavailable, models.ErrorResponse{Error: "opencode go vision is not configured"})
		return
	}

	var payload models.VisionAnalyzeRequest
	if err := context.ShouldBindJSON(&payload); err != nil {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: "invalid request body"})
		return
	}

	imageBytes, deviceID, imageID, err := handler.resolveImage(payload)
	if err != nil {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: err.Error()})
		return
	}

	analysis, err := handler.analyzeAndStore(context.Request.Context(), deviceID, imageID, imageBytes, payload.Prompt)
	if err != nil {
		context.JSON(http.StatusBadGateway, models.ErrorResponse{Error: err.Error()})
		return
	}

	context.JSON(http.StatusOK, analysis)
}

func (handler *VisionHandler) GetLatest(context *gin.Context) {
	deviceID := strings.TrimSpace(context.Query("device-id"))
	if deviceID == "" {
		context.JSON(http.StatusBadRequest, models.ErrorResponse{Error: "device-id query parameter is required"})
		return
	}

	record, err := handler.visionStore.GetLatestVisionAnalysis(deviceID)
	if err != nil {
		if errors.Is(err, store.ErrVisionAnalysisNotFound) {
			context.JSON(http.StatusNotFound, models.ErrorResponse{Error: "no vision analysis found for device"})
			return
		}
		context.JSON(http.StatusInternalServerError, models.ErrorResponse{Error: "failed to retrieve vision analysis"})
		return
	}

	response := models.VisionAnalyzeResponse{
		DeviceID:   record.DeviceID,
		Model:      record.Model,
		Analysis:   record.Analysis,
		AnalyzedAt: record.AnalyzedAt,
	}
	if record.ImageID != nil {
		response.ImageID = *record.ImageID
	}
	context.JSON(http.StatusOK, response)
}

func (handler *VisionHandler) AnalyzeFrameAsync(deviceID, framePath string, imageID *int64) {
	if !handler.Enabled() || framePath == "" {
		return
	}

	go func() {
		imageBytes, err := os.ReadFile(framePath)
		if err != nil {
			log.Printf("vision async read frame failed device=%s path=%s err=%v", deviceID, framePath, err)
			return
		}
		ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
		defer cancel()
		if _, err := handler.analyzeAndStore(ctx, deviceID, imageID, imageBytes, ""); err != nil {
			log.Printf("vision async analyze failed device=%s err=%v", deviceID, err)
		}
	}()
}

func (handler *VisionHandler) resolveImage(payload models.VisionAnalyzeRequest) ([]byte, string, *int64, error) {
	if payload.ImageID > 0 {
		frame, err := handler.frameStore.GetFrameByID(payload.ImageID)
		if err != nil {
			if errors.Is(err, store.ErrFrameNotFound) {
				return nil, "", nil, errors.New("image not found")
			}
			return nil, "", nil, errors.New("failed to load image")
		}
		imageBytes, err := os.ReadFile(frame.Path)
		if err != nil {
			return nil, "", nil, errors.New("failed to read image file")
		}
		imageID := frame.ID
		deviceID := frame.DeviceID
		if deviceID == "" {
			deviceID = strings.TrimSpace(payload.DeviceID)
		}
		return imageBytes, deviceID, &imageID, nil
	}

	if strings.TrimSpace(payload.ImageBase64) == "" {
		return nil, "", nil, errors.New("image_id or image_base64 is required")
	}
	imageBytes, err := store.DecodeImagePayload(payload.ImageBase64)
	if err != nil {
		return nil, "", nil, errors.New("invalid image_base64")
	}
	return imageBytes, strings.TrimSpace(payload.DeviceID), nil, nil
}

func (handler *VisionHandler) analyzeAndStore(ctx context.Context, deviceID string, imageID *int64, imageBytes []byte, prompt string) (models.VisionAnalyzeResponse, error) {
	sessionID := "dogan-vision"
	if deviceID != "" {
		sessionID = "dogan-" + store.SanitizeDeviceSegment(deviceID)
	}

	analysis, err := handler.client.AnalyzeImage(ctx, imageBytes, "image/jpeg", prompt, sessionID)
	if err != nil {
		return models.VisionAnalyzeResponse{}, err
	}

	response := models.VisionAnalyzeResponse{
		DeviceID:   deviceID,
		Model:      handler.client.Model(),
		Analysis:   analysis,
		AnalyzedAt: time.Now().UTC(),
	}
	if imageID != nil {
		response.ImageID = *imageID
	}

	if deviceID == "" || handler.visionStore == nil {
		return response, nil
	}

	record := models.VisionAnalysisRecord{
		DeviceID:   deviceID,
		ImageID:    imageID,
		Model:      response.Model,
		Prompt:     prompt,
		Analysis:   analysis,
		AnalyzedAt: response.AnalyzedAt,
	}
	if strings.TrimSpace(record.Prompt) == "" {
		record.Prompt = handler.client.DefaultPrompt()
	}
	saved, err := handler.visionStore.SaveVisionAnalysis(record)
	if err != nil {
		log.Printf("vision save analysis failed device=%s err=%v", deviceID, err)
		return response, nil
	}
	response.AnalyzedAt = saved.AnalyzedAt
	return response, nil
}
