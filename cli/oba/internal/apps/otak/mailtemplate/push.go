package mailtemplate

import (
	"errors"

	"github.com/korurlabs/oba-cli/internal/apps/otak/docfile"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// PushInput is one push: the .md file and the flags. Without ID a new mail template is created
// and Language is required; with ID the mail template is replaced, and an empty Language or Name
// keeps the stored value.
type PushInput struct {
	File     string
	ID       string
	Language string
	Name     string
}

// PushResult is the mail template after the push and whether it was created.
type PushResult struct {
	Template client.MailTemplate
	Created  bool
}

// Push creates or updates a mail template from a .md file. The file is read before the first
// call, so that a bad file sends nothing.
func Push(c *client.Client, in PushInput) (PushResult, error) {
	if err := docfile.CheckMailFile(in.File); err != nil {
		return PushResult{}, err
	}
	mail, err := docfile.ReadMail(in.File)
	if err != nil {
		return PushResult{}, err
	}
	language := ""
	if in.Language != "" {
		if language, err = docfile.Language(in.Language); err != nil {
			return PushResult{}, err
		}
	}
	content := client.MailTemplateContent{Name: in.Name, Language: language, Subject: mail.Subject, Body: mail.Body}

	if in.ID == "" {
		if content.Language == "" {
			return PushResult{}, errors.New("a new mail template needs --lang; pass --id to update an existing one")
		}
		if content.Name == "" {
			content.Name = docfile.BaseName(in.File)
		}
		created, err := c.CreateMailTemplate(content)
		if err != nil {
			return PushResult{}, err
		}
		return PushResult{Template: created, Created: true}, nil
	}

	if content.Language == "" || content.Name == "" {
		stored, err := c.GetMailTemplate(in.ID)
		if err != nil {
			return PushResult{}, err
		}
		if content.Language == "" {
			content.Language = stored.Language
		}
		if content.Name == "" {
			content.Name = stored.Name
		}
	}
	updated, err := c.UpdateMailTemplate(in.ID, content)
	if err != nil {
		return PushResult{}, err
	}
	return PushResult{Template: updated}, nil
}
