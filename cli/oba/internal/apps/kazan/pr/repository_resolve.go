package pr

import (
	"fmt"
	"net/http"
	"sort"
	"strconv"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// FindRepository resolves a repository the user named against the repositories Kazan's GitHub
// App can read. The list read is a lookup and stays out of the --json output.
func FindRepository(c *client.Client, ref string) (client.GithubRepository, error) {
	repos, err := c.Unrecorded().ListGithubRepositories()
	if err != nil {
		return client.GithubRepository{}, err
	}
	return ResolveRepository(ref, repos)
}

// ResolveRepository matches a repository given by GitHub repository id, by owner/name with case
// ignored, or by a short name that only one repository has. More than one match is an error
// naming them; no match is a not-found error, so the command exits 2.
func ResolveRepository(ref string, repos []client.GithubRepository) (client.GithubRepository, error) {
	ref = strings.TrimSpace(ref)
	if id, err := strconv.ParseInt(ref, 10, 64); err == nil {
		for _, r := range repos {
			if r.GithubRepositoryID == id {
				return r, nil
			}
		}
	}
	for _, r := range repos {
		if strings.EqualFold(r.FullName, ref) {
			return r, nil
		}
	}
	if !strings.Contains(ref, "/") {
		var matches []client.GithubRepository
		for _, r := range repos {
			_, name, _ := strings.Cut(r.FullName, "/")
			if strings.EqualFold(name, ref) {
				matches = append(matches, r)
			}
		}
		if len(matches) == 1 {
			return matches[0], nil
		}
		if len(matches) > 1 {
			names := make([]string, len(matches))
			for i, m := range matches {
				names[i] = guardrail.OneLine(m.FullName)
			}
			return client.GithubRepository{}, fmt.Errorf("repository %q matches %s: use owner/name", ref, strings.Join(names, ", "))
		}
	}
	return client.GithubRepository{}, &client.APIError{
		Status:  http.StatusNotFound,
		Message: fmt.Sprintf("Kazan's GitHub App cannot read a repository %q: 'oba kazan pr repos' lists those it can", ref),
	}
}

// SortRepositories orders repositories by full name, case ignored.
func SortRepositories(repos []client.GithubRepository) {
	sort.Slice(repos, func(i, j int) bool {
		return strings.ToLower(repos[i].FullName) < strings.ToLower(repos[j].FullName)
	})
}
