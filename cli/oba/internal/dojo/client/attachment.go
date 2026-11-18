package client

import (
	"encoding/json"
	"fmt"
	"net/url"
)

// The StorageEntityType values of the Otak parents an attachment can belong to: the own mail of
// a MAIL outreach and a relation.
const (
	StorageEntityTypeMail     = "MAIL"
	StorageEntityTypeRelation = "RELATION"
)

// The multipart parts of an attachment upload (POST /storage/attachments): one "metadata" part,
// a JSON array with one entry per file, and one "files" part per file in the same order. The API
// compares each file part's filename with its metadata entry, so both come from one list.
const (
	partAttachmentMetadata = "metadata"
	partAttachmentFiles    = "files"
	contentTypeJSONPart    = "application/json"
)

// Attachment is one stored attachment as the storage endpoints return it (AttachmentResponse).
// URL is a short-lived signed URL of the file.
type Attachment struct {
	ID          string `json:"id"`
	URL         string `json:"url"`
	FileName    string `json:"fileName"`
	ContentType string `json:"contentType"`
	FileSize    int64  `json:"fileSize"`
	EntityType  string `json:"entityType"`
	EntityID    string `json:"entityId"`
	CreatedAt   string `json:"createdAt"`
}

// AttachmentFile is one file to upload as an attachment: its name, its media type, which the API
// stores as it is, and its content.
type AttachmentFile struct {
	FileName    string
	ContentType string
	Data        []byte
}

// attachmentMetadata is one entry of the metadata part (AttachmentCreateRequest).
type attachmentMetadata struct {
	FileName   string `json:"fileName"`
	EntityType string `json:"entityType"`
	EntityID   string `json:"entityId"`
}

// attachmentFromTemplate is the body of a template copy (DocumentTemplateAttachmentRequest).
type attachmentFromTemplate struct {
	EntityType string `json:"entityType"`
	EntityID   string `json:"entityId"`
}

// ListAttachments reads the attachments of one parent, in upload order.
func (c *Client) ListAttachments(entityType, entityID string) ([]Attachment, error) {
	q := url.Values{}
	q.Set("entityType", entityType)
	q.Set("entityId", entityID)
	body, err := c.Get(attachmentsPath + "?" + q.Encode())
	if err != nil {
		return nil, err
	}
	return parseData[[]Attachment](body, "the attachments")
}

// AddAttachments uploads files as attachments of one parent in one request, so that either all
// of them are stored or none.
func (c *Client) AddAttachments(entityType, entityID string, files []AttachmentFile) ([]Attachment, error) {
	metadata := make([]attachmentMetadata, 0, len(files))
	for _, f := range files {
		metadata = append(metadata, attachmentMetadata{FileName: f.FileName, EntityType: entityType, EntityID: entityID})
	}
	metadataJSON, err := json.Marshal(metadata)
	if err != nil {
		return nil, fmt.Errorf("failed to encode the attachment metadata: %w", err)
	}
	parts := []Part{{Name: partAttachmentMetadata, ContentType: contentTypeJSONPart, Data: metadataJSON}}
	for _, f := range files {
		parts = append(parts, Part{Name: partAttachmentFiles, FileName: f.FileName, ContentType: f.ContentType, Data: f.Data})
	}
	body, err := c.PostMultipart(attachmentsPath, parts)
	if err != nil {
		return nil, err
	}
	return parseData[[]Attachment](body, "the added attachments")
}

// CopyTemplateToAttachment stores the PDF of document template templateID as a new attachment of
// one parent. The API reads and stores the PDF itself; later changes to the template leave the
// copy as it is.
func (c *Client) CopyTemplateToAttachment(templateID, entityType, entityID string) (Attachment, error) {
	body, err := c.Post(templatePath(templateID)+"/attachments", attachmentFromTemplate{EntityType: entityType, EntityID: entityID})
	if err != nil {
		return Attachment{}, err
	}
	return parseData[Attachment](body, "the added attachment")
}

// DeleteAttachment deletes one attachment. The API checks access on the parent it stored with the
// attachment, never on a value the client sends.
func (c *Client) DeleteAttachment(attachmentID string) error {
	_, err := c.Delete(attachmentsPath + "/" + url.PathEscape(attachmentID))
	return err
}

const attachmentsPath = "/storage/attachments"
