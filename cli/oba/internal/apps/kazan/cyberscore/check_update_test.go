package cyberscore

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

func strPtr(s string) *string { return &s }

func storedCheck() client.CyberscoreCheck {
	return client.CyberscoreCheck{
		ID: idMFA, Name: "MFA enforced", Description: strPtr("Why"), Expected: "Every account", Weight: 10,
		SortOrder: 3, Category: strPtr("IDENTITY"), Outcome: "PASS", Status: "DONE",
	}
}

func mergedBody(t *testing.T, in UpdateInput) string {
	t.Helper()
	req, err := merge(storedCheck(), in)
	if err != nil {
		t.Fatal(err)
	}
	body, _ := json.Marshal(req)
	return string(body)
}

func TestMergeKeepsUnsetFieldsAndOutcomeNeverStatus(t *testing.T) {
	got := mergedBody(t, UpdateInput{Weight: intPtr(20)})
	want := `{"name":"MFA enforced","description":"Why","expected":"Every account","weight":20,"sortOrder":3,` +
		`"category":"IDENTITY","outcome":"PASS"}`
	if got != want {
		t.Errorf("body = %s", got)
	}
}

func TestMergeEmptyClearsDescriptionAndCategory(t *testing.T) {
	got := mergedBody(t, UpdateInput{Description: strPtr(""), Category: strPtr("")})
	want := `{"name":"MFA enforced","expected":"Every account","weight":10,"sortOrder":3,"outcome":"PASS"}`
	if got != want {
		t.Errorf("body = %s", got)
	}
}

func TestMergeGivenFields(t *testing.T) {
	got := mergedBody(t, UpdateInput{Name: strPtr("MFA everywhere"), Expected: strPtr("All"), SortOrder: intPtr(0),
		Category: strPtr("office")})
	want := `{"name":"MFA everywhere","description":"Why","expected":"All","weight":10,"sortOrder":0,` +
		`"category":"OFFICE","outcome":"PASS"}`
	if got != want {
		t.Errorf("body = %s", got)
	}
}

func TestCheckUpdateRefusals(t *testing.T) {
	if err := (UpdateInput{}).CheckUpdate(); err != terminal.ErrNothingToUpdate {
		t.Errorf("nothing: err = %v", err)
	}
	for _, tc := range []struct {
		in   UpdateInput
		want string
	}{
		{UpdateInput{Name: strPtr(" ")}, "--name cannot be empty"},
		{UpdateInput{Expected: strPtr("")}, "--expected cannot be empty"},
		{UpdateInput{Weight: intPtr(0)}, "--weight must be between 1 and 100"},
		{UpdateInput{SortOrder: intPtr(10000)}, "--sort-order must be between 0 and 9999"},
		{UpdateInput{Description: strPtr(strings.Repeat("x", 5001))}, "--description must be at most 5000"},
		{UpdateInput{Category: strPtr("nope")}, `invalid category "nope"`},
	} {
		if err := tc.in.CheckUpdate(); err == nil || !strings.Contains(err.Error(), tc.want) {
			t.Errorf("err = %v, want %q", err, tc.want)
		}
	}
}
