package pr

import (
	"errors"
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// PickPull lets the user choose a repository, search it and pick a pull request, 30 per page
// with "More" while GitHub has more. Every read is a lookup and stays out of the --json output.
func PickPull(c *client.Client, p *Prompter) (client.GithubRepository, client.GithubPullRequestSummary, error) {
	lookup := c.Unrecorded()
	repos, err := lookup.ListGithubRepositories()
	if err != nil {
		return client.GithubRepository{}, client.GithubPullRequestSummary{}, err
	}
	if len(repos) == 0 {
		return client.GithubRepository{}, client.GithubPullRequestSummary{},
			errors.New("no repository of this organization is readable by Kazan's GitHub App")
	}
	SortRepositories(repos)
	names := make([]string, len(repos))
	for i, r := range repos {
		names[i] = guardrail.OneLine(r.FullName)
	}
	idx, err := p.Choose("Repository", names)
	if err != nil {
		return client.GithubRepository{}, client.GithubPullRequestSummary{}, err
	}
	repo := repos[idx]

	search, err := p.Text("Search (optional; #N finds a number)")
	if err != nil {
		return client.GithubRepository{}, client.GithubPullRequestSummary{}, err
	}
	if err := CheckSearch(search); err != nil {
		return client.GithubRepository{}, client.GithubPullRequestSummary{}, err
	}

	for page := 1; ; page++ {
		result, err := lookup.ListGithubPullRequests(repo.GithubRepositoryID, PullFilter{Search: search, Page: page}.query())
		if err != nil {
			return client.GithubRepository{}, client.GithubPullRequestSummary{}, err
		}
		if len(result.Items) == 0 {
			return client.GithubRepository{}, client.GithubPullRequestSummary{},
				fmt.Errorf("no pull request of %s matches", guardrail.OneLine(repo.FullName))
		}
		options := make([]string, 0, len(result.Items)+1)
		for _, pr := range result.Items {
			options = append(options, fmt.Sprintf("#%d  %s  %s (%s)", pr.Number, guardrail.OneLine(pr.Title),
				guardrail.OneLine(pr.State), guardrail.OneLine(pr.AuthorLogin)))
		}
		if result.HasMore {
			options = append(options, "More")
		}
		choice, err := p.Choose("Pull request", options)
		if err != nil {
			return client.GithubRepository{}, client.GithubPullRequestSummary{}, err
		}
		if choice < len(result.Items) {
			return repo, result.Items[choice], nil
		}
	}
}
