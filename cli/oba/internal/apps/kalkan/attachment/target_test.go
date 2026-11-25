package attachment

import (
	"errors"
	"net/http"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestResolveTargetFindsThePage(t *testing.T) {
	api, c := newClient(t)
	api.Respond("GET /kalkan/pages", http.StatusOK, treeJSON)
	target, err := ResolveTarget(c, "engineering/plans")
	if err != nil {
		t.Fatal(err)
	}
	want := Target{EntityType: "KALKAN_PAGE", EntityID: pageID, Label: "page engineering/plans"}
	if target != want {
		t.Errorf("target = %+v, want %+v", target, want)
	}
}

func TestResolveTargetOfAnUnknownPathExits2(t *testing.T) {
	api, c := newClient(t)
	api.Respond("GET /kalkan/pages", http.StatusOK, treeJSON)
	_, err := ResolveTarget(c, "engineering/nope")
	var apiErr *client.APIError
	if !errors.As(err, &apiErr) || apiErr.Code != client.CodeKalkanPageNotFound || client.ExitCode(err) != 2 {
		t.Fatalf("err = %v", err)
	}
}
