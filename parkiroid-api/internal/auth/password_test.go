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
}
