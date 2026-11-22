package client

import (
	"fmt"
	"net/url"
)

// documentTemplatesPath is the API path of the organization's document templates.
const documentTemplatesPath = "/document-templates"

// DocumentTemplate is a reusable HTML document of the organization, with its PDF, as the
// template endpoints return it. The list returns the fields up to UpdatedAt; the detail adds
// htmlUrl and pdfUrl, short-lived signed URLs that are nil until the files are uploaded, and
// createdAt. A nil LegalEntityID means every legal entity of the organization may use it.
type DocumentTemplate struct {
	ID                   string  `json:"id"`
	Name                 string  `json:"name"`
	Type                 string  `json:"type"`
	Language             string  `json:"language"`
	LegalEntityID        *string `json:"legalEntityId"`
	LegalEntityShortName *string `json:"legalEntityShortName"`
	Ready                bool    `json:"ready"`
	UpdatedAt            string  `json:"updatedAt"`
	HTMLURL              *string `json:"htmlUrl,omitempty"`
	PDFURL               *string `json:"pdfUrl,omitempty"`
	CreatedAt            string  `json:"createdAt,omitempty"`
}

// DocumentTemplateRequest is the body of POST /document-templates: the metadata only, since the
// HTML and the PDF go up in a files upload of their own. A nil LegalEntityID is sent as null.
type DocumentTemplateRequest struct {
	Type          string  `json:"type"`
	Name          string  `json:"name"`
	Language      string  `json:"language"`
	LegalEntityID *string `json:"legalEntityId"`
}

func documentTemplatePath(templateID string) string {
	return documentTemplatesPath + "/" + url.PathEscape(templateID)
}

// ListDocumentTemplates reads every template of the organization, filtered by the non-empty
// query values (type, language, legalEntityId, search).
func (c *Client) ListDocumentTemplates(query url.Values) ([]DocumentTemplate, error) {
	return ListPages[DocumentTemplate](c, documentTemplatesPath, query)
}

// GetDocumentTemplate reads one template with its signed file URLs.
func (c *Client) GetDocumentTemplate(templateID string) (DocumentTemplate, error) {
	body, err := c.Get(documentTemplatePath(templateID))
	if err != nil {
		return DocumentTemplate{}, err
	}
	return parseData[DocumentTemplate](body, "document template "+templateID)
}

// CreateDocumentTemplate creates a template without files and returns it.
func (c *Client) CreateDocumentTemplate(req DocumentTemplateRequest) (DocumentTemplate, error) {
	body, err := c.Post(documentTemplatesPath, req)
	if err != nil {
		return DocumentTemplate{}, err
	}
	created, err := parseData[DocumentTemplate](body, "the created document template")
	if err != nil {
		return DocumentTemplate{}, fmt.Errorf("the document template was created, but the response could not be read: %w", err)
	}
	return created, nil
}

// ReplaceDocumentTemplateFiles uploads a template's HTML and PDF, replacing the stored ones.
func (c *Client) ReplaceDocumentTemplateFiles(templateID string, files DocumentFiles) (DocumentTemplate, error) {
	body, err := c.PutMultipart(documentTemplatePath(templateID)+"/files", files.parts())
	if err != nil {
		return DocumentTemplate{}, err
	}
	return parseData[DocumentTemplate](body, "document template "+templateID)
}
