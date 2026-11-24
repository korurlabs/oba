package client

import (
	"net/url"
	"strconv"
)

// Locales of a Kalkan page translation, as the API names them.
const (
	KalkanLocaleTR = "TR"
	KalkanLocaleEN = "EN"
)

// Error codes of the Kalkan page endpoints a command tells apart. A denial (403, 404) is told
// apart by its status.
const (
	// CodeKalkanPageNotFound is a page that is missing or that the caller holds no level on.
	CodeKalkanPageNotFound = "KLK_NOT_001"
	// CodeKalkanPageChanged is a page edited in Kalkan since the last upload; its errorValues carry
	// path, updatedById, updatedByName and updatedAt.
	CodeKalkanPageChanged = "KLK_STA_004"
	// CodeConcurrentWrite is a write that raced another between the version check and the commit.
	CodeConcurrentWrite = "GEN_STA_001"
)

// KalkanPageListItem is one page of the tree, without its bodies. ParentID and RootID are empty
// on a root page; Level is the caller's level and set on root pages only.
type KalkanPageListItem struct {
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

// KalkanPageTranslation is the live translation of a page in one locale.
type KalkanPageTranslation struct {
	Locale        string `json:"locale"`
	Title         string `json:"title"`
	Body          string `json:"body"`
	UpdatedAt     string `json:"updatedAt"`
	UpdatedByName string `json:"updatedByName"`
}

// KalkanPageDetail is a page with its translations and the caller's level on its root.
type KalkanPageDetail struct {
	ID           string                  `json:"id"`
	ParentID     string                  `json:"parentId,omitempty"`
	RootID       string                  `json:"rootId,omitempty"`
	Slug         string                  `json:"slug"`
	Path         string                  `json:"path"`
	Compliance   bool                    `json:"compliance"`
	Version      int64                   `json:"version"`
	Level        string                  `json:"level"`
	Translations []KalkanPageTranslation `json:"translations"`
	UpdatedAt    string                  `json:"updatedAt"`
}

// KalkanTranslationInput is the title and Markdown body of a page in one locale.
type KalkanTranslationInput struct {
	Locale string `json:"locale"`
	Title  string `json:"title"`
	Body   string `json:"body"`
}

// KalkanPageUpsert writes a page by its path of slugs. A nil Position or Compliance keeps the
// stored value; Force overwrites a page edited in Kalkan since the last upload. Translations not
// named keep their stored content.
type KalkanPageUpsert struct {
	Path         string                   `json:"path"`
	Position     *int                     `json:"position,omitempty"`
	Compliance   *bool                    `json:"compliance,omitempty"`
	Translations []KalkanTranslationInput `json:"translations"`
	Force        bool                     `json:"force"`
}

// KalkanPageUpsertResult is what an upsert did, or would do on a dry run (then ID is empty and
// Version 0 for a page it would create). Outcome is CREATED, UPDATED or UNCHANGED.
type KalkanPageUpsertResult struct {
	ID      string `json:"id"`
	Path    string `json:"path"`
	Outcome string `json:"outcome"`
	Version int64  `json:"version"`
}

// ListKalkanPages reads every live page under a root page the caller can see, parents before
// children.
func (c *Client) ListKalkanPages() ([]KalkanPageListItem, error) {
	body, err := c.Get(kalkanPagesPath)
	if err != nil {
		return nil, err
	}
	return parseData[[]KalkanPageListItem](body, "the Kalkan pages")
}

// GetKalkanPage reads one page with its translations.
func (c *Client) GetKalkanPage(pageID string) (KalkanPageDetail, error) {
	body, err := c.Get(kalkanPagePath(pageID))
	if err != nil {
		return KalkanPageDetail{}, err
	}
	return parseData[KalkanPageDetail](body, "Kalkan page "+pageID)
}

// UpsertKalkanPage creates or updates the page at in.Path. With dryRun the API runs every check
// and answers the outcome without writing.
func (c *Client) UpsertKalkanPage(in KalkanPageUpsert, dryRun bool) (KalkanPageUpsertResult, error) {
	body, err := c.Put(kalkanPagesPath+"/by-path?dryRun="+strconv.FormatBool(dryRun), in)
	if err != nil {
		return KalkanPageUpsertResult{}, err
	}
	return parseData[KalkanPageUpsertResult](body, "the Kalkan page upsert of "+in.Path)
}

// DeleteKalkanPage soft-deletes a page and every page below it.
func (c *Client) DeleteKalkanPage(pageID string) error {
	_, err := c.Delete(kalkanPagePath(pageID))
	return err
}

const kalkanPagesPath = "/kalkan/pages"

func kalkanPagePath(pageID string) string {
	return kalkanPagesPath + "/" + url.PathEscape(pageID)
}
