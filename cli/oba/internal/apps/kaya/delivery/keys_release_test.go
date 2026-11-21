//go:build release

package delivery

import "testing"

// TestReleaseTrustsAKey runs before every published build (.goreleaser.yaml, before hooks): a
// binary without the production public key would refuse every release.
func TestReleaseTrustsAKey(t *testing.T) {
	keys, err := parseKeys(releaseKeys)
	if err != nil {
		t.Fatal(err)
	}
	if len(keys) == 0 {
		t.Fatal("releaseKeys in internal/apps/kaya/delivery/keys.go is empty: add the production public key from GET /kaya/signing-keys")
	}
}
