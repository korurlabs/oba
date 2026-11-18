package client

import (
	"context"
	"net/http"
	"net/url"
	"strconv"
)

// Locales of a page translation, as the API names them.
const (
	LocaleTR = "TR"
	LocaleEN = "EN"
)

// Upsert outcomes, as the API names them.
const (
	OutcomeCreated   = "CREATED"
	OutcomeUpdated   = "UPDATED"
	OutcomeUnchanged = "UNCHANGED"
)

// Error codes the CLI tells apart. The codes of a denial (403, 404) are told apart by status.
const (
	// CodePageNotFound is a page that is missing or that the caller holds no level on.
	CodePageNotFound = "KLK_NOT_001"
	// CodeChangedSincePush is a page edited in Kalkan since the last push; errorValues carry
	// path, updatedById, updatedByName and updatedAt.
	CodeChangedSincePush = "KLK_STA_004"
	// CodeConcurrentWrite is a write that raced another between the version check and the commit.
	CodeConcurrentWrite = "GEN_STA_001"
	// CodeNoAppAccess is a caller without the organization's grant to the Kalkan app.
	CodeNoAppAccess = "GRP_SEC_001"
)

// PageListItem is one page of the tree, without its bodies. ParentID and RootID are empty on a
// root page; Level is the caller's level and set on root pages only.
type PageListItem struct {
	ID         string            `json:"id"`
	ParentID   string            `json:"parentId,omitempty"`
	RootID     string            `json:"rootId,omitempty"`
	Slug       string            `json:"slug"`
	Path       string            `json:"path"`
	Position   int               `json:"position"`
	Compliance bool              `json:"compliance"`
	Titles     map[string]string `json:"titles"`
	Level      string            `json:"level,omitempty"`
}

// PageTranslation is the live translation of a page in one locale.
type PageTranslation struct {
	Locale        string `json:"locale"`
	Title         string `json:"title"`
	Body          string `json:"body"`
	UpdatedAt     string `json:"updatedAt"`
	UpdatedByName string `json:"updatedByName"`
}

// PageDetail is a page with its translations and the caller's level on its root.
type PageDetail struct {
	ID           string            `json:"id"`
	ParentID     string            `json:"parentId,omitempty"`
	RootID       string            `json:"rootId,omitempty"`
	Slug         string            `json:"slug"`
	Path         string            `json:"path"`
	Compliance   bool              `json:"compliance"`
	Version      int64             `json:"version"`
	Level        string            `json:"level"`
	Translations []PageTranslation `json:"translations"`
	UpdatedAt    string            `json:"updatedAt"`
}

// TranslationInput is the title and Markdown body of a page in one locale.
type TranslationInput struct {
	Locale string `json:"locale"`
	Title  string `json:"title"`
	Body   string `json:"body"`
}

// UpsertInput writes a page by its path of slugs. A nil Position or Compliance keeps the stored
// value; Force overwrites a page edited in Kalkan since the last push.
type UpsertInput struct {
	Path         string             `json:"path"`
	Position     *int               `json:"position,omitempty"`
	Compliance   *bool              `json:"compliance,omitempty"`
	Translations []TranslationInput `json:"translations"`
	Force        bool               `json:"force"`
}

// UpsertResult is what an upsert did, or would do on a dry run (then ID is empty and Version 0
// for a page it would create).
type UpsertResult struct {
	ID      string `json:"id"`
	Path    string `json:"path"`
	Outcome string `json:"outcome"`
	Version int64  `json:"version"`
}

// ListPages returns every live page under a root the caller can see, parents before children.
func (c *Client) ListPages(ctx context.Context) ([]PageListItem, error) {
	var out []PageListItem
	err := c.doJSON(ctx, http.MethodGet, "/kalkan/pages", nil, &out)
	return out, err
}

// GetPage returns one page with its translations.
func (c *Client) GetPage(ctx context.Context, pageID string) (PageDetail, error) {
	var out PageDetail
	err := c.doJSON(ctx, http.MethodGet, "/kalkan/pages/"+url.PathEscape(pageID), nil, &out)
	return out, err
}

// UpsertPage creates or updates the page at in.Path. With dryRun the server runs every check and
// answers the outcome without writing.
func (c *Client) UpsertPage(ctx context.Context, in UpsertInput, dryRun bool) (UpsertResult, error) {
	var out UpsertResult
	path := "/kalkan/pages/by-path?dryRun=" + strconv.FormatBool(dryRun)
	err := c.doJSON(ctx, http.MethodPut, path, in, &out)
	return out, err
}
