package updates

import (
	"strings"
	"testing"
)

const testTicketID = "0b000000-0000-4000-8000-000000000001"

func TestApplyLineFlagsSetsByKeyOrID(t *testing.T) {
	lines := []Line{{TicketID: testTicketID, Key: "OPS-7", Percentage: 10}, {TicketID: "t2", Key: "KAGI-2", Percentage: 20}}
	f, err := ParseLineFlags([]string{"ops-7=40"}, []string{testTicketID + "=x", "KAGI-2="})
	if err != nil {
		t.Fatal(err)
	}
	if err := ApplyLineFlags(lines, f); err != nil {
		t.Fatal(err)
	}
	if lines[0].Percentage != 40 || !lines[0].PercentSet || lines[0].Note == nil || *lines[0].Note != "x" || !lines[0].NoteSet {
		t.Errorf("line 0 = %+v", lines[0])
	}
	if lines[1].Percentage != 20 || lines[1].PercentSet || lines[1].Note != nil || !lines[1].NoteSet {
		t.Errorf("line 1 = %+v", lines[1])
	}
}

func TestApplyLineFlagsRejectsATicketNotOnTheUpdate(t *testing.T) {
	f, _ := ParseLineFlags([]string{"ACME-1=4"}, nil)
	err := ApplyLineFlags([]Line{{Key: "OPS-7"}, {Key: "KAGI-2"}}, f)
	if err == nil || !strings.Contains(err.Error(), "--percent ACME-1: the ticket is not on the update (tickets: OPS-7, KAGI-2)") {
		t.Fatalf("err = %v", err)
	}
}

func TestCheckLineFlagRefs(t *testing.T) {
	f, _ := ParseLineFlags([]string{"kagi-2=4"}, []string{"OPS-7=n"})
	if err := CheckLineFlagRefs([]string{"OPS-7", "KAGI-2"}, f); err != nil {
		t.Errorf("all named: %v", err)
	}
	if err := CheckLineFlagRefs([]string{"OPS-7"}, f); err == nil || !strings.Contains(err.Error(), "kagi-2 is not among --tickets OPS-7") {
		t.Errorf("missing one: %v", err)
	}
	// An id entry's key is known only after the read, so the check waits for ApplyLineFlags.
	if err := CheckLineFlagRefs([]string{"OPS-7", testTicketID}, f); err != nil {
		t.Errorf("with an id: %v", err)
	}
}
