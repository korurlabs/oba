package client

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"net/url"
)

// Error codes the seed handles by name.
const (
	// CodeSeedBlobsMissing (422): the seed names blobs the organization does not hold yet;
	// errorValues.missing lists their sha256.
	CodeSeedBlobsMissing = "KAY_CFG_009"
	// CodeBlobHashMismatch (400): the uploaded bytes do not hash to the path's sha256.
	CodeBlobHashMismatch = "KAY_CFG_005"
)

// File modes of a delivered file.
const (
	ModeFile       = "FILE"
	ModeExecutable = "EXECUTABLE"
	ModeSymlink    = "SYMLINK"
)

// KindPack is the kind of every seeded set: Kaya keeps all config in packs.
const KindPack = "PACK"

// Seed run statuses.
const (
	SeedPassed = "PASSED"
	SeedFailed = "FAILED"
)

// SeedFile is one file of a seeded set. SHA256 is empty for a SYMLINK, SymlinkTarget for the others.
type SeedFile struct {
	Path          string `json:"path"`
	SHA256        string `json:"sha256,omitempty"`
	Mode          string `json:"mode"`
	SymlinkTarget string `json:"symlinkTarget,omitempty"`
}

// SeedSet is a pack with every file it holds. Parent names its parent pack, a set of the request
// or a pack of the organization; empty for a root pack. A stored pack keeps its parent: the
// server refuses another one with 409 KAY_CFG_033.
type SeedSet struct {
	Kind   string     `json:"kind"`
	Name   string     `json:"name"`
	Parent string     `json:"parent,omitempty"`
	Files  []SeedFile `json:"files"`
}

// SeedRepositoryPack is a pack a repository uses: at its root when RootPath is empty, else
// mounted in that folder.
type SeedRepositoryPack struct {
	Name     string `json:"name"`
	RootPath string `json:"rootPath,omitempty"`
}

// SeedRepository is a repository the seed connects, with the full list of packs it uses in
// display order; the list replaces the stored one.
type SeedRepository struct {
	RemoteURL string               `json:"remoteUrl"`
	Name      string               `json:"name,omitempty"`
	Packs     []SeedRepositoryPack `json:"packs"`
}

// NotImported is a path the CLI left out on purpose, with the reason.
type NotImported struct {
	Path   string `json:"path"`
	Reason string `json:"reason"`
}

// Drift is a managed file whose disk copy differs from .agents/.managed.json. ActualSHA256 is
// empty when the file is missing.
type Drift struct {
	Path           string `json:"path"`
	Pack           string `json:"pack,omitempty"`
	ExpectedSHA256 string `json:"expectedSha256"`
	ActualSHA256   string `json:"actualSha256,omitempty"`
}

// SeedRequest is the body of POST /kaya/seed-runs. Repositories left out keep their stored pack list.
type SeedRequest struct {
	SourceRef    string           `json:"sourceRef"`
	Sets         []SeedSet        `json:"sets"`
	Repositories []SeedRepository `json:"repositories,omitempty"`
	NotImported  []NotImported    `json:"notImported,omitempty"`
	Drift        []Drift          `json:"drift,omitempty"`
}

// Violation is a validation rule a file or a repository's composition breaks. RepositoryID names
// the repository of a composition violation; OwnerIDs are the packs it involves; RootPath is the
// mount folder of a MOUNT_RULE_SCOPE violation.
type Violation struct {
	Path         string   `json:"path,omitempty"`
	Rule         string   `json:"rule"`
	Message      string   `json:"message"`
	RepositoryID string   `json:"repositoryId,omitempty"`
	RootPath     string   `json:"rootPath,omitempty"`
	OwnerIDs     []string `json:"ownerIds,omitempty"`
}

// SeedSetReport is the outcome of one set. PackID is empty when the run created the pack and
// then discarded its writes.
type SeedSetReport struct {
	PackID           string `json:"packId"`
	Kind             string `json:"kind"`
	Name             string `json:"name"`
	Outcome          string `json:"outcome"`
	VersionID        string `json:"versionId"`
	VersionNumber    *int   `json:"versionNumber"`
	FileCount        int    `json:"fileCount"`
	RefusedCount     int    `json:"refusedCount"`
	ExecutableChange bool   `json:"executableChange"`
}

// SeedReleaseReport is a release the run created.
type SeedReleaseReport struct {
	RepositoryID   string `json:"repositoryId"`
	Number         int    `json:"number"`
	ManifestSHA256 string `json:"manifestSha256"`
	FileCount      int    `json:"fileCount"`
}

// SeedRefusedFile is a file left out of its version: either a file no kind delivers or that does
// not parse, with ErrorCode and Reason, or a file that breaks file rules, with Violations.
type SeedRefusedFile struct {
	SetKind    string      `json:"setKind"`
	SetName    string      `json:"setName"`
	Path       string      `json:"path"`
	ErrorCode  string      `json:"errorCode"`
	Reason     string      `json:"reason"`
	Violations []Violation `json:"violations"`
}

// SeedWarning is a size limit a seeded file breaks, imported anyway: a seed mirrors the
// repository as it is. Every warning names the set of its file.
type SeedWarning struct {
	SetKind   string    `json:"setKind"`
	SetName   string    `json:"setName"`
	Violation Violation `json:"violation"`
}

// SeedReport is the report of a seed run. Entities counts the stored entities per kind (RULE,
// SKILL, ...); Sets are in the order of the request.
type SeedReport struct {
	Applied     bool                `json:"applied"`
	Entities    map[string]int      `json:"entities"`
	Sets        []SeedSetReport     `json:"sets"`
	Releases    []SeedReleaseReport `json:"releases"`
	Refused     []SeedRefusedFile   `json:"refused"`
	Violations  []Violation         `json:"violations"`
	Warnings    []SeedWarning       `json:"warnings"`
	NotImported []NotImported       `json:"notImported"`
	Drift       []Drift             `json:"drift"`
}

// SeedRun is the answer of POST /kaya/seed-runs.
type SeedRun struct {
	ID        string     `json:"id"`
	SourceRef string     `json:"sourceRef"`
	Status    string     `json:"status"`
	Report    SeedReport `json:"report"`
	CreatedAt string     `json:"createdAt"`
}

// BlobUpload is the answer of PUT /kaya/blobs/{sha256}; Stored is false when the organization
// already held the blob.
type BlobUpload struct {
	SHA256 string `json:"sha256"`
	Size   int    `json:"size"`
	Stored bool   `json:"stored"`
}

// ReleaseEntry is one delivered path of a release.
type ReleaseEntry struct {
	Path     string `json:"path"`
	SHA256   string `json:"sha256"`
	Mode     string `json:"mode"`
	Target   string `json:"target"`
	AlwaysOn bool   `json:"alwaysOn"`
	// OverridesPath is the root path a mounted pack's file overrides; empty for every other file.
	OverridesPath string `json:"overridesPath"`
	// NarrowedBy is the mounted pack whose override narrowed this inherited rule, so SHA256 is
	// that of the narrowed copy; empty when the rule was not narrowed.
	NarrowedBy string `json:"narrowedBy"`
}

// Release is a repository's release with every delivered path.
type Release struct {
	ID             string         `json:"id"`
	RepositoryID   string         `json:"repositoryId"`
	Number         int            `json:"number"`
	ManifestSHA256 string         `json:"manifestSha256"`
	FileCount      int            `json:"fileCount"`
	Files          []ReleaseEntry `json:"files"`
}

// Repository is a repository of the organization; RemoteURL is normalised as the server stores it.
type Repository struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	RemoteURL string `json:"remoteUrl"`
	Status    string `json:"status"`
}

// Repositories lists every repository of the organization, archived ones included (MEMBER).
func (c *Client) Repositories(ctx context.Context) ([]Repository, error) {
	var out []Repository
	err := c.doJSON(ctx, http.MethodGet, "/kaya/repositories", nil, &out)
	return out, err
}

// UploadBlob stores content under its sha256 (OWNER). The server hashes the bytes again.
func (c *Client) UploadBlob(ctx context.Context, sha256 string, content []byte) (BlobUpload, error) {
	var out BlobUpload
	err := c.do(ctx, http.MethodPut, "/kaya/blobs/"+url.PathEscape(sha256), "application/octet-stream", content, &out)
	return out, err
}

// CreateSeedRun posts a seed run (OWNER) and returns its report.
func (c *Client) CreateSeedRun(ctx context.Context, req SeedRequest) (SeedRun, error) {
	var out SeedRun
	err := c.doJSON(ctx, http.MethodPost, "/kaya/seed-runs", req, &out)
	return out, err
}

// LatestRelease returns the newest release of a repository, or nil when it has none (MEMBER).
func (c *Client) LatestRelease(ctx context.Context, repositoryID string) (*Release, error) {
	var page []Release
	path := fmt.Sprintf("/kaya/repositories/%s/releases?page=1&amount=1", url.PathEscape(repositoryID))
	if err := c.doJSON(ctx, http.MethodGet, path, nil, &page); err != nil {
		return nil, err
	}
	if len(page) == 0 {
		return nil, nil
	}
	return c.Release(ctx, repositoryID, page[0].Number)
}

// Release reads one release of a repository with its delivered paths (MEMBER).
func (c *Client) Release(ctx context.Context, repositoryID string, number int) (*Release, error) {
	var out Release
	path := fmt.Sprintf("/kaya/repositories/%s/releases/%d", url.PathEscape(repositoryID), number)
	if err := c.doJSON(ctx, http.MethodGet, path, nil, &out); err != nil {
		return nil, err
	}
	return &out, nil
}

// MissingBlobs reads errorValues.missing of a KAY_CFG_009 refusal, each hash once.
func MissingBlobs(err error) []string {
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.Code != CodeSeedBlobsMissing {
		return nil
	}
	raw, ok := apiErr.ErrorValues["missing"].([]any)
	if !ok {
		return nil
	}
	missing := make([]string, 0, len(raw))
	seen := map[string]bool{}
	for _, value := range raw {
		if sha, ok := value.(string); ok && !seen[sha] {
			seen[sha] = true
			missing = append(missing, sha)
		}
	}
	return missing
}
