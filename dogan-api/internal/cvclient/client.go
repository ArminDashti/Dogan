package cvclient

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

type Client struct {
	baseURL    string
	httpClient *http.Client
}

type Analysis struct {
	Analysis string
	Model    string
	Prompt   string
}

func New(baseURL string) *Client {
	return &Client{
		baseURL: strings.TrimRight(strings.TrimSpace(baseURL), "/"),
		httpClient: &http.Client{
			Timeout: 70 * time.Second,
		},
	}
}

func (client *Client) Enabled() bool {
	return client != nil && client.baseURL != ""
}

type frameResponse struct {
	Path  string `json:"path"`
	Error string `json:"error"`
}

func (client *Client) SaveFrame(ctx context.Context, deviceID string, capturedAt time.Time, imageData string) (string, error) {
	if !client.Enabled() {
		return "", fmt.Errorf("dogan-cv is not configured")
	}
	payload := map[string]any{
		"device_id":   deviceID,
		"captured_at": capturedAt.UTC(),
		"image_data":  imageData,
	}
	var parsed frameResponse
	if err := client.post(ctx, "/frames", payload, &parsed); err != nil {
		return "", err
	}
	if parsed.Path == "" {
		return "", fmt.Errorf("dogan-cv returned an empty path")
	}
	return parsed.Path, nil
}

type analyzeResponse struct {
	Analysis string `json:"analysis"`
	Model    string `json:"model"`
	Prompt   string `json:"prompt"`
	Error    string `json:"error"`
}

func (client *Client) Analyze(ctx context.Context, imageBytes []byte, prompt, sessionID string) (Analysis, error) {
	if !client.Enabled() {
		return Analysis{}, fmt.Errorf("dogan-cv is not configured")
	}
	if len(imageBytes) == 0 {
		return Analysis{}, fmt.Errorf("image is empty")
	}
	payload := map[string]string{
		"image_data": base64.StdEncoding.EncodeToString(imageBytes),
		"prompt":     prompt,
		"session_id": sessionID,
		"mime_type":  "image/jpeg",
	}
	var parsed analyzeResponse
	if err := client.post(ctx, "/analyze", payload, &parsed); err != nil {
		return Analysis{}, err
	}
	if strings.TrimSpace(parsed.Analysis) == "" {
		return Analysis{}, fmt.Errorf("dogan-cv returned empty analysis")
	}
	return Analysis{Analysis: parsed.Analysis, Model: parsed.Model, Prompt: parsed.Prompt}, nil
}

func (client *Client) post(ctx context.Context, path string, payload any, dest any) error {
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("marshal dogan-cv request: %w", err)
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, client.baseURL+path, bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("create dogan-cv request: %w", err)
	}
	request.Header.Set("Content-Type", "application/json")
	response, err := client.httpClient.Do(request)
	if err != nil {
		return fmt.Errorf("dogan-cv request failed: %w", err)
	}
	defer response.Body.Close()
	responseBody, err := io.ReadAll(io.LimitReader(response.Body, 1<<20))
	if err != nil {
		return fmt.Errorf("read dogan-cv response: %w", err)
	}
	if err := json.Unmarshal(responseBody, dest); err != nil {
		return fmt.Errorf("decode dogan-cv response (http %d): %w", response.StatusCode, err)
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		var parsed struct {
			Error string `json:"error"`
		}
		_ = json.Unmarshal(responseBody, &parsed)
		if parsed.Error == "" {
			parsed.Error = strings.TrimSpace(string(responseBody))
		}
		return fmt.Errorf("dogan-cv http %d: %s", response.StatusCode, parsed.Error)
	}
	return nil
}
