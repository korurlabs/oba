package upload

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"os"
)

// hashFile returns the hex SHA-256 of a file, which names an asset by its content.
func hashFile(path string) (string, error) {
	data, err := os.ReadFile(path) // #nosec G304 G703 -- an asset inside the directory the user uploads
	if err != nil {
		return "", fmt.Errorf("read the asset %s: %w", path, err)
	}
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:]), nil
}
