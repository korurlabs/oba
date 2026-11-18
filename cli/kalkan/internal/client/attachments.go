package client

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"mime"
	"mime/multipart"
	"net/http"
	"net/textproto"
	"net/url"
	"path/filepath"
	"strings"
)

// StorageEntityTypeKalkanPage is the attachment parent type of a Kalkan page.
const StorageEntityTypeKalkanPage = "KALKAN_PAGE"

// CodeDuplicateContent is an upload whose bytes are already attached to some Kalkan page: the
// server keys its duplicate check on the entity type and the content hash, not on the page.
const CodeDuplicateContent = "STG_VAL_005"

const attachmentsPath = "/storage/attachments"

// Attachment is a stored file of a page. URL is signed for reading and its query string changes
// on every answer.
type Attachment struct {
	ID          string `json:"id"`
	URL         string `json:"url"`
	FileName    string `json:"fileName"`
	ContentType string `json:"contentType"`
	FileSize    int64  `json:"fileSize"`
}

type attachmentMetadata struct {
	FileName   string `json:"fileName"`
	EntityType string `json:"entityType"`
	EntityID   string `json:"entityId"`
}

// ListAttachments returns the attachments of one entity.
func (c *Client) ListAttachments(ctx context.Context, entityType, entityID string) ([]Attachment, error) {
	query := url.Values{}
	query.Set("entityType", entityType)
	query.Set("entityId", entityID)
	var out []Attachment
	err := c.doJSON(ctx, http.MethodGet, attachmentsPath+"?"+query.Encode(), nil, &out)
	return out, err
}

// UploadAttachment uploads content as fileName to one entity, in the multipart shape of
// AttachmentController: a "metadata" part holding a JSON array, then one "files" part whose file
// name must equal the metadata's.
func (c *Client) UploadAttachment(ctx context.Context, entityType, entityID, fileName string, content []byte) (Attachment, error) {
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)

	metadata, err := json.Marshal([]attachmentMetadata{{FileName: fileName, EntityType: entityType, EntityID: entityID}})
	if err != nil {
		return Attachment{}, fmt.Errorf("encode the metadata of %s: %w", fileName, err)
	}
	// Spring reads the metadata part through its message converters, so it must say JSON.
	metaHeader := textproto.MIMEHeader{}
	metaHeader.Set("Content-Disposition", `form-data; name="metadata"`)
	metaHeader.Set("Content-Type", "application/json")
	metaPart, err := writer.CreatePart(metaHeader)
	if err != nil {
		return Attachment{}, fmt.Errorf("build the upload of %s: %w", fileName, err)
	}
	if _, err := metaPart.Write(metadata); err != nil {
		return Attachment{}, fmt.Errorf("build the upload of %s: %w", fileName, err)
	}

	// The server stores the part's content type on the attachment, so it is set from the file
	// rather than left as CreateFormFile's application/octet-stream.
	fileHeader := textproto.MIMEHeader{}
	fileHeader.Set("Content-Disposition", fmt.Sprintf(`form-data; name="files"; filename="%s"`,
		strings.NewReplacer("\\", "\\\\", `"`, "\\\"").Replace(fileName)))
	fileHeader.Set("Content-Type", contentTypeOf(fileName, content))
	filePart, err := writer.CreatePart(fileHeader)
	if err != nil {
		return Attachment{}, fmt.Errorf("build the upload of %s: %w", fileName, err)
	}
	if _, err := filePart.Write(content); err != nil {
		return Attachment{}, fmt.Errorf("build the upload of %s: %w", fileName, err)
	}
	if err := writer.Close(); err != nil {
		return Attachment{}, fmt.Errorf("build the upload of %s: %w", fileName, err)
	}

	var out []Attachment
	if err := c.do(ctx, http.MethodPost, attachmentsPath, writer.FormDataContentType(), buf.Bytes(), &out); err != nil {
		return Attachment{}, err
	}
	if len(out) != 1 {
		return Attachment{}, fmt.Errorf("the upload of %s answered %d attachments, not 1", fileName, len(out))
	}
	return out[0], nil
}

// contentTypeOf names the type by extension first, which tells text/csv from text/plain, and
// sniffs the content otherwise.
func contentTypeOf(fileName string, content []byte) string {
	if byExtension := mime.TypeByExtension(filepath.Ext(fileName)); byExtension != "" {
		return byExtension
	}
	return http.DetectContentType(content)
}
