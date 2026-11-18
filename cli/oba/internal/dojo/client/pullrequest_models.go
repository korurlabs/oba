package client

// GithubRepository is a repository Kazan's GitHub App can read for the organization.
type GithubRepository struct {
	GithubRepositoryID int64  `json:"githubRepositoryId"`
	FullName           string `json:"fullName"`
	PrivateRepository  bool   `json:"privateRepository"`
}

// GithubPullRequestSummary is one row of a page of pull requests. State is OPEN, DRAFT, MERGED
// or CLOSED, as GitHub reports it.
type GithubPullRequestSummary struct {
	Number      int    `json:"number"`
	Title       string `json:"title"`
	State       string `json:"state"`
	AuthorLogin string `json:"authorLogin"`
	UpdatedAt   string `json:"updatedAt"`
	URL         string `json:"url"`
}

// GithubPullRequestPage is one page of a repository's pull requests, 30 rows at most, last
// updated first. HasMore is true when a next page may hold more rows; ResultsLimited is true
// when GitHub's search found more than the 1000 rows it shows.
type GithubPullRequestPage struct {
	Items          []GithubPullRequestSummary `json:"items"`
	Page           int                        `json:"page"`
	HasMore        bool                       `json:"hasMore"`
	ResultsLimited bool                       `json:"resultsLimited"`
}

// GithubPullRequest is one pull request as GitHub returns it. TicketKey is the upper-case ticket
// key found in the head branch, else in the title.
type GithubPullRequest struct {
	Number      int     `json:"number"`
	Title       string  `json:"title"`
	State       string  `json:"state"`
	AuthorLogin string  `json:"authorLogin"`
	UpdatedAt   string  `json:"updatedAt"`
	URL         string  `json:"url"`
	HeadRef     string  `json:"headRef"`
	BaseRef     string  `json:"baseRef"`
	TicketKey   *string `json:"ticketKey"`
}

// PullRequestShareReviewer is a reviewer of a share, in display order. ApprovedAt is nil while
// the reviewer has not approved.
type PullRequestShareReviewer struct {
	UserID     string  `json:"userId"`
	FirstName  string  `json:"firstName"`
	LastName   string  `json:"lastName"`
	ApprovedAt *string `json:"approvedAt"`
}

// PullRequestShareTicket is a related ticket of a share, in display order. BoardName, TicketKey
// and TicketTitle are stored when the ticket is added, so a later rename does not change them.
// CanOpen is true when the ticket is live and on a board the reader is a member of.
type PullRequestShareTicket struct {
	TicketID    string `json:"ticketId"`
	BoardID     string `json:"boardId"`
	BoardName   string `json:"boardName"`
	TicketKey   string `json:"ticketKey"`
	TicketTitle string `json:"ticketTitle"`
	CanOpen     bool   `json:"canOpen"`
}

// PullRequestShare is a pull request shared in the organization's PR channel. Status is the
// team's review status (DRAFT, OPEN, IN_REVIEW, MERGED, DISCUSS), chosen by the author, not
// GitHub's state. SummaryText is the card as plain text, the same for every reader. ChannelID
// and MessageID name the share's chat card; both are nil once the card is gone.
type PullRequestShare struct {
	ID                 string                     `json:"id"`
	AuthorID           string                     `json:"authorId"`
	AuthorFirstName    string                     `json:"authorFirstName"`
	AuthorLastName     string                     `json:"authorLastName"`
	RepositoryFullName string                     `json:"repositoryFullName"`
	Number             int                        `json:"number"`
	Title              string                     `json:"title"`
	URL                string                     `json:"url"`
	GithubAuthorLogin  string                     `json:"githubAuthorLogin"`
	HeadRef            string                     `json:"headRef"`
	BaseRef            string                     `json:"baseRef"`
	TicketKey          *string                    `json:"ticketKey"`
	Status             string                     `json:"status"`
	Note               *string                    `json:"note"`
	Reviewers          []PullRequestShareReviewer `json:"reviewers"`
	RelatedTickets     []PullRequestShareTicket   `json:"relatedTickets"`
	CreatedAt          string                     `json:"createdAt"`
	EditedAt           *string                    `json:"editedAt"`
	SummaryText        string                     `json:"summaryText"`
	ChannelID          *string                    `json:"channelId"`
	MessageID          *string                    `json:"messageId"`
}

// PullRequestSharePreview is the card text a share request would post, with the pull request as
// GitHub returns it. Nothing is saved.
type PullRequestSharePreview struct {
	PullRequest GithubPullRequest `json:"pullRequest"`
	SummaryText string            `json:"summaryText"`
}

// PullRequestShareRequest is the body of a new, previewed or edited share. An edit leaves
// GithubRepositoryID and Number zero, so they are not sent: the pull request of a share never
// changes. ReviewerIDs and RelatedTicketIDs are always sent, an empty list for none, because the
// API requires them; an edit sends the full new lists in display order. A nil note is stored as
// none.
type PullRequestShareRequest struct {
	GithubRepositoryID int64    `json:"githubRepositoryId,omitempty"`
	Number             int      `json:"number,omitempty"`
	Status             string   `json:"status"`
	Note               *string  `json:"note,omitempty"`
	ReviewerIDs        []string `json:"reviewerIds"`
	RelatedTicketIDs   []string `json:"relatedTicketIds"`
}

// PullRequestSharePage is one page of the PR channel's shares, newest first, with the totals
// the footer of the list names.
type PullRequestSharePage struct {
	Shares        []PullRequestShare
	CurrentPage   int
	TotalPages    int
	TotalElements int
}
