package documenttemplate

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/docfile"
)

// CreateInput is one create: the .html file, type and language, the name (the file name without
// extension when empty) and the legal entity it serves (every legal entity when empty).
type CreateInput struct {
	File          string
	Type          string
	Language      string
	Name          string
	LegalEntityID string
}

// request checks every value and builds the POST body, before any call.
func (in CreateInput) request() (client.DocumentTemplateRequest, error) {
	if strings.TrimSpace(in.File) == "" {
		return client.DocumentTemplateRequest{}, errors.New("--file is required")
	}
	if strings.TrimSpace(in.Type) == "" {
		return client.DocumentTemplateRequest{}, errors.New("--type is required")
	}
	docType, err := docfile.Type(in.Type)
	if err != nil {
		return client.DocumentTemplateRequest{}, err
	}
	if strings.TrimSpace(in.Language) == "" {
		return client.DocumentTemplateRequest{}, errors.New("--language is required")
	}
	language, err := docfile.Language(in.Language)
	if err != nil {
		return client.DocumentTemplateRequest{}, err
	}
	name := strings.TrimSpace(in.Name)
	if name == "" {
		name = docfile.BaseName(in.File)
	}
	req := client.DocumentTemplateRequest{Type: docType, Name: name, Language: language}
	id, err := legalEntityID(in.LegalEntityID)
	if err != nil {
		return client.DocumentTemplateRequest{}, err
	}
	if id != "" {
		req.LegalEntityID = &id
	}
	return req, nil
}

// Check refuses a missing or invalid value without a call.
func (in CreateInput) Check() error {
	_, err := in.request()
	return err
}

// Create creates a template from an .html file. The file is read and rendered to PDF before the
// first call; the create and the files upload are two calls, and when the upload fails the error
// names the new template's id and the upload command that finishes it, so that a retry never
// creates a second template.
func Create(ctx context.Context, c *client.Client, in CreateInput) (client.DocumentTemplate, error) {
	req, err := in.request()
	if err != nil {
		return client.DocumentTemplate{}, err
	}
	files, err := docfile.Load(ctx, in.File, "a document template")
	if err != nil {
		return client.DocumentTemplate{}, err
	}
	created, err := c.CreateDocumentTemplate(req)
	if err != nil {
		return client.DocumentTemplate{}, err
	}
	withFiles, err := c.ReplaceDocumentTemplateFiles(created.ID, files)
	if err != nil {
		return created, fmt.Errorf("created document template %s, but its files were not uploaded, so it cannot be used yet; "+
			"finish it with 'oba document template upload %s %s': %w", created.ID, created.ID, in.File, err)
	}
	return withFiles, nil
}
