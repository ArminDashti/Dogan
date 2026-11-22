package mediamtx

import (
	"net/url"
	"strings"
)

type AuthRequest struct {
	IP        string `json:"ip"`
	User      string `json:"user"`
	Password  string `json:"password"`
	Token     string `json:"token"`
	Action    string `json:"action"`
	Path      string `json:"path"`
	Protocol  string `json:"protocol"`
	ID        string `json:"id"`
	Query     string `json:"query"`
	UserAgent string `json:"userAgent"`
}

func (request AuthRequest) tokenValue() string {
	if token := strings.TrimSpace(request.Token); token != "" {
		return token
	}
	if password := strings.TrimSpace(request.Password); password != "" {
		return password
	}

	query, err := url.ParseQuery(request.Query)
	if err != nil {
		return ""
	}
	for _, key := range []string{"token", "jwt"} {
		if value := strings.TrimSpace(query.Get(key)); value != "" {
			return value
		}
	}

	return ""
}

func (issuer *TokenIssuer) Authorize(request AuthRequest) bool {
	if !issuer.config.Enabled() {
		return false
	}

	switch request.Action {
	case ActionPublish, ActionRead, ActionPlayback:
	default:
		return false
	}

	tokenString := request.tokenValue()
	if tokenString == "" {
		return false
	}

	claims, err := issuer.ValidateToken(tokenString)
	if err != nil {
		return false
	}
	if claims.Path == "" || claims.Path != request.Path {
		return false
	}

	switch request.Action {
	case ActionPublish:
		return claims.Action == ActionPublish
	case ActionRead:
		return claims.Action == ActionRead || claims.Action == ActionPublish
	default:
		return claims.Action == ActionRead
	}
}
