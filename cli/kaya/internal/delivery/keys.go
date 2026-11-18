package delivery

import (
	"crypto/ed25519"
	"encoding/base64"
	"fmt"
	"net"
	"net/url"
	"os"
	"strings"
)

// EnvDevTrustedKeys names extra public keys for a local Kaya stack, as
// "<keyId>=<base64 raw key>[,<keyId>=<base64 raw key>]". The local backend signs with an
// ephemeral key whose public half only GET /kaya/signing-keys knows. They are trusted only
// while the API is a loopback address, so the variable cannot widen trust in production.
const EnvDevTrustedKeys = "KAYA_DEV_TRUSTED_KEYS"

// releaseKeys are the public keys of the production signing key (Kagi app /kaya/api,
// CONFIG_SIGNING_KEY_ID to the raw 32-byte public key in base64, as GET /kaya/signing-keys
// lists it). Two entries are valid while a key is rotated. They are compiled in, not fetched,
// so a compromised API or network path cannot change what the CLI trusts. The release build
// refuses to run with this map empty (keys_release_test.go).
var releaseKeys = map[string]string{}

// Keys are the public keys a signature may be made with, by key id.
type Keys map[string]ed25519.PublicKey

// TrustedKeys returns the compiled-in keys, plus the keys of EnvDevTrustedKeys when apiURL is a
// loopback address.
func TrustedKeys(apiURL string) (Keys, error) {
	keys, err := parseKeys(releaseKeys)
	if err != nil {
		return nil, fmt.Errorf("a compiled-in signing key is invalid: %w", err)
	}
	extra := strings.TrimSpace(os.Getenv(EnvDevTrustedKeys))
	if extra == "" || !isLoopback(apiURL) {
		return keys, nil
	}
	pairs := map[string]string{}
	for _, item := range strings.Split(extra, ",") {
		id, value, ok := strings.Cut(strings.TrimSpace(item), "=")
		if !ok {
			return nil, fmt.Errorf("%s: %q is not <keyId>=<base64 key>", EnvDevTrustedKeys, item)
		}
		pairs[strings.TrimSpace(id)] = strings.TrimSpace(value)
	}
	devKeys, err := parseKeys(pairs)
	if err != nil {
		return nil, fmt.Errorf("%s: %w", EnvDevTrustedKeys, err)
	}
	for id, key := range devKeys {
		keys[id] = key
	}
	return keys, nil
}

func parseKeys(encoded map[string]string) (Keys, error) {
	keys := Keys{}
	for id, value := range encoded {
		raw, err := base64.StdEncoding.DecodeString(value)
		if err != nil {
			return nil, fmt.Errorf("key %s is not base64: %w", id, err)
		}
		if len(raw) != ed25519.PublicKeySize || id == "" {
			return nil, fmt.Errorf("key %q is not a raw %d-byte Ed25519 public key", id, ed25519.PublicKeySize)
		}
		keys[id] = ed25519.PublicKey(raw)
	}
	return keys, nil
}

// Verify checks a base64 Ed25519 signature of message with the key keyID.
func (k Keys) Verify(keyID, signature string, message []byte) error {
	key, ok := k[keyID]
	if !ok {
		return fmt.Errorf("signed with key %q, which this CLI does not trust", keyID)
	}
	sig, err := base64.StdEncoding.DecodeString(signature)
	if err != nil {
		return fmt.Errorf("the signature is not base64: %w", err)
	}
	if !ed25519.Verify(key, message, sig) {
		return fmt.Errorf("the signature does not verify with key %q", keyID)
	}
	return nil
}

// isLoopback reports whether the API URL names this machine.
func isLoopback(apiURL string) bool {
	parsed, err := url.Parse(apiURL)
	if err != nil {
		return false
	}
	host := parsed.Hostname()
	if host == "localhost" {
		return true
	}
	ip := net.ParseIP(host)
	return ip != nil && ip.IsLoopback()
}

// CheckTransport refuses to send the machine token over plain http to another machine.
func CheckTransport(apiURL string) error {
	parsed, err := url.Parse(apiURL)
	if err != nil {
		return fmt.Errorf("the API URL %q is invalid: %w", apiURL, err)
	}
	if parsed.Scheme == "https" || (parsed.Scheme == "http" && isLoopback(apiURL)) {
		return nil
	}
	return fmt.Errorf("the API URL %s is not https; the machine token is sent only over https or to this machine", apiURL)
}
