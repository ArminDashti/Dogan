package mediamtx

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"
)

const controlRequestTimeout = 3 * time.Second

type PathInfo struct {
	Name         string `json:"name"`
	Ready        bool   `json:"ready"`
	SourceType   string `json:"source_type,omitempty"`
	ReadersCount int    `json:"readers_count"`
}

type ControlClient struct {
	apiURL     string
	httpClient *http.Client
}

type controlPath struct {
	Name      string `json:"name"`
	Ready     bool   `json:"ready"`
	Available bool   `json:"available"`
	Source    struct {
		Type string `json:"type"`
	} `json:"source"`
	Readers []json.RawMessage `json:"readers"`
}

func NewControlClient(apiURL string) *ControlClient {
	return &ControlClient{
		apiURL:     strings.TrimRight(strings.TrimSpace(apiURL), "/"),
		httpClient: &http.Client{Timeout: controlRequestTimeout},
	}
}

func (client *ControlClient) ListPaths() ([]PathInfo, error) {
	request, err := http.NewRequest(http.MethodGet, client.apiURL+"/v3/paths/list", nil)
	if err != nil {
		return nil, fmt.Errorf("create media control request: %w", err)
	}

	response, err := client.httpClient.Do(request)
	if err != nil {
		return nil, fmt.Errorf("query media control api: %w", err)
	}
	defer response.Body.Close()

	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("media control api returned status %d", response.StatusCode)
	}

	var payload struct {
		Items []controlPath `json:"items"`
	}
	if err := json.NewDecoder(io.LimitReader(response.Body, 1<<20)).Decode(&payload); err != nil {
		return nil, fmt.Errorf("decode media control response: %w", err)
	}

	paths := make([]PathInfo, 0, len(payload.Items))
	for _, item := range payload.Items {
		paths = append(paths, PathInfo{
			Name:         item.Name,
			Ready:        item.Ready || item.Available,
			SourceType:   item.Source.Type,
			ReadersCount: len(item.Readers),
		})
	}

	return paths, nil
}
