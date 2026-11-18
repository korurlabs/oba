package pr

import (
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestResolveRepository(t *testing.T) {
	repos := []client.GithubRepository{
		{GithubRepositoryID: 101, FullName: "senseylabs/village"},
		{GithubRepositoryID: 102, FullName: "senseylabs/agents"},
		{GithubRepositoryID: 103, FullName: "other/agents"},
	}
	for ref, want := range map[string]int64{
		"101": 101, "senseylabs/village": 101, "SenseyLabs/VILLAGE": 101, "Village": 101, "  village ": 101, "other/agents": 103,
	} {
		got, err := ResolveRepository(ref, repos)
		if err != nil || got.GithubRepositoryID != want {
			t.Errorf("%q: got %+v, %v; want id %d", ref, got, err, want)
		}
	}

	_, err := ResolveRepository("agents", repos)
	if err == nil || client.ExitCode(err) != 1 || !strings.Contains(err.Error(), "senseylabs/agents, other/agents") {
		t.Errorf("two matches: err = %v", err)
	}
	for _, ref := range []string{"nosuch", "999", "senseylabs/nosuch"} {
		if _, err := ResolveRepository(ref, repos); client.ExitCode(err) != 2 {
			t.Errorf("%q: err = %v, want a not-found", ref, err)
		}
	}
}
