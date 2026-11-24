package client

import (
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
)

const kalkanPageItemJSON = `{"id":"p1","slug":"plans","path":"engineering/plans","position":2,"compliance":true,"titles":{"EN":"Plans"},"level":"EDIT"}`

// kalkanServer answers every request with status and body and records it with its query.
func kalkanServer(t *testing.T, status int, body string) (*httptest.Server, *[]recorded) {
	t.Helper()
	var reqs []recorded
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		data, err := io.ReadAll(r.Body)
		if err != nil {
			t.Errorf("read body: %v", err)
		}
		reqs = append(reqs, recorded{r.Method, r.URL.RequestURI(), r.Header.Get("X-Organization-ID"), string(data)})
		w.WriteHeader(status)
		_, _ = w.Write([]byte(body))
	}))
	t.Cleanup(srv.Close)
	return srv, &reqs
}

func TestListKalkanPages(t *testing.T) {
	srv, reqs := kalkanServer(t, http.StatusOK, `{"data":[`+kalkanPageItemJSON+`]}`)
	pages, err := NewWithToken(srv.URL, "org-1", "tok").ListKalkanPages()
	if err != nil {
		t.Fatal(err)
	}
	onlyRequest(t, *reqs, recorded{"GET", "/kalkan/pages", "org-1", ""})
	if len(pages) != 1 || pages[0].ID != "p1" || pages[0].Path != "engineering/plans" || pages[0].Position != 2 ||
		!pages[0].Compliance || pages[0].Titles[KalkanLocaleEN] != "Plans" || pages[0].Level != "EDIT" {
		t.Errorf("pages = %+v", pages)
	}
}

func TestGetKalkanPage(t *testing.T) {
	srv, reqs := kalkanServer(t, http.StatusOK, `{"data":{"id":"p/1","path":"a","version":7,"level":"VIEW",`+
		`"translations":[{"locale":"TR","title":"Başlık","body":"gövde","updatedAt":"2026-11-25T10:00:00Z","updatedByName":"Ada"}]}}`)
	page, err := NewWithToken(srv.URL, "org-1", "tok").GetKalkanPage("p/1")
	if err != nil {
		t.Fatal(err)
	}
	onlyRequest(t, *reqs, recorded{"GET", "/kalkan/pages/p%2F1", "org-1", ""})
	if page.Version != 7 || len(page.Translations) != 1 || page.Translations[0].Locale != KalkanLocaleTR ||
		page.Translations[0].Body != "gövde" || page.Translations[0].UpdatedByName != "Ada" {
		t.Errorf("page = %+v", page)
	}
}

func TestUpsertKalkanPageOmitsNilPositionAndCompliance(t *testing.T) {
	srv, reqs := kalkanServer(t, http.StatusOK, `{"data":{"id":"","path":"a/b","outcome":"CREATED","version":0}}`)
	in := KalkanPageUpsert{Path: "a/b", Translations: []KalkanTranslationInput{{Locale: KalkanLocaleEN, Title: "B", Body: "x"}}}
	result, err := NewWithToken(srv.URL, "org-1", "tok").UpsertKalkanPage(in, true)
	if err != nil {
		t.Fatal(err)
	}
	onlyRequest(t, *reqs, recorded{"PUT", "/kalkan/pages/by-path?dryRun=true", "org-1",
		`{"path":"a/b","translations":[{"locale":"EN","title":"B","body":"x"}],"force":false}`})
	if result.Outcome != "CREATED" || result.Path != "a/b" {
		t.Errorf("result = %+v", result)
	}
}

func TestUpsertKalkanPageSendsPositionComplianceAndForce(t *testing.T) {
	srv, reqs := kalkanServer(t, http.StatusOK, `{"data":{"id":"p1","path":"a","outcome":"UPDATED","version":3}}`)
	position, compliance := 0, false
	in := KalkanPageUpsert{Path: "a", Position: &position, Compliance: &compliance, Translations: []KalkanTranslationInput{}, Force: true}
	result, err := NewWithToken(srv.URL, "org-1", "tok").UpsertKalkanPage(in, false)
	if err != nil {
		t.Fatal(err)
	}
	onlyRequest(t, *reqs, recorded{"PUT", "/kalkan/pages/by-path?dryRun=false", "org-1",
		`{"path":"a","position":0,"compliance":false,"translations":[],"force":true}`})
	if result.Version != 3 || result.ID != "p1" {
		t.Errorf("result = %+v", result)
	}
}

func TestUpsertKalkanPageChanged(t *testing.T) {
	srv, _ := kalkanServer(t, http.StatusConflict, `{"error":{"code":"KLK_STA_004","message":"changed in Kalkan since the last push"}}`)
	_, err := NewWithToken(srv.URL, "org-1", "tok").UpsertKalkanPage(KalkanPageUpsert{Path: "a"}, false)
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.Code != CodeKalkanPageChanged || apiErr.Status != http.StatusConflict || ExitCode(err) != 1 {
		t.Errorf("err = %v, exit %d", err, ExitCode(err))
	}
}

func TestDeleteKalkanPage(t *testing.T) {
	srv, reqs := kalkanServer(t, http.StatusOK, `{"message":"deleted"}`)
	if err := NewWithToken(srv.URL, "org-1", "tok").DeleteKalkanPage("p1"); err != nil {
		t.Fatal(err)
	}
	onlyRequest(t, *reqs, recorded{"DELETE", "/kalkan/pages/p1", "org-1", ""})
}
