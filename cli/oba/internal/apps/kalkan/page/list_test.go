package page

import (
	"errors"
	"net/http"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestListWithoutPathKeepsEveryPage(t *testing.T) {
	api, c := newClient(t)
	api.Respond("GET /kalkan/pages", http.StatusOK, treeJSON)
	pages, err := List(c, "")
	if err != nil {
		t.Fatal(err)
	}
	if len(pages) != 6 {
		t.Errorf("pages = %+v", pages)
	}
}

// A sibling whose slug starts with the path, such as engineering-x, is not below engineering.
func TestListWithPathKeepsTheSubtree(t *testing.T) {
	api, c := newClient(t)
	api.Respond("GET /kalkan/pages", http.StatusOK, treeJSON)
	pages, err := List(c, "engineering/plans")
	if err != nil {
		t.Fatal(err)
	}
	if len(pages) != 2 || pages[0].Path != "engineering/plans" || pages[1].Path != "engineering/plans/q4" {
		t.Errorf("pages = %+v", pages)
	}
	pages, err = List(c, "engineering")
	if err != nil {
		t.Fatal(err)
	}
	for _, page := range pages {
		if page.Path == "engineering-x" || page.Path == "isms" {
			t.Errorf("kept %s", page.Path)
		}
	}
	if len(pages) != 4 {
		t.Errorf("pages = %+v", pages)
	}
}

func TestListUnknownPathIsNotFound(t *testing.T) {
	api, c := newClient(t)
	api.Respond("GET /kalkan/pages", http.StatusOK, treeJSON)
	_, err := List(c, "engineering/nope")
	var apiErr *client.APIError
	if !errors.As(err, &apiErr) || apiErr.Code != client.CodeKalkanPageNotFound || client.ExitCode(err) != 2 {
		t.Fatalf("err = %v, exit %d", err, client.ExitCode(err))
	}
	if err.Error() != "KLK_NOT_001: No page at engineering/nope, or you have no access to it." {
		t.Errorf("err = %q", err)
	}
}

func TestListDenied(t *testing.T) {
	api, c := newClient(t)
	api.Respond("GET /kalkan/pages", http.StatusForbidden, `{"error":{"code":"GRP_SEC_001","message":"no access to Kalkan"}}`)
	if _, err := List(c, ""); client.ExitCode(err) != 3 {
		t.Errorf("err = %v, exit %d", err, client.ExitCode(err))
	}
}
