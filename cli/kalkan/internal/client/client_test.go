package client

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"mime"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func staticToken(token string) TokenFunc {
	return func(context.Context) (string, error) { return token, nil }
}

func newTestClient(t *testing.T, handler http.HandlerFunc) *Client {
	t.Helper()
	srv := httptest.NewServer(handler)
	t.Cleanup(srv.Close)
	return New(srv.URL+"/", "org-1", staticToken("tok"))
}

func writeEnvelope(w http.ResponseWriter, status int, body string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_, _ = w.Write([]byte(body))
}

func TestCallSendsBearerAndOrganization(t *testing.T) {
	var gotAuth, gotOrg, gotPath string
	api := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		gotAuth, gotOrg, gotPath = r.Header.Get("Authorization"), r.Header.Get(HeaderOrganizationID), r.URL.Path
		writeEnvelope(w, 200, `{"success":true,"data":[{"id":"p1","path":"a/b","titles":{"EN":"B"}}]}`)
	})
	pages, err := api.ListPages(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if gotAuth != "Bearer tok" || gotOrg != "org-1" || gotPath != "/kalkan/pages" {
		t.Fatalf("auth %q org %q path %q", gotAuth, gotOrg, gotPath)
	}
	if len(pages) != 1 || pages[0].Path != "a/b" || pages[0].Titles["EN"] != "B" {
		t.Fatalf("pages = %+v", pages)
	}
}

func TestCallWithoutOrganizationOmitsHeader(t *testing.T) {
	var present bool
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		present = r.Header.Get(HeaderOrganizationID) != ""
		writeEnvelope(w, 200, `{"success":true,"data":null}`)
	}))
	defer srv.Close()
	api := New(srv.URL, "", staticToken("t"))
	if _, err := api.ListPages(context.Background()); err != nil {
		t.Fatal(err)
	}
	if present {
		t.Fatal("the organization header must not be sent when there is no organization")
	}
	if got := api.WithOrg("o2").OrgID(); got != "o2" || api.OrgID() != "" {
		t.Fatalf("WithOrg changed the original or not the copy: %q %q", got, api.OrgID())
	}
}

func TestTokenErrorStopsTheCall(t *testing.T) {
	called := false
	srv := httptest.NewServer(http.HandlerFunc(func(http.ResponseWriter, *http.Request) { called = true }))
	defer srv.Close()
	boom := errors.New("no token")
	api := New(srv.URL, "o", func(context.Context) (string, error) { return "", boom })
	if _, err := api.ListPages(context.Background()); !errors.Is(err, boom) {
		t.Fatalf("err = %v, want the token error", err)
	}
	if called {
		t.Fatal("no request may leave without a token")
	}
}

func TestErrorMapping(t *testing.T) {
	cases := map[string]struct {
		status int
		body   string
		want   APIError
	}{
		"coded refusal": {409, `{"success":false,"error":{"code":"KLK_STA_004","message":"changed","errorValues":{"updatedByName":"Ada"}}}`,
			APIError{Status: 409, Code: "KLK_STA_004", Message: "changed"}},
		"message only":   {403, `{"success":false,"message":"nope"}`, APIError{Status: 403, Message: "nope"}},
		"plain text":     {502, `bad gateway`, APIError{Status: 502, Message: "bad gateway"}},
		"empty body":     {404, ``, APIError{Status: 404, Message: "Not Found"}},
		"json no fields": {500, `{}`, APIError{Status: 500, Message: "Internal Server Error"}},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			api := newTestClient(t, func(w http.ResponseWriter, _ *http.Request) { writeEnvelope(w, tc.status, tc.body) })
			_, err := api.GetPage(context.Background(), "x")
			var apiErr *APIError
			if !errors.As(err, &apiErr) {
				t.Fatalf("err = %v, want *APIError", err)
			}
			if apiErr.Status != tc.want.Status || apiErr.Code != tc.want.Code || apiErr.Message != tc.want.Message {
				t.Fatalf("APIError = %+v, want %+v", *apiErr, tc.want)
			}
			if tc.want.Code != "" && !IsCode(err, tc.want.Code) {
				t.Fatal("IsCode must match")
			}
		})
	}
}

func TestErrorValuesAreKept(t *testing.T) {
	api := newTestClient(t, func(w http.ResponseWriter, _ *http.Request) {
		writeEnvelope(w, 409, `{"error":{"code":"KLK_STA_004","message":"m","errorValues":{"updatedByName":"Ada","updatedAt":"t"}}}`)
	})
	_, err := api.GetPage(context.Background(), "x")
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.ErrorValues["updatedByName"] != "Ada" {
		t.Fatalf("err = %v", err)
	}
	if !strings.Contains(apiErr.Error(), "409 KLK_STA_004") {
		t.Fatalf("Error() = %q", apiErr.Error())
	}
	if got := (&APIError{Status: 500, Message: "x"}).Error(); strings.Contains(got, "  ") || !strings.Contains(got, "500") {
		t.Fatalf("Error() without code = %q", got)
	}
	if IsCode(errors.New("x"), "KLK_STA_004") {
		t.Fatal("IsCode on a plain error must be false")
	}
}

func TestSuccessWithGarbageIsParseError(t *testing.T) {
	api := newTestClient(t, func(w http.ResponseWriter, _ *http.Request) { writeEnvelope(w, 200, `<html>`) })
	_, err := api.ListPages(context.Background())
	var apiErr *APIError
	if err == nil || errors.As(err, &apiErr) {
		t.Fatalf("err = %v, want a parse error that is not an APIError", err)
	}
}

func TestNetworkErrorIsPlain(t *testing.T) {
	srv := httptest.NewServer(http.NotFoundHandler())
	url := srv.URL
	srv.Close()
	api := New(url, "o", staticToken("t"))
	_, err := api.ListPages(context.Background())
	var apiErr *APIError
	if err == nil || errors.As(err, &apiErr) {
		t.Fatalf("err = %v, want a transport error", err)
	}
}

func TestUpsertPageSendsBodyAndDryRun(t *testing.T) {
	var query string
	var got UpsertInput
	api := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPut || r.URL.Path != "/kalkan/pages/by-path" {
			t.Errorf("%s %s", r.Method, r.URL.Path)
		}
		query = r.URL.RawQuery
		if r.Header.Get("Content-Type") != "application/json" {
			t.Errorf("content type %q", r.Header.Get("Content-Type"))
		}
		_ = json.NewDecoder(r.Body).Decode(&got)
		writeEnvelope(w, 200, `{"data":{"id":"i","path":"a","outcome":"CREATED","version":3}}`)
	})
	position := 2
	in := UpsertInput{Path: "a", Position: &position, Translations: []TranslationInput{{Locale: LocaleEN, Title: "T", Body: "B"}}, Force: true}
	result, err := api.UpsertPage(context.Background(), in, true)
	if err != nil || result.Outcome != OutcomeCreated || result.Version != 3 {
		t.Fatalf("result = %+v, %v", result, err)
	}
	if query != "dryRun=true" || !got.Force || got.Position == nil || *got.Position != 2 || got.Translations[0].Body != "B" {
		t.Fatalf("query %q, body %+v", query, got)
	}
	if _, err := api.UpsertPage(context.Background(), UpsertInput{Path: "a"}, false); err != nil || query != "dryRun=false" {
		t.Fatalf("query %q err %v", query, err)
	}
}

func TestGetPageEscapesID(t *testing.T) {
	var rawPath string
	api := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		rawPath = r.URL.EscapedPath()
		writeEnvelope(w, 200, `{"data":{"id":"x","version":7}}`)
	})
	page, err := api.GetPage(context.Background(), "a/b")
	if err != nil || page.Version != 7 || rawPath != "/kalkan/pages/a%2Fb" {
		t.Fatalf("page %+v err %v path %s", page, err, rawPath)
	}
}

func TestListAttachmentsQuery(t *testing.T) {
	var query string
	api := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		query = r.URL.RawQuery
		writeEnvelope(w, 200, `{"data":[{"id":"1","url":"https://s/f.png?sig=1","fileName":"abc-f.png"}]}`)
	})
	list, err := api.ListAttachments(context.Background(), StorageEntityTypeKalkanPage, "page-1")
	if err != nil || len(list) != 1 || list[0].FileName != "abc-f.png" {
		t.Fatalf("list %+v err %v", list, err)
	}
	if query != "entityId=page-1&entityType=KALKAN_PAGE" {
		t.Fatalf("query = %s", query)
	}
}

func TestUploadAttachmentMultipartShape(t *testing.T) {
	var metadata []map[string]string
	var metaType, fileName, fileType string
	var content []byte
	api := newTestClient(t, func(w http.ResponseWriter, r *http.Request) {
		_, params, err := mime.ParseMediaType(r.Header.Get("Content-Type"))
		if err != nil {
			t.Error(err)
			return
		}
		reader := multipart.NewReader(r.Body, params["boundary"])
		for {
			part, err := reader.NextPart()
			if err == io.EOF {
				break
			}
			if err != nil {
				t.Error(err)
				return
			}
			data, _ := io.ReadAll(part)
			switch part.FormName() {
			case "metadata":
				metaType = part.Header.Get("Content-Type")
				_ = json.Unmarshal(data, &metadata)
			case "files":
				fileName, fileType, content = part.FileName(), part.Header.Get("Content-Type"), data
			}
		}
		writeEnvelope(w, 200, `{"data":[{"id":"a1","url":"https://s/x","fileName":"abc-logo.png"}]}`)
	})
	attachment, err := api.UploadAttachment(context.Background(), StorageEntityTypeKalkanPage, "page-1", "abc-logo.png", []byte("\x89PNG\r\n\x1a\nxx"))
	if err != nil || attachment.ID != "a1" {
		t.Fatalf("attachment %+v err %v", attachment, err)
	}
	if metaType != "application/json" || len(metadata) != 1 || metadata[0]["fileName"] != "abc-logo.png" ||
		metadata[0]["entityType"] != "KALKAN_PAGE" || metadata[0]["entityId"] != "page-1" {
		t.Fatalf("metadata type %q %+v", metaType, metadata)
	}
	if fileName != "abc-logo.png" || fileType != "image/png" || string(content) != "\x89PNG\r\n\x1a\nxx" {
		t.Fatalf("file %q %q %q", fileName, fileType, content)
	}
}

func TestUploadAttachmentErrors(t *testing.T) {
	api := newTestClient(t, func(w http.ResponseWriter, _ *http.Request) {
		writeEnvelope(w, 422, `{"error":{"code":"STG_VAL_005","message":"duplicate"}}`)
	})
	if _, err := api.UploadAttachment(context.Background(), "KALKAN_PAGE", "p", "f.txt", []byte("x")); !IsCode(err, CodeDuplicateContent) {
		t.Fatalf("err = %v, want STG_VAL_005", err)
	}
	api = newTestClient(t, func(w http.ResponseWriter, _ *http.Request) { writeEnvelope(w, 200, `{"data":[]}`) })
	if _, err := api.UploadAttachment(context.Background(), "KALKAN_PAGE", "p", "f.txt", []byte("x")); err == nil {
		t.Fatal("an answer without exactly one attachment must fail")
	}
}

func TestContentTypeOf(t *testing.T) {
	if got := contentTypeOf("a.csv", []byte("x")); !strings.HasPrefix(got, "text/csv") {
		t.Fatalf("csv = %s", got)
	}
	if got := contentTypeOf("noext", []byte("<html></html>")); !strings.HasPrefix(got, "text/html") {
		t.Fatalf("sniffed = %s", got)
	}
}
