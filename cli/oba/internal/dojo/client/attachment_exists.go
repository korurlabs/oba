package client

import (
	"encoding/json"
	"fmt"
)

// The duplicate check (POST /storage/attachments/exists) takes another shape than the upload: one
// "metadata" part holding a single JSON object without an entity id, and one "file" part.
const (
	attachmentsExistsPath = attachmentsPath + "/exists"
	partAttachmentFile    = "file"
)

// attachmentExistsMetadata is the metadata part of the duplicate check
// (AttachmentExistsCheckRequest).
type attachmentExistsMetadata struct {
	FileName   string `json:"fileName"`
	EntityType string `json:"entityType"`
}

type attachmentExistsResult struct {
	Exists bool `json:"exists"`
}

// AttachmentExists asks whether content identical to file is already stored as an attachment of
// any parent of entityType, without storing anything. The API refuses such content on upload for
// every parent of the type, not only the one the file is meant for.
func (c *Client) AttachmentExists(entityType string, file AttachmentFile) (bool, error) {
	metadataJSON, err := json.Marshal(attachmentExistsMetadata{FileName: file.FileName, EntityType: entityType})
	if err != nil {
		return false, fmt.Errorf("failed to encode the duplicate check metadata: %w", err)
	}
	parts := []Part{
		{Name: partAttachmentMetadata, ContentType: contentTypeJSONPart, Data: metadataJSON},
		{Name: partAttachmentFile, FileName: file.FileName, ContentType: file.ContentType, Data: file.Data},
	}
	body, err := c.PostMultipart(attachmentsExistsPath, parts)
	if err != nil {
		return false, err
	}
	result, err := parseData[attachmentExistsResult](body, "the duplicate check")
	if err != nil {
		return false, err
	}
	return result.Exists, nil
}
