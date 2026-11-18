package client

import (
	"bytes"
	"fmt"
	"mime/multipart"
	"net/http"
	"net/textproto"
	"strings"
)

// Part is one part of a multipart/form-data body.
type Part struct {
	// Name is the form field name the API reads, such as "html", "pdf" or "files".
	Name string
	// FileName is sent as the part's filename. An empty FileName sends none, as a JSON part
	// such as the "metadata" of an attachment upload needs.
	FileName string
	// ContentType is the part's own Content-Type. The API checks it per part, so it is always
	// set explicitly: Go's CreateFormFile would send application/octet-stream, which the API
	// refuses for an HTML or PDF part and for a JSON part.
	ContentType string
	Data        []byte
}

// PostMultipart sends a POST with the parts as one multipart/form-data body and returns the
// response body.
func (c *Client) PostMultipart(path string, parts []Part) ([]byte, error) {
	body, contentType, err := EncodeMultipart(parts)
	if err != nil {
		return nil, err
	}
	return c.send(http.MethodPost, path, body, contentType, true)
}

// PutMultipart sends a PUT with the parts as one multipart/form-data body and returns the
// response body.
func (c *Client) PutMultipart(path string, parts []Part) ([]byte, error) {
	body, contentType, err := EncodeMultipart(parts)
	if err != nil {
		return nil, err
	}
	return c.send(http.MethodPut, path, body, contentType, true)
}

// EncodeMultipart writes the parts as a multipart/form-data body and returns it with the
// request's Content-Type, which carries the boundary.
func EncodeMultipart(parts []Part) ([]byte, string, error) {
	var buf bytes.Buffer
	w := multipart.NewWriter(&buf)
	for _, p := range parts {
		header := make(textproto.MIMEHeader)
		disposition := fmt.Sprintf(`form-data; name="%s"`, escapeQuotes(p.Name))
		if p.FileName != "" {
			disposition += fmt.Sprintf(`; filename="%s"`, escapeQuotes(p.FileName))
		}
		header.Set("Content-Disposition", disposition)
		header.Set("Content-Type", p.ContentType)
		pw, err := w.CreatePart(header)
		if err != nil {
			return nil, "", fmt.Errorf("failed to build the %s part: %w", p.Name, err)
		}
		if _, err := pw.Write(p.Data); err != nil {
			return nil, "", fmt.Errorf("failed to build the %s part: %w", p.Name, err)
		}
	}
	if err := w.Close(); err != nil {
		return nil, "", fmt.Errorf("failed to finish the multipart body: %w", err)
	}
	return buf.Bytes(), w.FormDataContentType(), nil
}

var quoteEscaper = strings.NewReplacer("\\", "\\\\", `"`, "\\\"")

// escapeQuotes escapes a value for a quoted Content-Disposition parameter, as mime/multipart
// does for CreateFormFile.
func escapeQuotes(s string) string {
	return quoteEscaper.Replace(s)
}
