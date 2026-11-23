package delivery

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"slices"
	"strings"
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/apps/kaya/client"
	"github.com/korurlabs/oba-cli/internal/apps/kaya/machine"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

// listServer answers GET /kaya/delivery/repositories as Kaya does: the list for the token kyt_ok,
// 401 KAY_SEC_003 for any other. It returns a client that sends kyt_ok.
func listServer(t *testing.T, organization string, remotes ...string) *client.Client {
	t.Helper()
	return tokenClient(listServerURL(t, organization, remotes...), "kyt_ok")
}

func tokenClient(baseURL, token string) *client.Client {
	return client.New(baseURL, "", func(context.Context) (string, error) { return token, nil })
}

func listServerURL(t *testing.T, organization string, remotes ...string) string {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/kaya/delivery/repositories" {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		if r.Header.Get("Authorization") != "Bearer kyt_ok" {
			w.WriteHeader(http.StatusUnauthorized)
			_ = json.NewEncoder(w).Encode(map[string]any{"success": false, "error": map[string]any{"code": "KAY_SEC_003", "message": "Invalid machine token"}})
			return
		}
		repositories := []map[string]string{}
		for _, remote := range remotes {
			repositories = append(repositories, map[string]string{"remoteUrl": remote})
		}
		_ = json.NewEncoder(w).Encode(map[string]any{"success": true, "data": map[string]any{"organizationId": organization, "repositories": repositories}})
	}))
	t.Cleanup(server.Close)
	return server.URL
}

func readListFile(t *testing.T) string {
	t.Helper()
	path, err := RepositoryListPath()
	if err != nil {
		t.Fatal(err)
	}
	data, err := os.ReadFile(path)
	if err != nil && !errors.Is(err, os.ErrNotExist) {
		t.Fatal(err)
	}
	return string(data)
}

func TestRefreshRepositoryList_aFetchAddsAndNeverRemoves(t *testing.T) {
	isolateConfig(t)
	listRemotes(t, testOrg, "github.com/example/archived", "github.com/example/app")
	list, err := RefreshRepositoryList(context.Background(), listServer(t, testOrg, "github.com/example/app", "github.com/example/new"))
	if err != nil {
		t.Fatal(err)
	}
	want := []string{"github.com/example/app", "github.com/example/archived", "github.com/example/new"}
	if !slices.Equal(list.Remotes, want) || list.OrganizationID != testOrg {
		t.Fatalf("list = %+v, want remotes %v", list, want)
	}
	stored, err := LoadRepositoryList()
	if err != nil || !slices.Equal(stored.Remotes, want) {
		t.Fatalf("stored = %+v, %v", stored, err)
	}
	if _, err := time.Parse(time.RFC3339, stored.FetchedAt); err != nil {
		t.Fatalf("fetchedAt = %q: %v", stored.FetchedAt, err)
	}
	if info, err := os.Stat(mustListPath(t)); err != nil || info.Mode().Perm() != 0o600 {
		t.Fatalf("the list file is %v, %v; want mode 0600", info.Mode(), err)
	}
}

func TestRefreshRepositoryList_theFirstFetchWritesTheList(t *testing.T) {
	isolateConfig(t)
	list, err := RefreshRepositoryList(context.Background(), listServer(t, testOrg, "github.com/example/app"))
	if err != nil || !slices.Equal(list.Remotes, []string{"github.com/example/app"}) {
		t.Fatalf("list = %+v, %v", list, err)
	}
	if !strings.Contains(readListFile(t), `"organizationId": "`+testOrg+`"`) {
		t.Fatalf("list file = %s", readListFile(t))
	}
}

func TestRefreshRepositoryList_aFailureChangesNothing(t *testing.T) {
	isolateConfig(t)
	listRemotes(t, testOrg, "github.com/example/app")
	before := readListFile(t)

	refused := tokenClient(listServerURL(t, testOrg, "github.com/example/new"), "kyt_revoked")
	list, err := RefreshRepositoryList(context.Background(), refused)
	if err == nil || !strings.Contains(err.Error(), machine.EnvToken) || !strings.Contains(err.Error(), "KAY_SEC_003") {
		t.Fatalf("err = %v, want the machine token named", err)
	}
	if !slices.Equal(list.Remotes, []string{"github.com/example/app"}) {
		t.Fatalf("list = %+v, want the cached one", list)
	}

	unreachable := tokenClient("http://127.0.0.1:1", "kyt_ok")
	if _, err := RefreshRepositoryList(context.Background(), unreachable); err == nil || strings.Contains(err.Error(), machine.EnvToken) {
		t.Fatalf("err = %v, want a refresh error that does not blame the token", err)
	}

	other := listServer(t, "another-org", "github.com/example/other")
	if _, err := RefreshRepositoryList(context.Background(), other); err == nil || !strings.Contains(err.Error(), "another-org") {
		t.Fatalf("err = %v, want the organization change refused", err)
	}

	if _, err := RefreshRepositoryList(context.Background(), listServer(t, "", "github.com/example/other")); err == nil {
		t.Fatal("an answer without an organization was taken")
	}
	if after := readListFile(t); after != before {
		t.Fatalf("the list changed:\n%s\nwant\n%s", after, before)
	}
}

func TestRefreshRepositoryList_aDamagedListIsReplacedAndNamed(t *testing.T) {
	isolateConfig(t)
	path := mustListPath(t)
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(path, []byte("{not json"), 0o600); err != nil {
		t.Fatal(err)
	}
	list, err := RefreshRepositoryList(context.Background(), listServer(t, testOrg, "github.com/example/app"))
	if err == nil || !strings.Contains(err.Error(), "replaced") {
		t.Fatalf("err = %v, want the replacement named", err)
	}
	if !slices.Equal(list.Remotes, []string{"github.com/example/app"}) {
		t.Fatalf("list = %+v", list)
	}
	if stored, err := LoadRepositoryList(); err != nil || !stored.Has("github.com/example/app") {
		t.Fatalf("stored = %+v, %v", stored, err)
	}
}

func TestRefreshRepositories_withoutAMachineTokenKeepsTheCache(t *testing.T) {
	isolateConfig(t)
	listRemotes(t, testOrg, "github.com/example/app")
	t.Setenv(machine.EnvToken, "")
	t.Setenv("PATH", t.TempDir())
	list, err := RefreshRepositories(context.Background(), time.Second)
	if err == nil || !strings.Contains(err.Error(), "not refreshed") {
		t.Fatalf("err = %v, want the refresh named as failed", err)
	}
	if !list.Has("github.com/example/app") {
		t.Fatalf("list = %+v, want the cached one", list)
	}
}

func mustListPath(t *testing.T) string {
	t.Helper()
	path, err := RepositoryListPath()
	if err != nil {
		t.Fatal(err)
	}
	return path
}

func TestLaunchCheckout_refreshesTheListBeforeItRecognises(t *testing.T) {
	isolateConfig(t)
	root := gitCheckout(t, "origin", "https://github.com/example/app.git")
	t.Chdir(root)
	t.Setenv(config.EnvAPIURL, listServerURL(t, testOrg, "github.com/example/app"))
	t.Setenv(machine.EnvToken, "kyt_ok")
	var stderr strings.Builder
	checkout, m, err := launchCheckout(context.Background(), &stderr)
	if err != nil || stderr.Len() != 0 {
		t.Fatalf("launchCheckout: %v, stderr %q", err, stderr.String())
	}
	if checkout.Kind != Delivered || checkout.Remote != "github.com/example/app" || m.API == nil {
		t.Fatalf("checkout = %+v, machine = %+v; want Delivered after the refresh", checkout, m)
	}

	// A refused token leaves the cached list, says why, and the session still starts.
	t.Setenv(machine.EnvToken, "kyt_revoked")
	stderr.Reset()
	checkout, _, err = launchCheckout(context.Background(), &stderr)
	if err != nil || checkout.Kind != Delivered || !strings.Contains(stderr.String(), machine.EnvToken) {
		t.Fatalf("checkout = %+v, %v, stderr %q", checkout, err, stderr.String())
	}
}

func TestLaunchCheckout_outsideGitReachesNothing(t *testing.T) {
	isolateConfig(t)
	t.Chdir(t.TempDir())
	t.Setenv(config.EnvAPIURL, "http://127.0.0.1:1")
	t.Setenv(machine.EnvToken, "kyt_ok")
	var stderr strings.Builder
	checkout, m, err := launchCheckout(context.Background(), &stderr)
	if err != nil || checkout.Kind != NotRepository || m.API != nil || stderr.Len() != 0 {
		t.Fatalf("checkout = %+v, machine = %+v, %v, stderr %q", checkout, m, err, stderr.String())
	}
	if _, err := os.Stat(mustListPath(t)); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("a list was written outside git: %v", err)
	}
}

// listingSource is a release source that also lists the organization's repositories.
type listingSource struct {
	*fakeSource
	remotes []string
}

func (l listingSource) DeliveryRepositories(context.Context) (client.DeliveryRepositories, error) {
	answer := client.DeliveryRepositories{OrganizationID: testOrg}
	for _, remote := range l.remotes {
		answer.Repositories = append(answer.Repositories, client.DeliveryRepository{RemoteURL: remote})
	}
	return answer, nil
}

func TestWatcher_refreshesTheListThroughItsSource(t *testing.T) {
	f := newFixture(t)
	if err := f.d.refreshRepositories(context.Background()); err != nil {
		t.Fatalf("a source that cannot list repositories: %v", err)
	}
	f.d.Source = listingSource{fakeSource: f.source, remotes: []string{"github.com/example/new"}}
	if err := f.d.refreshRepositories(context.Background()); err != nil {
		t.Fatal(err)
	}
	list, err := LoadRepositoryList()
	if err != nil || !list.Has("github.com/example/new") || !list.Has(testRemote) {
		t.Fatalf("list = %+v, %v; want the new remote added and the old one kept", list, err)
	}
}
