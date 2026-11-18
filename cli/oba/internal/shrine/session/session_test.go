package session

import (
	"errors"
	"net/http"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/shrine/auth"
)

func TestUserClientSendsNoOrganization(t *testing.T) {
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("POST /organizations", http.StatusCreated, `{"data":{"id":"org-9","name":"Korur B.V."}}`)
	clitest.LoginTo(t, api.URL(), clitest.OrgIDAcme)

	c, err := UserClient()
	if err != nil {
		t.Fatal(err)
	}
	if _, err := c.CreateOrganization(map[string]string{"name": "Korur B.V."}); err != nil {
		t.Fatal(err)
	}
	req := api.Request("POST /organizations")
	if req.Org != "" || req.Auth != "Bearer access-token" {
		t.Errorf("org header %q, auth %q", req.Org, req.Auth)
	}
}

func TestUserClientNeedsSession(t *testing.T) {
	clitest.SetupHome(t)
	if _, err := UserClient(); !errors.Is(err, auth.ErrNotLoggedIn) {
		t.Errorf("err = %v, want ErrNotLoggedIn", err)
	}
}
