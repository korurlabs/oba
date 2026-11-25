package outreach

import (
	"errors"
	"fmt"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/docfile"
)

// defaultMailLanguage is the language of a mail file given without --lang, since the API requires
// one.
const defaultMailLanguage = "NL"

// CreateInput is one create. Type is mail, whatsapp or call, case ignored. A MAIL takes exactly
// one of TemplateID (a mail template id) and MailFile; Language applies to a MailFile only and
// is defaultMailLanguage when empty. DocumentTemplateIDs are document templates whose PDF the API
// copies onto the new mail as attachments. A WHATSAPP or CALL takes none of them. CampaignID,
// any type, links the outreach to a campaign; empty for none.
type CreateInput struct {
	RelationID          string
	ContactID           string
	Title               string
	Type                string
	TemplateID          string
	MailFile            string
	Language            string
	DocumentTemplateIDs []string
	CampaignID          string
}

// Create stores a DRAFT outreach. The flags are checked against the type, and the mail file is
// read, before the call.
func Create(c *client.Client, in CreateInput) (client.Outreach, error) {
	outreachType, err := Type(in.Type)
	if err != nil {
		return client.Outreach{}, err
	}
	req := client.NewOutreach{
		RelationID:          in.RelationID,
		ContactID:           in.ContactID,
		Title:               in.Title,
		Type:                outreachType,
		DocumentTemplateIDs: documentTemplateIDs(in.DocumentTemplateIDs),
	}
	if campaignID := strings.TrimSpace(in.CampaignID); campaignID != "" {
		req.CampaignID = &campaignID
	}
	if outreachType != client.OutreachTypeMail {
		if err := refuseMailFlags(outreachType, in, req.DocumentTemplateIDs); err != nil {
			return client.Outreach{}, err
		}
		return c.CreateOutreach(req)
	}
	if (in.TemplateID == "") == (in.MailFile == "") {
		return client.Outreach{}, errors.New("a MAIL outreach needs exactly one of --template and --mail-file")
	}
	if in.Language != "" && in.MailFile == "" {
		return client.Outreach{}, errors.New("--lang applies to --mail-file only; a mail template keeps its own language")
	}
	if in.TemplateID != "" {
		req.MailTemplateID = &in.TemplateID
		return c.CreateOutreach(req)
	}
	if err := docfile.CheckMailFile(in.MailFile); err != nil {
		return client.Outreach{}, err
	}
	mail, err := docfile.ReadMail(in.MailFile)
	if err != nil {
		return client.Outreach{}, err
	}
	lang := defaultMailLanguage
	if in.Language != "" {
		if lang, err = docfile.Language(in.Language); err != nil {
			return client.Outreach{}, err
		}
	}
	req.Mail = &client.OutreachMail{Subject: mail.Subject, Body: mail.Body, Language: lang}
	return c.CreateOutreach(req)
}

// Type turns a --type value (mail, whatsapp or call, case ignored) into the API's OutreachType.
func Type(value string) (string, error) {
	t := strings.ToUpper(strings.TrimSpace(value))
	switch t {
	case client.OutreachTypeMail, client.OutreachTypeWhatsApp, client.OutreachTypeCall:
		return t, nil
	default:
		return "", fmt.Errorf("unknown outreach type %q; use mail, whatsapp or call", value)
	}
}

// refuseMailFlags names every mail flag given for a WHATSAPP or CALL outreach, which has no mail
// and so no attachments.
func refuseMailFlags(outreachType string, in CreateInput, documentTemplates []string) error {
	var given []string
	if in.TemplateID != "" {
		given = append(given, "--template")
	}
	if in.MailFile != "" {
		given = append(given, "--mail-file")
	}
	if in.Language != "" {
		given = append(given, "--lang")
	}
	if len(documentTemplates) > 0 {
		given = append(given, "--attach")
	}
	if len(given) == 0 {
		return nil
	}
	return fmt.Errorf("a %s outreach has no mail; leave out %s", outreachType, strings.Join(given, ", "))
}

// documentTemplateIDs trims the ids and drops empty ones, never nil, so that the body always
// carries the list.
func documentTemplateIDs(ids []string) []string {
	out := []string{}
	for _, id := range ids {
		if id = strings.TrimSpace(id); id != "" {
			out = append(out, id)
		}
	}
	return out
}
