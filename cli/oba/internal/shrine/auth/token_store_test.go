package auth

import (
	"errors"
	"os"
	"path/filepath"
	"testing"

	"github.com/zalando/go-keyring"
)

func setupStore(t *testing.T) string {
	t.Helper()
	home := t.TempDir()
	t.Setenv("HOME", home)
	keyring.MockInit()
	ResetTokenStore()
	oldProbe := keyringProbe
	t.Cleanup(func() {
		keyringProbe = oldProbe
		ResetTokenStore()
	})
	return home
}

var sample = Credentials{AccessToken: "a", RefreshToken: "r", APIURL: "http://x"}

func TestStoreChoiceKeyringAvailable(t *testing.T) {
	home := setupStore(t)
	store := NewTokenStore()
	if _, ok := store.(*keyringStore); !ok {
		t.Fatalf("store = %T", store)
	}
	if err := store.Save(sample); err != nil {
		t.Fatal(err)
	}
	got, err := store.Load()
	if err != nil || got.RefreshToken != "r" {
		t.Fatalf("load = %+v, %v", got, err)
	}
	if _, err := os.Stat(filepath.Join(home, ".config", "oba", "keyring-active")); err != nil {
		t.Errorf("marker not written: %v", err)
	}
	if _, err := os.Stat(filepath.Join(home, ".config", "oba", "credentials")); err == nil {
		t.Error("plaintext file written next to the keyring")
	}
	if err := store.Delete(); err != nil {
		t.Fatal(err)
	}
	if _, err := store.Load(); !errors.Is(err, ErrNoCredentials) {
		t.Errorf("after delete: %v", err)
	}
}

func TestStoreChoiceUsedBeforeIsSticky(t *testing.T) {
	home := setupStore(t)
	dir := filepath.Join(home, ".config", "oba")
	if err := os.MkdirAll(dir, 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dir, "keyring-active"), []byte("1\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	keyringProbe = func() bool { return false }

	store := NewTokenStore()
	if _, ok := store.(*unavailableKeyringStore); !ok {
		t.Fatalf("store = %T", store)
	}
	if err := store.Save(sample); !errors.Is(err, errKeyringUnavailable) {
		t.Errorf("save: %v", err)
	}
	if _, err := store.Load(); !errors.Is(err, errKeyringUnavailable) {
		t.Errorf("load: %v", err)
	}
	if err := store.Delete(); !errors.Is(err, errKeyringUnavailable) {
		t.Errorf("delete: %v", err)
	}
	if _, err := os.Stat(filepath.Join(dir, "credentials")); err == nil {
		t.Error("fell back to a plaintext file")
	}
}

func TestStoreChoiceNeverUsedFallsBackToPlaintext(t *testing.T) {
	home := setupStore(t)
	keyringProbe = func() bool { return false }

	store := NewTokenStore()
	if _, ok := store.(*fileStore); !ok {
		t.Fatalf("store = %T", store)
	}
	if _, err := store.Load(); !errors.Is(err, ErrNoCredentials) {
		t.Errorf("empty load: %v", err)
	}
	if err := store.Save(sample); err != nil {
		t.Fatal(err)
	}

	dir := filepath.Join(home, ".config", "oba")
	file := filepath.Join(dir, "credentials")
	info, err := os.Stat(file)
	if err != nil {
		t.Fatal(err)
	}
	if info.Mode().Perm() != 0o600 {
		t.Errorf("file mode = %o, want 600", info.Mode().Perm())
	}
	dirInfo, err := os.Stat(dir)
	if err != nil {
		t.Fatal(err)
	}
	if dirInfo.Mode().Perm() != 0o700 {
		t.Errorf("dir mode = %o, want 700", dirInfo.Mode().Perm())
	}
	leftovers, _ := filepath.Glob(filepath.Join(dir, ".credentials-*.tmp"))
	if len(leftovers) != 0 {
		t.Errorf("temp files left: %v", leftovers)
	}
	got, err := store.Load()
	if err != nil || got.AccessToken != "a" {
		t.Fatalf("load = %+v, %v", got, err)
	}
	if err := store.Delete(); err != nil {
		t.Fatal(err)
	}
	if err := store.Delete(); err != nil {
		t.Errorf("second delete: %v", err)
	}
}

func TestStoreIsMemoisedUntilReset(t *testing.T) {
	setupStore(t)
	first := NewTokenStore()
	keyringProbe = func() bool { return false }
	if NewTokenStore() != first {
		t.Error("store changed without a reset")
	}
	ResetTokenStore()
	if _, ok := NewTokenStore().(*fileStore); !ok {
		t.Error("reset did not decide afresh")
	}
}

func TestLoadSession(t *testing.T) {
	setupStore(t)
	if _, err := LoadSession(); !errors.Is(err, ErrNotLoggedIn) {
		t.Fatalf("no session: %v", err)
	}
	if err := NewTokenStore().Save(sample); err != nil {
		t.Fatal(err)
	}
	got, err := LoadSession()
	if err != nil || got.APIURL != "http://x" {
		t.Fatalf("session = %+v, %v", got, err)
	}

	keyringProbe = func() bool { return false }
	if err := os.MkdirAll(filepath.Join(os.Getenv("HOME"), ".config", "oba"), 0o700); err != nil {
		t.Fatal(err)
	}
	ResetTokenStore()
	// the marker from the keyring save above makes the choice sticky
	if _, err := LoadSession(); err == nil || errors.Is(err, ErrNotLoggedIn) {
		t.Errorf("unreachable keyring must be reported, got %v", err)
	}
}
