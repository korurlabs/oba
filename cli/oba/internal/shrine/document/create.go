package document

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/docfile"
)

// CreateInput is one create: the owner relation, the legal entity, title, type and language, and
// exactly one source, a template (TemplateID) or a local .html file (File).
type CreateInput struct {
	RelationID    string
	LegalEntityID string
	Title         string
	Type          string
	Language      string
	TemplateID    string
	File          string
}

// CreateResult is the document after the create and whether its files came from the local file.
type CreateResult struct {
	Document client.Document
	// Uploaded is true when the HTML of File and its PDF were uploaded.
	Uploaded bool
}

// request checks every value and builds the POST body, before any call.
func (in CreateInput) request() (client.DocumentCreateRequest, error) {
	if (strings.TrimSpace(in.TemplateID) == "") == (strings.TrimSpace(in.File) == "") {
		return client.DocumentCreateRequest{}, errors.New("pass exactly one of --template and --file")
	}
	relationID, err := flagID("--relation", in.RelationID, "oba otak relation list")
	if err != nil {
		return client.DocumentCreateRequest{}, err
	}
	legalEntityID, err := flagID("--legal-entity", in.LegalEntityID, "oba legal-entity list")
	if err != nil {
		return client.DocumentCreateRequest{}, err
	}
	title := strings.TrimSpace(in.Title)
	if title == "" {
		return client.DocumentCreateRequest{}, errors.New("--title is required")
	}
	if strings.TrimSpace(in.Type) == "" {
		return client.DocumentCreateRequest{}, errors.New("--type is required")
	}
	docType, err := docfile.Type(in.Type)
	if err != nil {
		return client.DocumentCreateRequest{}, err
	}
	if strings.TrimSpace(in.Language) == "" {
		return client.DocumentCreateRequest{}, errors.New("--language is required")
	}
	language, err := docfile.Language(in.Language)
	if err != nil {
		return client.DocumentCreateRequest{}, err
	}
	req := client.DocumentCreateRequest{
		EntityType:    client.DocumentEntityTypeRelation,
		EntityID:      relationID,
		LegalEntityID: legalEntityID,
		Title:         title,
		Type:          docType,
		Language:      language,
	}
	if strings.TrimSpace(in.TemplateID) != "" {
		templateID, err := flagID("--template", in.TemplateID, "oba document template list")
		if err != nil {
			return client.DocumentCreateRequest{}, err
		}
		req.TemplateID = &templateID
	}
	return req, nil
}

// Check refuses a missing or invalid value without a call.
func (in CreateInput) Check() error {
	_, err := in.request()
	return err
}

// Create creates a document. From a template, the API copies the template's files when it has
// them. From a file, the file is read and rendered to PDF before the first call, and the HTML
// and the PDF are uploaded after the create; when that upload fails, the error names the new
// document's id and the upload command that finishes it, so that a retry never creates a second
// document.
func Create(ctx context.Context, c *client.Client, in CreateInput) (CreateResult, error) {
	req, err := in.request()
	if err != nil {
		return CreateResult{}, err
	}
	var files client.DocumentFiles
	if req.TemplateID == nil {
		if files, err = docfile.Load(ctx, in.File, "a document"); err != nil {
			return CreateResult{}, err
		}
	}
	created, err := c.CreateDocument(req)
	if err != nil {
		return CreateResult{}, err
	}
	result := CreateResult{Document: created}
	if req.TemplateID != nil {
		return result, nil
	}
	withFiles, err := c.ReplaceDocumentFiles(created.ID, files)
	if err != nil {
		return result, fmt.Errorf("created document %s, but its files were not uploaded; finish it with "+
			"'oba document upload %s %s': %w", created.ID, created.ID, in.File, err)
	}
	result.Document = withFiles
	result.Uploaded = true
	return result, nil
}
