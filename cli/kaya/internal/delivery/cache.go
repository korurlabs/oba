package delivery

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
)

// Cache is the content-addressed blob cache shared by every checkout of the machine,
// $XDG_CACHE_HOME/kaya/blobs/<sha256> (the user cache directory of the platform). A blob is
// stored only after its hash was checked, and checked again when it is read.
type Cache struct {
	Dir string
}

// DefaultCache is the cache in the user cache directory.
func DefaultCache() (Cache, error) {
	base, err := os.UserCacheDir()
	if err != nil {
		return Cache{}, fmt.Errorf("find the user cache directory: %w", err)
	}
	return Cache{Dir: filepath.Join(base, "kaya", "blobs")}, nil
}

func (c Cache) path(sha string) string {
	return filepath.Join(c.Dir, sha)
}

// Has reports whether the blob is cached.
func (c Cache) Has(sha string) bool {
	info, err := os.Lstat(c.path(sha))
	return err == nil && info.Mode().IsRegular()
}

// Get reads a cached blob and checks its hash; a damaged entry is removed and reported missing.
func (c Cache) Get(sha string) ([]byte, error) {
	if !sha256Hex.MatchString(sha) {
		return nil, fmt.Errorf("%q is not a sha256", sha)
	}
	data, err := os.ReadFile(c.path(sha))
	if err != nil {
		return nil, fmt.Errorf("blob %s: %w", sha, err)
	}
	if hashOf(data) != sha {
		_ = os.Remove(c.path(sha))
		return nil, fmt.Errorf("blob %s: the cached copy was damaged and is removed: %w", sha, fs.ErrNotExist)
	}
	return data, nil
}

// Put stores data under sha after checking that it hashes to sha.
func (c Cache) Put(sha string, data []byte) error {
	if !sha256Hex.MatchString(sha) {
		return fmt.Errorf("%q is not a sha256", sha)
	}
	if actual := hashOf(data); actual != sha {
		return fmt.Errorf("blob %s: the downloaded content hashes to %s", sha, actual)
	}
	if err := os.MkdirAll(c.Dir, 0o700); err != nil {
		return fmt.Errorf("create %s: %w", c.Dir, err)
	}
	return writeFileAtomic(c.path(sha), data, 0o600)
}

func hashOf(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}
