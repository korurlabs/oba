package client

import (
	"encoding/json"
	"fmt"
	"net/url"
	"strconv"
)

// pullRequestSharesPath is the collection of the PR channel's shares of the organization.
const pullRequestSharesPath = kazanPath + "/pull-request-shares"

// githubRepositoriesPath is the collection of the repositories Kazan's GitHub App can read.
const githubRepositoriesPath = kazanPath + "/github/repositories"

func pullRequestSharePath(shareID string) string {
	return pullRequestSharesPath + "/" + url.PathEscape(shareID)
}

func githubPullRequestsPath(repoID int64) string {
	return githubRepositoriesPath + "/" + strconv.FormatInt(repoID, 10) + "/pull-requests"
}

// ListGithubRepositories reads the repositories Kazan's GitHub App can read for the
// organization. The API answers with one plain list, not pages.
func (c *Client) ListGithubRepositories() ([]GithubRepository, error) {
	body, err := c.Get(githubRepositoriesPath)
	if err != nil {
		return nil, err
	}
	return parseData[[]GithubRepository](body, "the GitHub repositories")
}

// ListGithubPullRequests reads one page of a repository's pull requests, last updated first.
// query holds page and the optional search, states and authors filters.
func (c *Client) ListGithubPullRequests(repoID int64, query url.Values) (GithubPullRequestPage, error) {
	body, err := c.Get(githubPullRequestsPath(repoID) + "?" + query.Encode())
	if err != nil {
		return GithubPullRequestPage{}, err
	}
	return parseData[GithubPullRequestPage](body, "the pull requests")
}

// GetGithubPullRequest reads one pull request as GitHub returns it.
func (c *Client) GetGithubPullRequest(repoID int64, number int) (GithubPullRequest, error) {
	body, err := c.Get(githubPullRequestsPath(repoID) + "/" + strconv.Itoa(number))
	if err != nil {
		return GithubPullRequest{}, err
	}
	return parseData[GithubPullRequest](body, fmt.Sprintf("pull request #%d", number))
}

// ListPullRequestShares reads one page of the PR channel's shares, newest first. query holds
// page, amount and the optional statuses, authorIds and awaitingMyReview filters.
func (c *Client) ListPullRequestShares(query url.Values) (PullRequestSharePage, error) {
	body, err := c.Get(withQuery(pullRequestSharesPath, query))
	if err != nil {
		return PullRequestSharePage{}, err
	}
	shares, totals, err := parsePage[PullRequestShare](body, "the shares")
	if err != nil {
		return PullRequestSharePage{}, err
	}
	return PullRequestSharePage{
		Shares: shares, CurrentPage: totals.CurrentPage,
		TotalPages: totals.TotalPages, TotalElements: totals.TotalElements,
	}, nil
}

// GetPullRequestShare reads one share of the organization, with its data as the API sent it.
func (c *Client) GetPullRequestShare(shareID string) (PullRequestShare, json.RawMessage, error) {
	body, err := c.Get(pullRequestSharePath(shareID))
	if err != nil {
		return PullRequestShare{}, nil, err
	}
	share, err := parseData[PullRequestShare](body, "the share")
	if err != nil {
		return PullRequestShare{}, nil, err
	}
	raw, err := parseData[json.RawMessage](body, "the share")
	if err != nil {
		return PullRequestShare{}, nil, err
	}
	return share, raw, nil
}

// CreatePullRequestShare shares a pull request: it posts one card in the PR channel.
func (c *Client) CreatePullRequestShare(req PullRequestShareRequest) (PullRequestShare, error) {
	body, err := c.Post(pullRequestSharesPath, req)
	if err != nil {
		return PullRequestShare{}, err
	}
	return parseData[PullRequestShare](body, "the new share")
}

// PreviewPullRequestShare renders the card a share request would post, without saving it.
func (c *Client) PreviewPullRequestShare(req PullRequestShareRequest) (PullRequestSharePreview, error) {
	body, err := c.Post(pullRequestSharesPath+"/preview", req)
	if err != nil {
		return PullRequestSharePreview{}, err
	}
	return parseData[PullRequestSharePreview](body, "the preview")
}

// UpdatePullRequestShare replaces the status, note, reviewers and related tickets of a share.
func (c *Client) UpdatePullRequestShare(shareID string, req PullRequestShareRequest) (PullRequestShare, error) {
	body, err := c.Put(pullRequestSharePath(shareID), req)
	if err != nil {
		return PullRequestShare{}, err
	}
	return parseData[PullRequestShare](body, "the edited share")
}

// DeletePullRequestShare deletes a share; its card leaves the PR channel.
func (c *Client) DeletePullRequestShare(shareID string) error {
	_, err := c.Delete(pullRequestSharePath(shareID))
	return err
}

// ApprovePullRequestShare sets the caller's approval of a share; approving again changes
// nothing.
func (c *Client) ApprovePullRequestShare(shareID string) error {
	_, err := c.Put(pullRequestSharePath(shareID)+"/approval", nil)
	return err
}

// WithdrawPullRequestShareApproval clears the caller's approval of a share; withdrawing an
// approval not given changes nothing.
func (c *Client) WithdrawPullRequestShareApproval(shareID string) error {
	_, err := c.Delete(pullRequestSharePath(shareID) + "/approval")
	return err
}
