package client

import (
	"errors"
	"net/http"
	"testing"
)

func TestFindKalkanPage(t *testing.T) {
	srv, reqs := kalkanServer(t, http.StatusOK, `{"data":[{"id":"r","path":"engineering"},`+kalkanPageItemJSON+`]}`)
	page, err := NewWithToken(srv.URL, "org-1", "tok").FindKalkanPage("engineering/plans")
	if err != nil {
		t.Fatal(err)
	}
	onlyRequest(t, *reqs, recorded{"GET", "/kalkan/pages", "org-1", ""})
	if page.ID != "p1" {
		t.Errorf("page = %+v", page)
	}
}

func TestFindKalkanPageMissIsNotFound(t *testing.T) {
	srv, _ := kalkanServer(t, http.StatusOK, `{"data":[`+kalkanPageItemJSON+`]}`)
	_, err := NewWithToken(srv.URL, "org-1", "tok").FindKalkanPage("engineering")
	var apiErr *APIError
	if !errors.As(err, &apiErr) || apiErr.Status != http.StatusNotFound || apiErr.Code != CodeKalkanPageNotFound {
		t.Fatalf("err = %v", err)
	}
	if ExitCode(err) != 2 || err.Error() != "KLK_NOT_001: No page at engineering, or you have no access to it." {
		t.Errorf("err = %q, exit %d", err, ExitCode(err))
	}
}

func TestFindKalkanPageListFails(t *testing.T) {
	srv, _ := kalkanServer(t, http.StatusForbidden, `{"error":{"code":"GRP_SEC_001","message":"no access to Kalkan"}}`)
	_, err := NewWithToken(srv.URL, "org-1", "tok").FindKalkanPage("engineering")
	if ExitCode(err) != 3 {
		t.Errorf("err = %v, exit %d", err, ExitCode(err))
	}
}
