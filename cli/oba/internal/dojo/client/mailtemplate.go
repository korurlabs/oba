package client

import (
	"net/url"
)

// MailTemplate is a reusable mail of the organization as the mail template endpoints return it.
// The list returns id, name, language, subject and updatedAt; the detail adds body and createdAt.
// A mail template is usable as soon as it exists: it has no files and no ready state.
type MailTemplate struct {
	ID        string `json:"id"`
	Name      string `json:"name"`
	Language  string `json:"language"`
	Subject   string `json:"subject"`
	Body      string `json:"body"`
	UpdatedAt string `json:"updatedAt"`
	CreatedAt string `json:"createdAt"`
}

// MailTemplateContent is the body of a mail template create or update (MailTemplateCreateRequest,
// MailTemplateUpdateRequest). Every field is required; an update replaces all four.
type MailTemplateContent struct {
	Name     string `json:"name"`
	Language string `json:"language"`
	Subject  string `json:"subject"`
	Body     string `json:"body"`
}

// ListMailTemplates reads every mail template of the organization, filtered by the non-empty
// query values (language, search).
func (c *Client) ListMailTemplates(query url.Values) ([]MailTemplate, error) {
	return ListPages[MailTemplate](c, mailTemplatesPath, query)
}

// GetMailTemplate reads one mail template with its subject and body.
func (c *Client) GetMailTemplate(mailTemplateID string) (MailTemplate, error) {
	body, err := c.Get(mailTemplatePath(mailTemplateID))
	if err != nil {
		return MailTemplate{}, err
	}
	return parseData[MailTemplate](body, "mail template "+mailTemplateID)
}

// CreateMailTemplate creates a mail template and returns it.
func (c *Client) CreateMailTemplate(content MailTemplateContent) (MailTemplate, error) {
	body, err := c.Post(mailTemplatesPath, content)
	if err != nil {
		return MailTemplate{}, err
	}
	return parseData[MailTemplate](body, "the created mail template")
}

// UpdateMailTemplate replaces name, language, subject and body of a mail template. Mails copied
// from it before keep their own content.
func (c *Client) UpdateMailTemplate(mailTemplateID string, content MailTemplateContent) (MailTemplate, error) {
	body, err := c.Put(mailTemplatePath(mailTemplateID), content)
	if err != nil {
		return MailTemplate{}, err
	}
	return parseData[MailTemplate](body, "mail template "+mailTemplateID)
}

const mailTemplatesPath = "/otak/mail-templates"

func mailTemplatePath(mailTemplateID string) string {
	return mailTemplatesPath + "/" + url.PathEscape(mailTemplateID)
}
