package client

import (
	"fmt"
	"net/url"
)

// documentsPath is the API path of the organization's documents.
const documentsPath = "/documents"

// DocumentEntityTypeRelation is the owner type of a document of an Otak relation, the only owner
// a document has today.
const DocumentEntityTypeRelation = "RELATION"

// Document is one document of the organization: an HTML file with its PDF that belongs to one
// owner (a relation), written in the name of one legal entity. The list
// (DocumentListResponse) returns the fields up to CreatedAt; the detail (DocumentDetailResponse),
// which get, create and the files upload return, adds the signed file URLs, nil without files,
// and the mails it was attached to. TemplateID names the template it was made from, nil when it
// was made from a file or the template is gone.
type Document struct {
	ID                   string             `json:"id"`
	Title                string             `json:"title"`
	Type                 string             `json:"type"`
	Language             string             `json:"language"`
	EntityType           string             `json:"entityType"`
	EntityID             string             `json:"entityId"`
	RelationID           *string            `json:"relationId"`
	RelationName         *string            `json:"relationName"`
	LegalEntityID        string             `json:"legalEntityId"`
	LegalEntityShortName *string            `json:"legalEntityShortName"`
	TemplateID           *string            `json:"templateId"`
	Ready                bool               `json:"ready"`
	SnapshotCount        int                `json:"snapshotCount"`
	UpdatedBy            *DocumentAuthor    `json:"updatedBy"`
	UpdatedAt            string             `json:"updatedAt"`
	CreatedAt            string             `json:"createdAt"`
	HTMLURL              *string            `json:"htmlUrl,omitempty"`
	PDFURL               *string            `json:"pdfUrl,omitempty"`
	Snapshots            []DocumentSnapshot `json:"snapshots,omitempty"`
}

// DocumentAuthor is the user who last changed a document; DisplayName is nil when the user is
// gone.
type DocumentAuthor struct {
	ID          string  `json:"id"`
	DisplayName *string `json:"displayName"`
}

// DocumentSnapshot is one PDF copy of a document attached to a mail, newest first.
type DocumentSnapshot struct {
	AttachmentID  string  `json:"attachmentId"`
	MailID        string  `json:"mailId"`
	OutreachTitle *string `json:"outreachTitle"`
	CreatedAt     string  `json:"createdAt"`
}

// DocumentCreateRequest is the body of POST /documents. With a ready TemplateID the API copies the
// template's files to the new document; without one the document has no files until they are
// uploaded.
type DocumentCreateRequest struct {
	EntityType    string  `json:"entityType"`
	EntityID      string  `json:"entityId"`
	LegalEntityID string  `json:"legalEntityId"`
	Title         string  `json:"title"`
	Type          string  `json:"type"`
	Language      string  `json:"language"`
	TemplateID    *string `json:"templateId"`
}

// documentAttachRequest is the body of POST /documents/{id}/attachments.
type documentAttachRequest struct {
	EntityType string `json:"entityType"`
	EntityID   string `json:"entityId"`
}

func documentPath(documentID string) string {
	return documentsPath + "/" + url.PathEscape(documentID)
}

// ListDocuments reads every document of the organization that matches the non-empty query
// values (relationId, legalEntityId, language, type, search), last changed first.
func (c *Client) ListDocuments(query url.Values) ([]Document, error) {
	return ListPages[Document](c, documentsPath, query)
}

// GetDocument reads one document with its signed file URLs and the mails it was attached to.
func (c *Client) GetDocument(documentID string) (Document, error) {
	body, err := c.Get(documentPath(documentID))
	if err != nil {
		return Document{}, err
	}
	return parseData[Document](body, "document "+documentID)
}

// CreateDocument creates a document and returns it.
func (c *Client) CreateDocument(req DocumentCreateRequest) (Document, error) {
	body, err := c.Post(documentsPath, req)
	if err != nil {
		return Document{}, err
	}
	created, err := parseData[Document](body, "the created document")
	if err != nil {
		return Document{}, fmt.Errorf("the document was created, but the response could not be read: %w", err)
	}
	return created, nil
}

// ReplaceDocumentFiles uploads a document's HTML and PDF, replacing the stored ones. Copies
// already attached to mails keep their own PDF.
func (c *Client) ReplaceDocumentFiles(documentID string, files DocumentFiles) (Document, error) {
	body, err := c.PutMultipart(documentPath(documentID)+"/files", files.parts())
	if err != nil {
		return Document{}, err
	}
	return parseData[Document](body, "document "+documentID)
}

// AttachDocumentToMail stores a copy of the document's current PDF as a new attachment of a mail
// that is not sent yet, named <title>.pdf; later uploads to the document leave the copy as it is.
func (c *Client) AttachDocumentToMail(documentID, mailID string) (Attachment, error) {
	body, err := c.Post(documentPath(documentID)+"/attachments",
		documentAttachRequest{EntityType: StorageEntityTypeMail, EntityID: mailID})
	if err != nil {
		return Attachment{}, err
	}
	return parseData[Attachment](body, "the attached copy of document "+documentID)
}
