package auth

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"

	"github.com/zalando/go-keyring"

	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

const (
	keychainService = "oba-cli"
	keychainUser    = "default"
)

// keyringProbeTimeout bounds the availability probe of the OS secret service. go-keyring on
// Linux can block for seconds dialing a broken or absent D-Bus Secret Service before it errors,
// which would stall every command on a headless box; the cap keeps the fallback fast.
const keyringProbeTimeout = 3 * time.Second

// keyringStore keeps the session in the OS secret service through go-keyring (Keychain on
// macOS, the Secret Service over D-Bus on Linux, so GNOME Keyring or KWallet).
type keyringStore struct{}

// NewTokenStore returns the store on the best credential backend of this host.
//
// The backend choice is sticky, so that a passing secret-service outage cannot silently move a
// keyring host to plaintext, which would strand the real session in the keyring or write a
// diverging plaintext copy carrying a rotated refresh token:
//
//   - Secret service reachable now: the keyring. Its first successful use drops the marker
//     ~/.config/oba/keyring-active, recording that this host has used the keyring.
//   - Unreachable now, but the marker exists: a store whose every operation fails loudly,
//     rather than falling back to plaintext.
//   - Unreachable and never used here: the plaintext file under ~/.config/oba, with a warning
//     and mode 0600.
//
// The decision is made once per process: each probe is a keyring read, which on a headless box
// can sit out the whole probe timeout. A secret service that becomes usable during a run is
// therefore not noticed; `oba login` probes again through ResetTokenStore before it saves,
// because it writes after a browser round trip during which the user may unlock the keychain.
func NewTokenStore() TokenStore {
	tokenStoreMu.Lock()
	defer tokenStoreMu.Unlock()
	if tokenStoreMemo == nil {
		tokenStoreMemo = selectTokenStore(keyringProbe(), keyringEverUsed())
	}
	return tokenStoreMemo
}

var (
	tokenStoreMu   sync.Mutex
	tokenStoreMemo TokenStore

	// keyringProbe is the availability probe, a variable so that tests can replace it.
	keyringProbe = keyringAvailable
)

// ResetTokenStore drops the memoised backend so that the next NewTokenStore decides afresh.
// Login calls it before saving; tests call it because each case builds its own HOME and mock
// keyring.
func ResetTokenStore() {
	tokenStoreMu.Lock()
	defer tokenStoreMu.Unlock()
	tokenStoreMemo = nil
}

// selectTokenStore applies the sticky decision described on NewTokenStore. It does no I/O
// beyond the plaintext warning, so the decision table can be tested without a secret service.
func selectTokenStore(available, everUsed bool) TokenStore {
	switch {
	case available:
		return &keyringStore{}
	case everUsed:
		return &unavailableKeyringStore{}
	default:
		warnPlaintextFallback()
		return newFileStore()
	}
}

// errKeyringUnavailable is returned by every operation of unavailableKeyringStore.
var errKeyringUnavailable = errors.New(
	"could not reach the OS secret service, which has held this host's credentials before; " +
		"refusing to fall back to plaintext storage; retry once the secret service is back")

// unavailableKeyringStore stands in for a keyring that held this host's session before but is
// unreachable now. It refuses every operation instead of downgrading to the plaintext file.
type unavailableKeyringStore struct{}

func (unavailableKeyringStore) Save(Credentials) error { return errKeyringUnavailable }
func (unavailableKeyringStore) Load() (Credentials, error) {
	return Credentials{}, errKeyringUnavailable
}
func (unavailableKeyringStore) Delete() error { return errKeyringUnavailable }

func keyringMarkerPath() (string, error) {
	dir, err := config.Dir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "keyring-active"), nil
}

// markKeyringUsed records that the OS secret service has held this host's session, which makes
// the backend choice sticky for later runs. It is best effort.
func markKeyringUsed() {
	path, err := keyringMarkerPath()
	if err != nil {
		// rule-10-no-op -- reason: a missing marker only costs stickiness on the next run
		return
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		// rule-10-no-op -- reason: a missing marker only costs stickiness on the next run
		return
	}
	// rule-10-no-op -- reason: a missing marker only costs stickiness on the next run
	_ = os.WriteFile(path, []byte("1\n"), 0o600)
}

// keyringEverUsed reports whether the marker exists, that is whether the keyring has held this
// host's session before.
func keyringEverUsed() bool {
	path, err := keyringMarkerPath()
	if err != nil {
		return false
	}
	_, err = os.Stat(path)
	return err == nil
}

// keyringAvailable reports whether the OS secret service can be reached, by a bounded read of
// the session item. ErrNotFound means the service works and holds no session yet; any other
// error, or a probe that outlasts keyringProbeTimeout, means it is unusable here.
func keyringAvailable() bool {
	ch := make(chan error, 1)
	go func() {
		_, err := keyring.Get(keychainService, keychainUser)
		ch <- err
	}()

	select {
	case err := <-ch:
		if err == nil {
			// A session is present, perhaps from before the marker existed: record it now so
			// the choice stays sticky even if the service fails later.
			markKeyringUsed()
			return true
		}
		return errors.Is(err, keyring.ErrNotFound)
	case <-time.After(keyringProbeTimeout):
		// The probe wedged, typically on a broken D-Bus Secret Service. The goroutine finishes
		// on its own; ch is buffered, so it never blocks.
		return false
	}
}

var plaintextWarnOnce sync.Once

// warnPlaintextFallback prints, once per process, that the session is kept in plaintext.
func warnPlaintextFallback() {
	plaintextWarnOnce.Do(func() {
		_, _ = fmt.Fprintln(os.Stderr,
			"Warning: no OS secret service available; storing credentials in plaintext under ~/.config/oba (mode 0600).")
	})
}

func (k *keyringStore) Save(creds Credentials) error {
	data, err := json.Marshal(creds) //nolint:gosec // serializing credentials to store in the OS secret service is this store's purpose
	if err != nil {
		return fmt.Errorf("failed to serialize credentials: %w", err)
	}
	if err := keyring.Set(keychainService, keychainUser, string(data)); err != nil {
		return fmt.Errorf("failed to store credentials in the OS secret service: %w", err)
	}
	markKeyringUsed()
	return nil
}

func (k *keyringStore) Load() (Credentials, error) {
	data, err := keyring.Get(keychainService, keychainUser)
	if err != nil {
		if errors.Is(err, keyring.ErrNotFound) {
			return Credentials{}, fmt.Errorf("no credentials found in the OS secret service: %w",
				errors.Join(ErrNoCredentials, err))
		}
		return Credentials{}, fmt.Errorf("failed to read credentials from the OS secret service: %w", err)
	}
	var creds Credentials
	if err := json.Unmarshal([]byte(data), &creds); err != nil {
		return Credentials{}, fmt.Errorf("failed to parse credentials from the OS secret service: %w", err)
	}
	markKeyringUsed()
	return creds, nil
}

func (k *keyringStore) Delete() error {
	if err := keyring.Delete(keychainService, keychainUser); err != nil {
		if errors.Is(err, keyring.ErrNotFound) {
			return nil
		}
		return fmt.Errorf("failed to delete credentials from the OS secret service: %w", err)
	}
	return nil
}
