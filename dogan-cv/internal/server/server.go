package server

import (
	"encoding/json"
	"errors"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/dogan/dogan-cv/internal/frames"
	"github.com/dogan/dogan-cv/internal/pont"
)

type Server struct {
	framesDir string
	pont      *pont.Client
}

func New(framesDir string, client *pont.Client) *Server {
	return &Server{framesDir: framesDir, pont: client}
}

func (server *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", server.health)
	mux.HandleFunc("POST /frames", server.saveFrame)
	mux.HandleFunc("POST /analyze", server.analyze)
	return mux
}

func (server *Server) health(writer http.ResponseWriter, _ *http.Request) {
	writeJSON(writer, http.StatusOK, map[string]any{
		"status":  "ok",
		"vision":  server.pont.Enabled(),
		"service": "dogan-cv",
	})
}

type frameRequest struct {
	DeviceID   string    `json:"device_id"`
	CapturedAt time.Time `json:"captured_at"`
	ImageData  string    `json:"image_data"`
}

func (server *Server) saveFrame(writer http.ResponseWriter, request *http.Request) {
	var payload frameRequest
	if err := json.NewDecoder(request.Body).Decode(&payload); err != nil {
		writeError(writer, http.StatusBadRequest, "invalid request body")
		return
	}
	capturedAt := payload.CapturedAt
	if capturedAt.IsZero() {
		capturedAt = time.Now().UTC()
	}
	path, err := frames.Persist(server.framesDir, payload.DeviceID, capturedAt, payload.ImageData)
	if err != nil {
		writeError(writer, http.StatusBadRequest, err.Error())
		return
	}
	writeJSON(writer, http.StatusCreated, map[string]string{"path": path})
}

type analyzeRequest struct {
	Path      string `json:"path"`
	ImageData string `json:"image_data"`
	Prompt    string `json:"prompt"`
	SessionID string `json:"session_id"`
	MimeType  string `json:"mime_type"`
}

type analyzeResponse struct {
	Analysis string `json:"analysis"`
	Model    string `json:"model"`
	Prompt   string `json:"prompt"`
}

func (server *Server) analyze(writer http.ResponseWriter, request *http.Request) {
	if !server.pont.Enabled() {
		writeError(writer, http.StatusServiceUnavailable, "opencode go is not configured")
		return
	}
	var payload analyzeRequest
	if err := json.NewDecoder(request.Body).Decode(&payload); err != nil {
		writeError(writer, http.StatusBadRequest, "invalid request body")
		return
	}

	imageBytes, err := server.loadImage(payload)
	if err != nil {
		writeError(writer, http.StatusBadRequest, err.Error())
		return
	}

	prompt := payload.Prompt
	if strings.TrimSpace(prompt) == "" {
		prompt = server.pont.DefaultPrompt()
	}
	analysis, err := server.pont.AnalyzeImage(request.Context(), imageBytes, payload.MimeType, prompt, payload.SessionID)
	if err != nil {
		writeError(writer, http.StatusBadGateway, err.Error())
		return
	}
	writeJSON(writer, http.StatusOK, analyzeResponse{
		Analysis: analysis,
		Model:    server.pont.Model(),
		Prompt:   prompt,
	})
}

func (server *Server) loadImage(payload analyzeRequest) ([]byte, error) {
	if strings.TrimSpace(payload.ImageData) != "" {
		return frames.Decode(payload.ImageData)
	}
	path := strings.TrimSpace(payload.Path)
	if path == "" {
		return nil, errors.New("image_data or path is required")
	}
	relative, err := filepath.Rel(server.framesDir, path)
	if err != nil || relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator)) {
		return nil, errors.New("path is outside frames directory")
	}
	imageBytes, err := os.ReadFile(path)
	if err != nil {
		return nil, errors.New("failed to read image file")
	}
	return imageBytes, nil
}

func writeError(writer http.ResponseWriter, status int, message string) {
	writeJSON(writer, status, map[string]string{"error": message})
}

func writeJSON(writer http.ResponseWriter, status int, payload any) {
	writer.Header().Set("Content-Type", "application/json")
	writer.WriteHeader(status)
	_ = json.NewEncoder(writer).Encode(payload)
}
