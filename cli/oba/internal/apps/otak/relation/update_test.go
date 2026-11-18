package relation

import (
	"encoding/json"
	"net/http"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

const (
	relationID   = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
	relationPath = relationsPath + "/" + relationID
)

func TestUpdateStepErrorKeepsExitCode(t *testing.T) {
	err := &UpdateStepError{Step: StepType, Err: &client.APIError{Status: http.StatusNotFound}}
	if got := client.ExitCode(err); got != 2 {
		t.Errorf("ExitCode = %d, want 2", got)
	}
}

func detail(typ, stage string) string {
	stageJSON := "null"
	if stage != "" {
		stageJSON = `"` + stage + `"`
	}
	return `{"data":{"id":"` + relationID + `","name":"Acme","type":"` + typ + `","stage":` + stageJSON + `}}`
}

func updateClient(t *testing.T) (*client.Client, *clitest.MockAPI) {
	t.Helper()
	api := clitest.NewMockAPI(t)
	// Update reads the field list from the API document the command tests serve.
	api.ServeAPIDocs()
	return client.NewWithToken(api.URL(), clitest.OrgIDAcme, "token"), api
}

func TestUpdateClosedStageReturnsAfterFromStageResponse(t *testing.T) {
	c, api := updateClient(t)
	api.Respond("GET "+relationPath, http.StatusOK, detail("LEAD", "WARM"))
	api.Respond("PATCH "+relationPath+"/stage", http.StatusOK, detail("CUSTOMER", "CLOSED"))

	result, err := Update(c, relationID, map[string]json.RawMessage{fieldStage: json.RawMessage(`"CLOSED"`)})
	if err != nil {
		t.Fatal(err)
	}
	if len(result.Applied) != 1 || result.Applied[0] != StepStage {
		t.Errorf("applied = %v", result.Applied)
	}
	if result.Before.Type != "LEAD" || result.Before.Stage == nil || *result.Before.Stage != "WARM" {
		t.Errorf("before = %+v", result.Before)
	}
	if result.After.Type != "CUSTOMER" || result.After.Stage == nil || *result.After.Stage != "CLOSED" {
		t.Errorf("after = %+v", result.After)
	}
}

func TestUpdateNothingSentKeepsRelationAsRead(t *testing.T) {
	c, api := updateClient(t)
	api.Respond("GET "+relationPath, http.StatusOK, detail("CUSTOMER", "DIRECT"))

	result, err := Update(c, relationID, map[string]json.RawMessage{fieldType: json.RawMessage(`"customer"`)})
	if err != nil {
		t.Fatal(err)
	}
	if len(result.Applied) != 0 {
		t.Errorf("applied = %v", result.Applied)
	}
	if result.After.Type != "CUSTOMER" || result.Before.Type != "CUSTOMER" {
		t.Errorf("result = %+v", result)
	}
}
