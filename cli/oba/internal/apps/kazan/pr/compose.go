package pr

import (
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// ComposeInput is what the flags and the argument of share and preview give, checked before
// any request. Ref is nil when no pull request was named; Status is "" when --status was not
// given. NoteGiven and ReviewersGiven tell an empty flag from an absent one.
type ComposeInput struct {
	Ref            *PullRef
	Status         string
	Note           *string
	NoteGiven      bool
	ReviewerRefs   []string
	ReviewersGiven bool
	TicketRefs     []string
}

// Compose resolves the pull request, the reviewers and the related tickets into a share request.
// With a prompter it asks for what the flags left open; related tickets are never asked for.
// Without --status the status is preset from GitHub's state, and a closed pull request needs a
// choice. Every read is a lookup and stays out of the --json output.
func Compose(c *client.Client, in ComposeInput, p *Prompter, me MeFunc) (client.PullRequestShareRequest, error) {
	lookup := c.Unrecorded()
	var repo client.GithubRepository
	var number int
	var githubState string

	if in.Ref == nil {
		if p == nil {
			return client.PullRequestShareRequest{}, errPullRequired
		}
		pickedRepo, picked, err := PickPull(c, p)
		if err != nil {
			return client.PullRequestShareRequest{}, err
		}
		repo, number, githubState = pickedRepo, picked.Number, picked.State
	} else {
		resolved, err := FindRepository(c, in.Ref.Repo)
		if err != nil {
			return client.PullRequestShareRequest{}, err
		}
		repo, number = resolved, in.Ref.Number
		if in.Status == "" {
			pr, err := lookup.GetGithubPullRequest(repo.GithubRepositoryID, number)
			if err != nil {
				return client.PullRequestShareRequest{}, err
			}
			githubState = pr.State
		}
	}

	status, err := composeStatus(in.Status, githubState, repo, number, p)
	if err != nil {
		return client.PullRequestShareRequest{}, err
	}

	note := in.Note
	if p != nil && !in.NoteGiven {
		typed, err := p.OptionalText("Note", nil)
		if err != nil {
			return client.PullRequestShareRequest{}, err
		}
		if err := CheckNote(typed); err != nil {
			return client.PullRequestShareRequest{}, err
		}
		note = typed
	}

	var reviewerIDs []string
	if p != nil && !in.ReviewersGiven {
		reviewerIDs, _, err = PickReviewers(c, p, me)
		if err != nil {
			return client.PullRequestShareRequest{}, err
		}
	} else {
		reviewerIDs, err = ResolveReviewers(c, in.ReviewerRefs, me)
		if err != nil {
			return client.PullRequestShareRequest{}, fmt.Errorf("--reviewers: %w", err)
		}
	}

	ticketIDs, err := ResolveTickets(c, in.TicketRefs)
	if err != nil {
		return client.PullRequestShareRequest{}, fmt.Errorf("--tickets: %w", err)
	}

	return client.PullRequestShareRequest{
		GithubRepositoryID: repo.GithubRepositoryID,
		Number:             number,
		Status:             status,
		Note:               note,
		ReviewerIDs:        reviewerIDs,
		RelatedTicketIDs:   ticketIDs,
	}, nil
}

// composeStatus returns the status given, else asks with the preset marked, else the preset;
// a closed pull request without --status and without a terminal is refused.
func composeStatus(given, githubState string, repo client.GithubRepository, number int, p *Prompter) (string, error) {
	if given != "" {
		return given, nil
	}
	preset, hasPreset := PresetStatus(githubState)
	switch {
	case p != nil:
		if !hasPreset {
			_, _ = fmt.Fprintf(p.Out(), "%s#%d is closed on GitHub; choose its status.\n", guardrail.OneLine(repo.FullName), number)
		}
		return PickStatus(p, preset, "from GitHub")
	case hasPreset:
		return preset, nil
	default:
		return "", fmt.Errorf("%s#%d is closed on GitHub, so no status is preset: pass --status (%s)",
			guardrail.OneLine(repo.FullName), number, strings.Join(Statuses, ", "))
	}
}
