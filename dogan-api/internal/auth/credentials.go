package auth

const (
	AdminUsername    = "admin"
	AdminEmail       = "admin@local.local"
	AdminDisplayName = "Admin"

	// bcrypt hash of AdminUsername password "admin"
	AdminPasswordHash = "$2a$10$NhHYPKBR2Vh5m75s5r59LeT9ivpDVRSxliUQAnGrumihuzhC8pQG2"

	DeviceUsername = "dogan"
	// bcrypt hash of DeviceUsername password; plaintext lives only in the Android APK constant.
	DevicePasswordHash = "$2a$10$qt/1alpm.0N27kwr6Vsd7ezpcF4FRCOv4SWZoYeak5Wgj4JN3vp02"
)
