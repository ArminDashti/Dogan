package mediamtx

import "testing"

func TestAuthorizeDecisionTable(t *testing.T) {
	issuer := testIssuer()
	publisher, err := issuer.IssueToken(TokenRequest{DeviceID: "Cam-01", Role: RolePublisher})
	if err != nil {
		t.Fatalf("issue publisher token: %v", err)
	}
	subscriber, err := issuer.IssueToken(TokenRequest{DeviceID: "Cam-01", Role: RoleSubscriber})
	if err != nil {
		t.Fatalf("issue subscriber token: %v", err)
	}

	cases := []struct {
		name    string
		request AuthRequest
		allowed bool
	}{
		{
			name:    "publisher token publishes matching path",
			request: AuthRequest{Action: ActionPublish, Path: publisher.Path, Token: publisher.Token},
			allowed: true,
		},
		{
			name:    "publisher token reads matching path",
			request: AuthRequest{Action: ActionRead, Path: publisher.Path, Token: publisher.Token},
			allowed: true,
		},
		{
			name:    "subscriber token reads matching path",
			request: AuthRequest{Action: ActionRead, Path: subscriber.Path, Token: subscriber.Token},
			allowed: true,
		},
		{
			name:    "subscriber token cannot publish",
			request: AuthRequest{Action: ActionPublish, Path: subscriber.Path, Token: subscriber.Token},
			allowed: false,
		},
		{
			name:    "subscriber token can play back",
			request: AuthRequest{Action: ActionPlayback, Path: subscriber.Path, Token: subscriber.Token},
			allowed: true,
		},
		{
			name:    "publisher token cannot play back",
			request: AuthRequest{Action: ActionPlayback, Path: publisher.Path, Token: publisher.Token},
			allowed: false,
		},
		{
			name:    "path mismatch is rejected",
			request: AuthRequest{Action: ActionRead, Path: "device-other", Token: subscriber.Token},
			allowed: false,
		},
		{
			name:    "api action is rejected",
			request: AuthRequest{Action: "api", Path: subscriber.Path, Token: subscriber.Token},
			allowed: false,
		},
		{
			name:    "metrics action is rejected",
			request: AuthRequest{Action: "metrics", Path: subscriber.Path, Token: subscriber.Token},
			allowed: false,
		},
		{
			name:    "pprof action is rejected",
			request: AuthRequest{Action: "pprof", Path: subscriber.Path, Token: subscriber.Token},
			allowed: false,
		},
		{
			name:    "missing token is rejected",
			request: AuthRequest{Action: ActionRead, Path: subscriber.Path},
			allowed: false,
		},
		{
			name:    "password field is accepted as token",
			request: AuthRequest{Action: ActionRead, Path: subscriber.Path, Password: subscriber.Token},
			allowed: true,
		},
		{
			name:    "token query parameter is accepted",
			request: AuthRequest{Action: ActionRead, Path: subscriber.Path, Query: "token=" + subscriber.Token},
			allowed: true,
		},
		{
			name:    "jwt query parameter is accepted",
			request: AuthRequest{Action: ActionRead, Path: subscriber.Path, Query: "foo=bar&jwt=" + subscriber.Token},
			allowed: true,
		},
		{
			name:    "invalid token is rejected",
			request: AuthRequest{Action: ActionRead, Path: subscriber.Path, Token: "not-a-token"},
			allowed: false,
		},
	}

	for _, testCase := range cases {
		t.Run(testCase.name, func(t *testing.T) {
			allowed := issuer.Authorize(testCase.request)
			if allowed != testCase.allowed {
				t.Fatalf("expected allowed=%v, got %v", testCase.allowed, allowed)
			}
		})
	}
}

func TestAuthorizeRequiresConfiguration(t *testing.T) {
	issuer := NewTokenIssuer(Config{WebRTCPublicURL: "https://dogan-media.xaigrok.ir"})
	if issuer.Authorize(AuthRequest{Action: ActionRead, Path: "device-cam", Token: "token"}) {
		t.Fatal("expected unconfigured issuer to deny all requests")
	}
}
