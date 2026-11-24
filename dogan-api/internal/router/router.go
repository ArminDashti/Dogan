package router

import (
	"log"
	"time"

	"github.com/dogan/dogan-server/internal/auth"
	"github.com/dogan/dogan-server/internal/config"
	"github.com/dogan/dogan-server/internal/cvclient"
	"github.com/dogan/dogan-server/internal/handlers"
	"github.com/dogan/dogan-server/internal/mediamtx"
	"github.com/dogan/dogan-server/internal/middleware"
	"github.com/dogan/dogan-server/internal/store"
	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
)

func New(applicationConfig config.Config) *gin.Engine {
	engine := gin.New()
	engine.Use(gin.Logger(), gin.Recovery())
	engine.Use(cors.New(cors.Config{
		AllowOrigins:  applicationConfig.CORSAllowedOrigins,
		AllowMethods:  []string{"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"},
		AllowHeaders:  []string{"Origin", "Content-Type", "Authorization", "Accept"},
		ExposeHeaders: []string{"Content-Length"},
		MaxAge:        12 * time.Hour,
	}))

	dataStore, err := store.Open(applicationConfig.DatabaseURL, applicationConfig.RetentionPeriod)
	if err != nil {
		log.Fatalf("failed to open database: %v", err)
	}

	retentionCleaner := store.NewRetentionCleaner(
		dataStore,
		applicationConfig.FramesDir,
		applicationConfig.RetentionPeriod,
	)
	retentionCleaner.Start()

	tokenIssuer := auth.NewTokenIssuer(applicationConfig.JWTSecret, applicationConfig.TokenTTL)

	streamTokenSecret := applicationConfig.StreamTokenSecret
	if streamTokenSecret == "" {
		streamTokenSecret = applicationConfig.JWTSecret
	}
	streamTokenIssuer := mediamtx.NewTokenIssuer(mediamtx.Config{
		WebRTCPublicURL: applicationConfig.StreamingWebRTCURL,
		HLSPublicURL:    applicationConfig.StreamingHLSURL,
		APIURL:          applicationConfig.MediaMTXAPIURL,
		TokenSecret:     streamTokenSecret,
		TokenTTL:        applicationConfig.StreamTokenTTL,
	})
	iceOptions := mediamtx.IceServerOptions{
		TurnDomain:      applicationConfig.TurnDomain,
		TurnExternalTLS: applicationConfig.TurnExternalTLS,
		TurnSecret:      applicationConfig.TurnSecret,
		TurnTTL:         applicationConfig.TurnTTL,
		ExtraURLs:       applicationConfig.IceServerURLs,
	}
	controlClient := mediamtx.NewControlClient(applicationConfig.MediaMTXAPIURL)

	authHandler := handlers.NewAuthHandler(
		tokenIssuer,
		dataStore,
		dataStore,
		applicationConfig.EmbeddedAPIToken,
		applicationConfig.DeviceAPIKey,
		applicationConfig.TokenTTL,
	)
	deviceRegisterHandler := handlers.NewDeviceRegisterHandler(dataStore)
	endpointsHandler := handlers.NewEndpointsHandler()
	cvClient := cvclient.New(applicationConfig.CVURL)
	frameHandler := handlers.NewFrameHandler(dataStore, cvClient)
	deviceMetricsHandler := handlers.NewDeviceMetricsHandler(dataStore)
	visionHandler := handlers.NewVisionHandler(cvClient, dataStore, dataStore)
	telemetryHandler := handlers.NewTelemetryHandler(dataStore, dataStore, cvClient, visionHandler, applicationConfig.FramesDir)
	actionsHandler := handlers.NewActionsHandler(dataStore)
	settingsHandler := handlers.NewSettingsHandler(dataStore)
	soundsHandler := handlers.NewSoundsHandler(applicationConfig.SoundsDir)
	diagnosticAudioHandler := handlers.NewDiagnosticAudioHandler(dataStore, applicationConfig.AudioDir)
	webrtcHandler := handlers.NewWebRTCHandler(dataStore, controlClient)
	healthHandler := handlers.NewHealthHandler(
		streamTokenIssuer,
		applicationConfig.StreamingWebRTCURL,
		applicationConfig.StreamingHLSURL,
		applicationConfig.TurnDomain,
	)
	streamTokenHandler := handlers.NewStreamTokenHandler(streamTokenIssuer, dataStore, iceOptions)
	mediaMTXAuthHandler := handlers.NewMediaMTXAuthHandler(streamTokenIssuer)
	streamPublisherHandler := handlers.NewStreamPublisherHandler(streamTokenIssuer, applicationConfig.MediaMTXRTMPAddress)
	webrtcSessionHandler := handlers.NewWebRTCSessionHandler(streamTokenIssuer, dataStore, dataStore, iceOptions)
	devicesHandler := handlers.NewDevicesHandler(dataStore, streamTokenIssuer, dataStore, iceOptions)
	devicesWebHandler := handlers.NewDevicesWebHandler(dataStore, dataStore, dataStore, dataStore)

	api := engine.Group("/dogan/api/v1")
	{
		api.POST("/auth", authHandler.Authenticate)
		api.POST("/auth/login", authHandler.WebLogin)
		api.GET("/endpoints", endpointsHandler.ListEndpoints)
		api.GET("/health", healthHandler.GetHealth)
		// MediaMTX authHTTPAddress hook: validates short-lived stream tokens (no bearer middleware).
		api.POST("/mediamtx/auth", mediaMTXAuthHandler.Authorize)
		api.GET("/stream/publish", streamPublisherHandler.Publish)

		protected := api.Group("/")
		protected.Use(middleware.RequireBearerToken(tokenIssuer, applicationConfig.EmbeddedAPIToken))
		{
			protected.GET("/auth/me", authHandler.CurrentUser)
			protected.GET("/auth/login-logs", authHandler.ListLoginLogs)
			protected.POST("/auth/logout", authHandler.Logout)
			protected.GET("/last-frame", frameHandler.GetLastFrame)
			protected.GET("/frame/image", frameHandler.GetFrameImage)
			protected.POST("/frame", frameHandler.SubmitFrame)
			protected.POST("/devices/register", deviceRegisterHandler.Register)
			protected.POST("/telemetry", telemetryHandler.SubmitTelemetry)
			protected.POST("/vision/analyze", visionHandler.Analyze)
			protected.GET("/vision/latest", visionHandler.GetLatest)
			protected.GET("/device-metrics", deviceMetricsHandler.GetLatestMetrics)
			protected.POST("/device-metrics", deviceMetricsHandler.SubmitMetrics)
			protected.POST("/actions", actionsHandler.CreateAction)
			protected.GET("/actions/pending", actionsHandler.GetPendingActions)
			protected.PUT("/actions/:id/ack", actionsHandler.AcknowledgeAction)
			protected.GET("/settings", settingsHandler.GetSettings)
			protected.PUT("/settings", settingsHandler.UpsertSetting)
			protected.PATCH("/settings", settingsHandler.PatchWebSettings)
			protected.GET("/sounds", soundsHandler.ListSounds)
			protected.GET("/sounds/:id", soundsHandler.GetSound)
			protected.POST("/diagnostic-audio", diagnosticAudioHandler.SubmitDiagnosticAudio)
			protected.GET("/webrtc/connections", webrtcHandler.ListConnections)
			protected.POST("/streaming/token", streamTokenHandler.IssueToken)
			protected.POST("/webrtc/session", webrtcSessionHandler.CreateSession)
			protected.GET("/devices", devicesHandler.ListDevices)
			protected.DELETE("/devices/:id", devicesHandler.DeleteDevice)
			protected.GET("/devices/:id/stream", devicesHandler.GetDeviceStream)
			protected.GET("/devices/:id/telemetry", devicesWebHandler.GetDeviceTelemetry)
			protected.GET("/devices/:id/metrics", devicesWebHandler.GetDeviceMetrics)
			protected.POST("/devices/:id/capture", devicesWebHandler.CaptureDeviceFrame)
			protected.GET("/images", devicesWebHandler.ListImages)
			protected.GET("/images/:id", devicesWebHandler.GetImage)
		}
	}

	return engine
}
