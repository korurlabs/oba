package delivery

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"time"
)

// Floors is the newest release this machine verified for each repository of each organization.
// A checkout refuses a release below its repository's floor, so a new worktree, or a checkout
// whose .kaya/ was removed, cannot be rolled back to an older signed release that its own
// state no longer remembers. A floor only rises.
type Floors struct {
	// Organizations maps an organization id, in lower case, to the floor of each normalised
	// remote of that organization.
	Organizations map[string]map[string]int `json:"organizations"`
}

// Floor is the floor of remote in organization; 0 when this machine verified none. Without an
// organization it is the highest floor of remote in any organization.
func (f Floors) Floor(organization, remote string) int {
	if organization != "" {
		return f.Organizations[strings.ToLower(organization)][remote]
	}
	highest := 0
	for _, remotes := range f.Organizations {
		highest = max(highest, remotes[remote])
	}
	return highest
}

// raise sets the floor of remote in organization to number when number is higher, and reports
// whether it rose.
func (f *Floors) raise(organization, remote string, number int) bool {
	organization = strings.ToLower(organization)
	if f.Floor(organization, remote) >= number {
		return false
	}
	if f.Organizations == nil {
		f.Organizations = map[string]map[string]int{}
	}
	if f.Organizations[organization] == nil {
		f.Organizations[organization] = map[string]int{}
	}
	f.Organizations[organization][remote] = number
	return true
}

// FloorsPath is <user config dir>/oba/kaya/floors.json, outside every checkout, so that removing
// a checkout's .kaya/ or adding a worktree leaves it in place.
func FloorsPath() (string, error) {
	base, err := os.UserConfigDir()
	if err != nil {
		return "", fmt.Errorf("find the user config directory: %w", err)
	}
	return filepath.Join(base, "oba", "kaya", "floors.json"), nil
}

// LoadFloors reads the machine's release floors; a missing file holds none.
func LoadFloors() (Floors, error) {
	var floors Floors
	path, err := FloorsPath()
	if err != nil {
		return floors, err
	}
	data, err := os.ReadFile(path) // #nosec G304 -- fixed path below the user config directory
	if errors.Is(err, fs.ErrNotExist) {
		return floors, nil
	}
	if err != nil {
		return floors, fmt.Errorf("read %s: %w", path, err)
	}
	if err := json.Unmarshal(data, &floors); err != nil {
		return Floors{}, fmt.Errorf("%s is damaged; remove it, and the next sync of each checkout writes its floor again: %w", path, err)
	}
	return floors, nil
}

// RaiseFloor raises the floor of remote in organization to number, a release this machine just
// verified for it. The file is changed only when the floor rises, under a lock and by rename, so
// two checkouts verifying at once never lose each other's floor and a reader never sees a part.
func RaiseFloor(ctx context.Context, organization, remote string, number int) error {
	if organization == "" || remote == "" || number <= 0 {
		return fmt.Errorf("a release floor needs an organization, a remote and a release number; got %q, %q and %d", organization, remote, number)
	}
	path, err := FloorsPath()
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return fmt.Errorf("create %s: %w", filepath.Dir(path), err)
	}
	unlock, err := lockFile(ctx, path+".lock")
	if err != nil {
		return err
	}
	defer unlock()
	floors, err := LoadFloors()
	if err != nil {
		// A damaged file is never overwritten: the floors of the other repositories it holds
		// would be lost, and a floor must not fall.
		return fmt.Errorf("release %d of %s did not raise its floor: %w", number, remote, err)
	}
	if !floors.raise(organization, remote, number) {
		return nil
	}
	data, err := json.MarshalIndent(floors, "", "  ")
	if err != nil {
		return fmt.Errorf("encode the release floors: %w", err)
	}
	return writeFileAtomic(path, append(data, '\n'), 0o600)
}

// lockFile takes an exclusive lock on path, creating it, and waits for it until ctx ends.
func lockFile(ctx context.Context, path string) (func(), error) {
	file, err := os.OpenFile(path, os.O_CREATE|os.O_RDWR, 0o600) // #nosec G304 -- fixed lock path below the user config directory
	if err != nil {
		return nil, fmt.Errorf("open %s: %w", path, err)
	}
	for {
		err := syscall.Flock(int(file.Fd()), syscall.LOCK_EX|syscall.LOCK_NB) // #nosec G115 -- a file descriptor fits an int
		if err == nil {
			return func() {
				_ = syscall.Flock(int(file.Fd()), syscall.LOCK_UN) // #nosec G115 -- a file descriptor fits an int
				_ = file.Close()
			}, nil
		}
		if !errors.Is(err, syscall.EWOULDBLOCK) {
			_ = file.Close()
			return nil, fmt.Errorf("lock %s: %w", path, err)
		}
		select {
		case <-ctx.Done():
			_ = file.Close()
			return nil, fmt.Errorf("another kaya command holds %s: %w", path, ctx.Err())
		case <-time.After(20 * time.Millisecond):
		}
	}
}
