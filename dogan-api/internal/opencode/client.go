package opencode

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

const (
	defaultBaseURL = "https://pont.xaigrok.ir/api/v1"
	defaultModel   = "opencodego-glm-5.3-flash"
	defaultPrompt  = "Describe this camera image briefly. Note people, vehicles, hazards, or anything unusual."
	defaultTimeout = 60 * time.Second
)

type Client struct {
	baseURL    string
	apiKey     string
	model      string
	prompt     string
	httpClient *http.Client
}

func NewClient(baseURL, apiKey, model, prompt string) *Client {
	if strings.TrimSpace(baseURL) == "" {
		baseURL = defaultBaseURL
	}
	if strings.TrimSpace(model) == "" {
		model = defaultModel
	}
	if strings.TrimSpace(prompt) == "" {
		prompt = defaultPrompt
	}
	return &Client{
		baseURL: strings.TrimRight(baseURL, "/"),
		apiKey:  strings.TrimSpace(apiKey),
		model:   model,
		prompt:  prompt,
		httpClient: &http.Client{
			Timeout: defaultTimeout,
		},
	}
}

func (client *Client) Enabled() bool {
	return client != nil && client.apiKey != ""
}

func (client *Client) Model() string {
	if client == nil {
		return ""
	}
	return client.model
}

func (client *Client) DefaultPrompt() string {
	if client == nil {
		return defaultPrompt
	}
	return client.prompt
}

type chatMessage struct {
	Role    string        `json:"role"`
	Content []contentPart `json:"content"`
}

type contentPart struct {
	Type     string    `json:"type"`
	Text     string    `json:"text,omitempty"`
	ImageURL *imageURL `json:"image_url,omitempty"`
}

type imageURL struct {
	URL string `json:"url"`
}

type chatRequest struct {
	Model     string        `json:"model"`
	MaxTokens int           `json:"max_tokens"`
	Messages  []chatMessage `json:"messages"`
}

type chatResponse struct {
	Choices []struct {
		Message struct {
			Content string `json:"content"`
		} `json:"message"`
	} `json:"choices"`
	Error *struct {
		Message string `json:"message"`
		Type    string `json:"type"`
	} `json:"error"`
}

func (client *Client) AnalyzeImage(ctx context.Context, imageBytes []byte, mimeType, prompt, sessionID string) (string, error) {
	if !client.Enabled() {
		return "", fmt.Errorf("opencode go is not configured")
	}
	if len(imageBytes) == 0 {
		return "", fmt.Errorf("image is empty")
	}
	if mimeType == "" {
		mimeType = "image/jpeg"
	}
	if strings.TrimSpace(prompt) == "" {
		prompt = client.prompt
	}
	if strings.TrimSpace(sessionID) == "" {
		sessionID = "dogan-vision"
	}

	dataURL := fmt.Sprintf("data:%s;base64,%s", mimeType, base64.StdEncoding.EncodeToString(imageBytes))
	payload := chatRequest{
		Model:     client.model,
		MaxTokens: 512,
		Messages: []chatMessage{{
			Role: "user",
			Content: []contentPart{
				{Type: "text", Text: prompt},
				{Type: "image_url", ImageURL: &imageURL{URL: dataURL}},
			},
		}},
	}

	body, err := json.Marshal(payload)
	if err != nil {
		return "", fmt.Errorf("marshal opencode request: %w", err)
	}

	endpoint := client.baseURL + "/chat/completions"
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(body))
	if err != nil {
		return "", fmt.Errorf("create opencode request: %w", err)
	}
	request.Header.Set("Authorization", "Bearer "+client.apiKey)
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("x-opencode-session", sessionID)

	response, err := client.httpClient.Do(request)
	if err != nil {
		return "", fmt.Errorf("opencode request failed: %w", err)
	}
	defer response.Body.Close()

	responseBody, err := io.ReadAll(io.LimitReader(response.Body, 1<<20))
	if err != nil {
		return "", fmt.Errorf("read opencode response: %w", err)
	}

	var parsed chatResponse
	if err := json.Unmarshal(responseBody, &parsed); err != nil {
		return "", fmt.Errorf("decode opencode response (http %d): %w", response.StatusCode, err)
	}
	if parsed.Error != nil && parsed.Error.Message != "" {
		return "", fmt.Errorf("opencode error: %s", parsed.Error.Message)
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return "", fmt.Errorf("opencode http %d: %s", response.StatusCode, strings.TrimSpace(string(responseBody)))
	}
	if len(parsed.Choices) == 0 || strings.TrimSpace(parsed.Choices[0].Message.Content) == "" {
		return "", fmt.Errorf("opencode returned empty analysis")
	}
	return strings.TrimSpace(parsed.Choices[0].Message.Content), nil
}
