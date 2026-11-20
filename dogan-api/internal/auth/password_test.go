package auth

import "testing"

func TestAdminCredentialsAdminAdmin(t *testing.T) {
	if !VerifyAdminCredentials("admin", "admin") {
		t.Fatal("expected admin/admin to verify")
	}
	if !VerifyAdminCredentials(LoginIdentifier(AdminEmail), "admin") {
		t.Fatal("expected admin@local.local/admin to verify via login identifier")
	}
	if VerifyAdminCredentials("armin", "dogan123") {
		t.Fatal("old credentials should not verify")
	}
	if VerifyAdminCredentials(DeviceUsername, "Kp7#mQv2$nLx9wRd4!bT") {
		t.Fatal("device account must not pass the web admin check")
	}
	if !VerifyDeviceCredentials(DeviceUsername, "Kp7#mQv2$nLx9wRd4!bT") {
		t.Fatal("expected dogan device password to verify")
	}
	if VerifyDeviceCredentials(AdminUsername, "admin") {
		t.Fatal("admin must not pass the device check")
	}
}
