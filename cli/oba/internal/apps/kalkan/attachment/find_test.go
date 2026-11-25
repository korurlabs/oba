package attachment

import (
	"net/http"
	"strings"
	"testing"
)

func pageTarget() Target {
	return Target{EntityType: "KALKAN_PAGE", EntityID: pageID, Label: "page engineering/plans"}
}

func TestFindMatchesTheIdInAnyCase(t *testing.T) {
	api, c := newClient(t)
	api.Respond("GET "+attachmentsPath, http.StatusOK, `{"data":[{"id":"`+attachmentID+`","fileName":"policy.pdf"}]}`)
	found, err := Find(c, pageTarget(), strings.ToUpper(attachmentID))
	if err != nil || found.ID != attachmentID {
		t.Fatalf("found %+v, err %v", found, err)
	}
}

func TestFindRefusesAnIdOfAnotherPage(t *testing.T) {
	api, c := newClient(t)
	api.Respond("GET "+attachmentsPath, http.StatusOK, `{"data":[]}`)
	_, err := Find(c, pageTarget(), attachmentID)
	want := "attachment " + attachmentID + " is not an attachment of page engineering/plans; 'oba kalkan attachment list' lists the ids"
	if err == nil || err.Error() != want {
		t.Fatalf("err = %v", err)
	}
}
