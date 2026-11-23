package delivery

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io/fs"
	"net/http"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/machine"
)

// RepositoryList is the list of repositories Kaya delivers to in the machine token's organization,
// as this machine last fetched it. A checkout is Kaya's only when one of its remotes is on it, so
// no file in a repository has to name Kaya.
type RepositoryList struct {
	OrganizationID string `json:"organizationId,omitempty"`
	// FetchedAt is when Kaya last answered, in RFC 3339; empty before the first fetch.
	FetchedAt string `json:"fetchedAt,omitempty"`
	// Remotes are normalised as the server stores them (host/owner/name in lower case), sorted.
	Remotes []string `json:"remotes"`
}

// RepositorySource is the call of the Kaya API that lists the organization's repositories.
type RepositorySource interface {
	DeliveryRepositories(ctx context.Context) (client.DeliveryRepositories, error)
}

// Has reports whether remote, normalised, is on the list.
func (l RepositoryList) Has(remote string) bool {
	if remote == "" {
		return false
	}
	_, found := slices.BinarySearch(l.Remotes, remote)
	return found
}

// RepositoryListPath is <user config dir>/oba/kaya/repositories.json, outside every checkout.
func RepositoryListPath() (string, error) {
	base, err := os.UserConfigDir()
	if err != nil {
		return "", fmt.Errorf("find the user config directory: %w", err)
	}
	return filepath.Join(base, "oba", "kaya", "repositories.json"), nil
}

// LoadRepositoryList reads the cached list; a missing file is an empty list, under which every
// checkout is NotKaya.
func LoadRepositoryList() (RepositoryList, error) {
	var list RepositoryList
	path, err := RepositoryListPath()
	if err != nil {
		return list, err
	}
	data, err := os.ReadFile(path) // #nosec G304 -- fixed path below the user config directory
	if errors.Is(err, fs.ErrNotExist) {
		return list, nil
	}
	if err != nil {
		return list, fmt.Errorf("read %s: %w", path, err)
	}
	if err := json.Unmarshal(data, &list); err != nil {
		return RepositoryList{}, fmt.Errorf("%s is damaged; oba kaya sync fetches it again: %w", path, err)
	}
	list.Remotes = cleanRemotes(list.Remotes)
	return list, nil
}

// SaveRepositoryList writes the list by rename, its remotes sorted and without repeats.
func SaveRepositoryList(list RepositoryList) error {
	path, err := RepositoryListPath()
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return fmt.Errorf("create %s: %w", filepath.Dir(path), err)
	}
	list.Remotes = cleanRemotes(list.Remotes)
	data, err := json.MarshalIndent(list, "", "  ")
	if err != nil {
		return fmt.Errorf("encode the repository list: %w", err)
	}
	return writeFileAtomic(path, append(data, '\n'), 0o600)
}

// RefreshRepositoryList asks Kaya for the organization's repositories and adds the new ones to the
// cached list. It never removes a remote: one wrong answer must not take delivery away from every
// checkout. A failure changes nothing; the list it returns is then the cached one.
func RefreshRepositoryList(ctx context.Context, src RepositorySource) (RepositoryList, error) {
	answer, err := src.DeliveryRepositories(ctx)
	if err != nil {
		cached, loadErr := LoadRepositoryList()
		return cached, errors.Join(refreshFailed(err), loadErr)
	}
	if answer.OrganizationID == "" {
		cached, loadErr := LoadRepositoryList()
		return cached, errors.Join(errors.New("the repository list was not refreshed: Kaya's answer names no organization"), loadErr)
	}
	cached, loadErr := LoadRepositoryList()
	if loadErr != nil {
		// A damaged file holds nothing that can be merged; Kaya's answer replaces it, and the
		// error still says so.
		cached = RepositoryList{}
	}
	if cached.OrganizationID != "" && !strings.EqualFold(cached.OrganizationID, answer.OrganizationID) {
		path, pathErr := RepositoryListPath()
		return cached, errors.Join(fmt.Errorf("the repository list was not refreshed: the machine token belongs to organization %s, "+
			"the cached list at %s to organization %s; remove that file to switch organizations", answer.OrganizationID, path, cached.OrganizationID), pathErr)
	}
	merged := RepositoryList{OrganizationID: answer.OrganizationID, FetchedAt: time.Now().UTC().Format(time.RFC3339), Remotes: cached.Remotes}
	for _, repository := range answer.Repositories {
		merged.Remotes = append(merged.Remotes, repository.RemoteURL)
	}
	if err := SaveRepositoryList(merged); err != nil {
		return cached, errors.Join(err, loadErr)
	}
	merged.Remotes = cleanRemotes(merged.Remotes)
	if loadErr != nil {
		return merged, fmt.Errorf("the cached repository list was replaced by Kaya's: %w", loadErr)
	}
	return merged, nil
}

// RefreshRepositories finds the machine token and refreshes the cached repository list with it,
// both within timeout. It is the refresh of commands that reach Kaya for nothing else.
func RefreshRepositories(ctx context.Context, timeout time.Duration) (RepositoryList, error) {
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	m, err := ConnectMachine(ctx)
	if err != nil {
		cached, loadErr := LoadRepositoryList()
		return cached, errors.Join(refreshFailed(err), loadErr)
	}
	return m.RefreshRepositoryList(ctx)
}

// refreshFailed names why Kaya's list was not taken; a refused machine token is named as such,
// since only a new token fixes it.
func refreshFailed(err error) error {
	var apiErr *client.APIError
	if errors.As(err, &apiErr) && apiErr.Status == http.StatusUnauthorized {
		return fmt.Errorf("the repository list was not refreshed: Kaya refused the machine token; set a valid one in %s "+
			"or your Kagi personal environment: %w", machine.EnvToken, err)
	}
	return fmt.Errorf("the repository list was not refreshed: %w", err)
}

// cleanRemotes lower-cases, sorts and deduplicates remotes and drops empty ones.
func cleanRemotes(remotes []string) []string {
	cleaned := make([]string, 0, len(remotes))
	for _, remote := range remotes {
		if remote = strings.ToLower(strings.TrimSpace(remote)); remote != "" {
			cleaned = append(cleaned, remote)
		}
	}
	slices.Sort(cleaned)
	return slices.Compact(cleaned)
}
