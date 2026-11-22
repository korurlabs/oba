package mailtemplate

import (
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/docfile"
)

// Pull writes a mail template into dir as <name>.md, in the form Push reads. It returns the path
// written.
func Pull(c *client.Client, mailTemplateID, dir string, overwrite bool) ([]string, error) {
	t, err := c.GetMailTemplate(mailTemplateID)
	if err != nil {
		return nil, err
	}
	mail := docfile.Mail{Subject: t.Subject, Body: t.Body}
	files := []docfile.File{{Ext: docfile.ExtMail, Data: []byte(mail.Format())}}
	return docfile.WriteFiles(dir, t.Name, t.ID, files, overwrite)
}
