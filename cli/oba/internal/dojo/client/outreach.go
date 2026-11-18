package client

import (
	"net/url"
)

// The channels of an outreach (OutreachType). A MAIL outreach owns a mail and is sent from Oba;
// a WHATSAPP or CALL outreach happens outside Oba and is recorded as done.
const (
	OutreachTypeMail     = "MAIL"
	OutreachTypeWhatsApp = "WHATSAPP"
	OutreachTypeCall     = "CALL"
)

// Outreach is one reach-out to one contact of a relation as the outreach endpoints return it.
// Contact is nil once the contact was deleted. SentTo and SentAt are set once it was sent or
// recorded as done: SentTo is the mail address of a MAIL, the phone of a WHATSAPP or CALL.
// MailSenderAddress is set for a sent MAIL only. Mail comes with the detail only (get, create,
// complete) and is nil for a WHATSAPP or CALL. The attachments of the mail are shrine
// attachments of entity type MAIL on Mail.ID, listed with ListAttachments.
type Outreach struct {
	ID                string              `json:"id"`
	Title             string              `json:"title"`
	Type              string              `json:"type"`
	Status            string              `json:"status"`
	RelationID        string              `json:"relationId"`
	Contact           *OutreachContact    `json:"contact"`
	SentTo            *string             `json:"sentTo"`
	SentAt            *string             `json:"sentAt"`
	MailSenderAddress *string             `json:"mailSenderAddress"`
	Mail              *OutreachMailDetail `json:"mail"`
	CreatedAt         string              `json:"createdAt"`
}

// OutreachContact is the contact an outreach goes to.
type OutreachContact struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

// OutreachMailDetail is the own mail of a MAIL outreach. MailTemplateID is the mail template it
// was copied from, nil when it was written by hand or the template is gone.
type OutreachMailDetail struct {
	ID             string  `json:"id"`
	MailTemplateID *string `json:"mailTemplateId"`
	Subject        string  `json:"subject"`
	Body           string  `json:"body"`
	Language       string  `json:"language"`
}

// NewOutreach is the body of an outreach create (OutreachCreateRequest). Type is required. A
// MAIL takes exactly one of MailTemplateID and Mail; a WHATSAPP or CALL takes neither and no
// document templates. The API copies the PDF of each DocumentTemplateIDs entry onto the new mail
// as an attachment, in the create's transaction.
type NewOutreach struct {
	RelationID          string        `json:"relationId"`
	ContactID           string        `json:"contactId"`
	Title               string        `json:"title"`
	Type                string        `json:"type"`
	MailTemplateID      *string       `json:"mailTemplateId,omitempty"`
	Mail                *OutreachMail `json:"mail,omitempty"`
	DocumentTemplateIDs []string      `json:"documentTemplateIds"`
}

// OutreachMail is a mail written for one outreach. The API requires its language.
type OutreachMail struct {
	Subject  string `json:"subject"`
	Body     string `json:"body"`
	Language string `json:"language"`
}

// ListOutreaches reads every outreach of the organization, newest first, filtered by the
// non-empty query values (relationId, status).
func (c *Client) ListOutreaches(query url.Values) ([]Outreach, error) {
	return ListPages[Outreach](c, outreachesPath, query)
}

// GetOutreach reads one outreach with its detail; a MAIL carries its mail and the mail's id.
func (c *Client) GetOutreach(outreachID string) (Outreach, error) {
	body, err := c.Get(outreachesPath + "/" + url.PathEscape(outreachID))
	if err != nil {
		return Outreach{}, err
	}
	return parseData[Outreach](body, "outreach "+outreachID)
}

// CreateOutreach stores a DRAFT outreach and returns its detail.
func (c *Client) CreateOutreach(outreach NewOutreach) (Outreach, error) {
	body, err := c.Post(outreachesPath, outreach)
	if err != nil {
		return Outreach{}, err
	}
	return parseData[Outreach](body, "the created outreach")
}

// CompleteOutreach records a DRAFT WHATSAPP or CALL outreach as done: the API sets it SENT, now,
// to the contact's phone, and returns its detail.
func (c *Client) CompleteOutreach(outreachID string) (Outreach, error) {
	body, err := c.Post(outreachesPath+"/"+url.PathEscape(outreachID)+"/complete", nil)
	if err != nil {
		return Outreach{}, err
	}
	return parseData[Outreach](body, "outreach "+outreachID)
}

const outreachesPath = "/otak/outreaches"
