package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// The multipart part names and media types of a template files upload.
const (
	PartHTML        = "html"
	PartPDF         = "pdf"
	ContentTypeHTML = "text/html; charset=utf-8"
	ContentTypePDF  = "application/pdf"
)

// DocumentTemplate is a reusable HTML document of the organization, with its PDF, as the
// template endpoints return it. The list returns id, name, type, language, ready and updatedAt;
// the detail adds htmlUrl and pdfUrl, short-lived signed URLs that are nil until the files are
// uploaded.
type DocumentTemplate struct {
	ID        string  `json:"id"`
	Name      string  `json:"name"`
	Type      string  `json:"type"`
	Language  string  `json:"language"`
	Ready     bool    `json:"ready"`
	HTMLURL   *string `json:"htmlUrl"`
	PDFURL    *string `json:"pdfUrl"`
	UpdatedAt string  `json:"updatedAt"`
}

// DocumentContent is the body of a template create or update (DocumentTemplateCreateRequest,
// DocumentTemplateUpdateRequest): the metadata only, since
// the HTML and the PDF go up in a files upload of their own.
type DocumentContent struct {
	Name     string `json:"name"`
	Type     string `json:"type"`
	Language string `json:"language"`
}

// ListTemplates reads every template of the organization, filtered by the non-empty query
// values (type, language, search).
func (c *Client) ListTemplates(query url.Values) ([]DocumentTemplate, error) {
	return ListPages[DocumentTemplate](c, templatesPath, query)
}

// GetTemplate reads one template with its signed file URLs.
func (c *Client) GetTemplate(templateID string) (DocumentTemplate, error) {
	body, err := c.Get(templatePath(templateID))
	if err != nil {
		return DocumentTemplate{}, err
	}
	return parseData[DocumentTemplate](body, "template "+templateID)
}

// CreateTemplate creates a template and returns it.
func (c *Client) CreateTemplate(content DocumentContent) (DocumentTemplate, error) {
	body, err := c.Post(templatesPath, content)
	if err != nil {
		return DocumentTemplate{}, err
	}
	return parseData[DocumentTemplate](body, "the created template")
}

// UpdateTemplate replaces name, type and language of a template; its files stay.
func (c *Client) UpdateTemplate(templateID string, content DocumentContent) (DocumentTemplate, error) {
	body, err := c.Put(templatePath(templateID), content)
	if err != nil {
		return DocumentTemplate{}, err
	}
	return parseData[DocumentTemplate](body, "template "+templateID)
}

// ReplaceTemplateFiles uploads a template's HTML and PDF, replacing the stored ones.
func (c *Client) ReplaceTemplateFiles(templateID string, files DocumentFiles) (DocumentTemplate, error) {
	body, err := c.PutMultipart(templatePath(templateID)+"/files", files.parts())
	if err != nil {
		return DocumentTemplate{}, err
	}
	return parseData[DocumentTemplate](body, "template "+templateID)
}

// DocumentFiles is the HTML and its PDF of a template.
type DocumentFiles struct {
	// BaseName names the uploaded files: BaseName.html and BaseName.pdf.
	BaseName string
	HTML     []byte
	PDF      []byte
}

func (f DocumentFiles) parts() []Part {
	return []Part{
		{Name: PartHTML, FileName: f.BaseName + ".html", ContentType: ContentTypeHTML, Data: f.HTML},
		{Name: PartPDF, FileName: f.BaseName + ".pdf", ContentType: ContentTypePDF, Data: f.PDF},
	}
}

const templatesPath = "/otak/document-templates"

func templatePath(templateID string) string {
	return templatesPath + "/" + url.PathEscape(templateID)
}

// parseData reads the data of a CustomResponse envelope; what names the object in the error.
func parseData[T any](body []byte, what string) (T, error) {
	var resp APIResponse[T]
	if err := json.Unmarshal(body, &resp); err != nil {
		var zero T
		return zero, fmt.Errorf("failed to read %s: %w", what, err)
	}
	return resp.Data, nil
}
