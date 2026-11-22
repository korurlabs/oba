package client

import (
	"io"
	"mime"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestPutMultipartSetsEachPartContentType(t *testing.T) {
	type got struct{ name, file, contentType, data string }
	var parts []got
	var calls int
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls++
		if r.Method != http.MethodPut || r.URL.Path != "/document-templates/d1/files" {
			t.Errorf("%s %s", r.Method, r.URL.Path)
		}
		if calls == 1 {
			// The retry after a 429 must send the same body again.
			w.Header().Set("Retry-After", "0")
			w.WriteHeader(http.StatusTooManyRequests)
			return
		}
		mediaType, params, err := mime.ParseMediaType(r.Header.Get("Content-Type"))
		if err != nil || mediaType != "multipart/form-data" {
			t.Fatalf("content type %q: %v", r.Header.Get("Content-Type"), err)
		}
		reader := multipart.NewReader(r.Body, params["boundary"])
		for {
			p, err := reader.NextPart()
			if err == io.EOF {
				break
			}
			if err != nil {
				t.Fatal(err)
			}
			data, _ := io.ReadAll(p)
			parts = append(parts, got{p.FormName(), p.FileName(), p.Header.Get("Content-Type"), string(data)})
		}
		_, _ = w.Write([]byte(`{"data":{"id":"d1","name":"NDA","ready":true}}`))
	}))
	defer srv.Close()

	c := NewWithToken(srv.URL, "org", "tok")
	doc, err := c.ReplaceDocumentTemplateFiles("d1", DocumentFiles{BaseName: `NDA "v2"`, HTML: []byte("<p>x</p>"), PDF: []byte("%PDF-1.7")})
	if err != nil {
		t.Fatal(err)
	}
	if doc.ID != "d1" || !doc.Ready || calls != 2 {
		t.Errorf("doc %+v calls %d", doc, calls)
	}
	want := []got{
		{"html", `NDA "v2".html`, "text/html; charset=utf-8", "<p>x</p>"},
		{"pdf", `NDA "v2".pdf`, "application/pdf", "%PDF-1.7"},
	}
	if len(parts) != len(want) {
		t.Fatalf("parts = %+v", parts)
	}
	for i := range want {
		if parts[i] != want[i] {
			t.Errorf("part %d = %+v, want %+v", i, parts[i], want[i])
		}
	}
}

func TestDownloadSendsNoCredentialsAndHidesSignature(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Authorization") != "" || r.Header.Get("X-Organization-ID") != "" {
			t.Errorf("credentials sent: %v", r.Header)
		}
		if r.URL.Path == "/gone.pdf" {
			w.WriteHeader(http.StatusForbidden)
			return
		}
		_, _ = w.Write([]byte("%PDF-1.7"))
	}))
	defer srv.Close()

	data, err := Download(srv.URL + "/a.pdf?sig=secret")
	if err != nil || string(data) != "%PDF-1.7" {
		t.Fatalf("data %q err %v", data, err)
	}
	_, err = Download(srv.URL + "/gone.pdf?sig=secret")
	if err == nil || strings.Contains(err.Error(), "secret") || !strings.Contains(err.Error(), "403") {
		t.Fatalf("err = %v", err)
	}
}
