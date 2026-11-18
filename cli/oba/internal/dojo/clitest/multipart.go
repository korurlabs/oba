package clitest

import (
	"io"
	"mime"
	"mime/multipart"
	"net/http"
	"testing"
)

// FormPart is one part of a multipart request the mock API received. FileName is empty for a
// part sent without a filename, such as a JSON part.
type FormPart struct {
	Name        string
	FileName    string
	ContentType string
	Data        string
}

// ReadMultipart reads the parts of a multipart/form-data request by form name; of a repeated
// name the last part is kept, which ReadMultipartParts returns all of.
func ReadMultipart(t *testing.T, r *http.Request) map[string]FormPart {
	t.Helper()
	parts := map[string]FormPart{}
	for _, p := range ReadMultipartParts(t, r) {
		parts[p.Name] = p
	}
	return parts
}

// ReadMultipartParts reads every part of a multipart/form-data request in the order sent.
func ReadMultipartParts(t *testing.T, r *http.Request) []FormPart {
	t.Helper()
	mediaType, params, err := mime.ParseMediaType(r.Header.Get("Content-Type"))
	if err != nil || mediaType != "multipart/form-data" {
		t.Errorf("content type %q: %v", r.Header.Get("Content-Type"), err)
		return nil
	}
	var parts []FormPart
	reader := multipart.NewReader(r.Body, params["boundary"])
	for {
		p, err := reader.NextPart()
		if err == io.EOF {
			return parts
		}
		if err != nil {
			t.Errorf("reading the multipart body: %v", err)
			return parts
		}
		data, _ := io.ReadAll(p)
		parts = append(parts, FormPart{Name: p.FormName(), FileName: p.FileName(),
			ContentType: p.Header.Get("Content-Type"), Data: string(data)})
	}
}
