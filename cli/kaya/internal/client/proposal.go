package client

import (
	"context"
	"net/http"
)

// Encodings of a proposed file's content.
const (
	EncodingUTF8   = "UTF_8"
	EncodingBase64 = "BASE64"
)

// ProposalFile is one path of a proposal: its new state, or its removal. Content is a pointer
// because an empty file is sent as "" while a link or a removal sends none.
type ProposalFile struct {
	Path          string  `json:"path"`
	Mode          string  `json:"mode,omitempty"`
	Encoding      string  `json:"encoding,omitempty"`
	Content       *string `json:"content,omitempty"`
	SymlinkTarget string  `json:"symlinkTarget,omitempty"`
	Deleted       bool    `json:"deleted,omitempty"`
}

// ProposalRequest is the body of POST /kaya/delivery/proposals: the files edited on top of the
// release the checkout runs.
type ProposalRequest struct {
	RemoteURL     string         `json:"remoteUrl"`
	ReleaseNumber int            `json:"releaseNumber"`
	Reason        string         `json:"reason"`
	Files         []ProposalFile `json:"files"`
}

// Proposal is the version a proposal created, in review. PackID names the pack that owns it and
// Name the pack's name.
type Proposal struct {
	ProposalID       string `json:"proposalId"`
	Number           int    `json:"number"`
	Status           string `json:"status"`
	PackID           string `json:"packId"`
	Name             string `json:"name"`
	BaseVersionID    string `json:"baseVersionId"`
	ReleaseNumber    int    `json:"releaseNumber"`
	ExecutableChange bool   `json:"executableChange"`
	ReviewURL        string `json:"reviewUrl"`
}

// Propose creates a change from a checkout and submits it for review in one call; an
// machine-token call.
func (c *Client) Propose(ctx context.Context, request ProposalRequest) (Proposal, error) {
	var proposal Proposal
	return proposal, c.doJSON(ctx, http.MethodPost, "/kaya/delivery/proposals", request, &proposal)
}
