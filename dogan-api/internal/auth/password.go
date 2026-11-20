package auth

import (
	"crypto/subtle"

	"golang.org/x/crypto/bcrypt"
)

func VerifyAdminCredentials(username, password string) bool {
	if subtle.ConstantTimeCompare([]byte(username), []byte(AdminUsername)) != 1 {
		return false
	}

	return bcrypt.CompareHashAndPassword([]byte(AdminPasswordHash), []byte(password)) == nil
}

func VerifyDeviceCredentials(username, password string) bool {
	if subtle.ConstantTimeCompare([]byte(username), []byte(DeviceUsername)) != 1 {
		return false
	}

	return bcrypt.CompareHashAndPassword([]byte(DevicePasswordHash), []byte(password)) == nil
}
