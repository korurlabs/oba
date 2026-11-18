package auth

import (
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"

	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

// fileStore is the plaintext fallback, used only when no OS secret service has ever been
// reachable on this host: ~/.config/oba/credentials, mode 0600 in a 0700 directory.
type fileStore struct {
	path string
	// locateErr is set when the home directory cannot be resolved. It is returned from every
	// operation so that credentials are never written to a relative path.
	locateErr error
}

func newFileStore() *fileStore {
	dir, err := config.Dir()
	if err != nil {
		return &fileStore{locateErr: fmt.Errorf("cannot store credentials: %w", err)}
	}
	return &fileStore{path: filepath.Join(dir, "credentials")}
}

func (f *fileStore) Save(creds Credentials) error {
	if f.locateErr != nil {
		return f.locateErr
	}
	dir := filepath.Dir(f.path)
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return fmt.Errorf("failed to create credentials directory: %w", err)
	}

	data, err := json.MarshalIndent(creds, "", "  ") //nolint:gosec // serializing credentials for the local token store is this store's purpose; the file is written 0600
	if err != nil {
		return fmt.Errorf("failed to serialize credentials: %w", err)
	}

	// Write a temp file in the same directory and rename it over the target, so that a
	// concurrent reader sees the old or the new complete file, never a half-written one.
	tmp, err := os.CreateTemp(dir, ".credentials-*.tmp")
	if err != nil {
		return fmt.Errorf("failed to create temp credentials file: %w", err)
	}
	tmpName := tmp.Name()
	// After a successful rename the temp name no longer exists, so this only removes a temp
	// file left by an early return.
	defer func() { _ = os.Remove(tmpName) }()

	if _, err := tmp.Write(data); err != nil {
		_ = tmp.Close()
		return fmt.Errorf("failed to write temp credentials file: %w", err)
	}
	// The file holds plaintext tokens: set 0600 before the rename so the target is never
	// readable by others, even for a moment.
	if err := os.Chmod(tmpName, 0o600); err != nil {
		_ = tmp.Close()
		return fmt.Errorf("failed to set credentials file mode: %w", err)
	}
	if err := tmp.Close(); err != nil {
		return fmt.Errorf("failed to close temp credentials file: %w", err)
	}
	if err := os.Rename(tmpName, f.path); err != nil {
		return fmt.Errorf("failed to write credentials file: %w", err)
	}
	return nil
}

func (f *fileStore) Load() (Credentials, error) {
	if f.locateErr != nil {
		return Credentials{}, f.locateErr
	}
	data, err := os.ReadFile(f.path)
	if err != nil {
		if errors.Is(err, fs.ErrNotExist) {
			return Credentials{}, fmt.Errorf("no credentials found at %s: %w", f.path,
				errors.Join(ErrNoCredentials, err))
		}
		return Credentials{}, fmt.Errorf("failed to read credentials file %s: %w", f.path, err)
	}
	var creds Credentials
	if err := json.Unmarshal(data, &creds); err != nil {
		return Credentials{}, fmt.Errorf("failed to parse credentials file %s: %w", f.path, err)
	}
	return creds, nil
}

func (f *fileStore) Delete() error {
	if f.locateErr != nil {
		return f.locateErr
	}
	if err := os.Remove(f.path); err != nil && !errors.Is(err, fs.ErrNotExist) {
		return fmt.Errorf("failed to delete credentials file %s: %w", f.path, err)
	}
	return nil
}
