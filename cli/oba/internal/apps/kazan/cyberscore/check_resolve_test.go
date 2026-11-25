package cyberscore

import (
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

const (
	idMFA     = "c0000000-0000-4000-8000-000000000001"
	idMFALow  = "c0000000-0000-4000-8000-000000000002"
	idBackup  = "c0000000-0000-4000-8000-000000000003"
	idUnknown = "c0000000-0000-4000-8000-000000000009"
)

// sampleChecks has two names that differ only in case, which the API's unique name allows.
func sampleChecks() []client.CyberscoreCheck {
	return []client.CyberscoreCheck{
		{ID: idMFA, Name: "MFA enforced"},
		{ID: idMFALow, Name: "mfa enforced"},
		{ID: idBackup, Name: "Backups tested"},
	}
}

func TestMatchCheckByID(t *testing.T) {
	ch, err := matchCheck(sampleChecks(), strings.ToUpper(idMFALow))
	if err != nil || ch.ID != idMFALow {
		t.Errorf("got %+v, %v", ch, err)
	}
}

func TestMatchCheckUnknownIDExitsTwo(t *testing.T) {
	_, err := matchCheck(sampleChecks(), idUnknown)
	if client.ExitCode(err) != 2 || !strings.Contains(err.Error(), idUnknown) {
		t.Errorf("err %v exit %d", err, client.ExitCode(err))
	}
}

func TestMatchCheckExactNameWins(t *testing.T) {
	ch, err := matchCheck(sampleChecks(), "mfa enforced")
	if err != nil || ch.ID != idMFALow {
		t.Errorf("got %+v, %v", ch, err)
	}
}

func TestMatchCheckNameCaseIgnored(t *testing.T) {
	ch, err := matchCheck(sampleChecks(), " backups TESTED ")
	if err != nil || ch.ID != idBackup {
		t.Errorf("got %+v, %v", ch, err)
	}
}

func TestMatchCheckAmbiguousListsIDs(t *testing.T) {
	_, err := matchCheck(sampleChecks(), "MFA ENFORCED")
	if err == nil || !strings.Contains(err.Error(), `2 checks are named "MFA ENFORCED"; pass the check id instead:`) ||
		!strings.Contains(err.Error(), idMFA) || !strings.Contains(err.Error(), idMFALow) {
		t.Errorf("err = %v", err)
	}
}

func TestMatchCheckNotFoundListsNames(t *testing.T) {
	_, err := matchCheck(sampleChecks(), "Disk encryption")
	if err == nil || err.Error() != `check "Disk encryption" not found. Available checks: MFA enforced, mfa enforced, Backups tested` {
		t.Errorf("err = %v", err)
	}
	if client.ExitCode(err) != 1 {
		t.Errorf("exit %d", client.ExitCode(err))
	}
	_, err = matchCheck(nil, "Disk encryption")
	if err == nil || !strings.Contains(err.Error(), "the organization has no checks") {
		t.Errorf("err = %v", err)
	}
}

func TestMatchCheckEmptyArgument(t *testing.T) {
	if _, err := matchCheck(sampleChecks(), "  "); err != errNoCheck {
		t.Errorf("err = %v", err)
	}
}
