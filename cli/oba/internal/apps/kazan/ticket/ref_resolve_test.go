package ticket

import (
	"bytes"
	"errors"
	"net/http"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/board"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

func TestResolveTicketByKeyTakesTheTicketsBoard(t *testing.T) {
	api, c := newAPI(t)
	var warn bytes.Buffer
	got, err := Resolve(c, "ops-12", "", &warn)
	if err != nil {
		t.Fatal(err)
	}
	if got.Found == nil || got.Found.ID != idTicket {
		t.Errorf("Found = %+v; want the ticket the lookup by key read", got.Found)
	}
	got.Found = nil
	if got != (Target{Ref: "ops-12", ID: idTicket, BoardID: idOps, Key: "OPS-12"}) {
		t.Errorf("got %+v", got)
	}
	if strings.Join(api.Calls(), ",") != "GET /kaizen/boards,GET /kaizen/tickets/by-key/OPS-12" {
		t.Errorf("calls = %v", api.Calls())
	}
	if warn.Len() != 0 {
		t.Errorf("warn = %q", warn.String())
	}
}

func TestResolveTicketWarnsWhenBoardNamesAnotherBoard(t *testing.T) {
	_, c := newAPI(t)
	var warn bytes.Buffer
	got, err := Resolve(c, "OPS-12", "KAGI", &warn)
	if err != nil || got.BoardID != idOps {
		t.Fatalf("got %+v, %v", got, err)
	}
	if !strings.Contains(warn.String(), `OPS-12 is on board Operations (OPS), not on --board "KAGI"`) {
		t.Errorf("warn = %q", warn.String())
	}
	warn.Reset()
	if _, err := Resolve(c, "OPS-12", "nope", &warn); err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(warn.String(), `ignoring --board "nope"`) {
		t.Errorf("warn = %q", warn.String())
	}
}

func TestResolveTicketByIDUsesTheSelectedBoard(t *testing.T) {
	api, c := newAPI(t)
	if _, err := Resolve(c, idTicket, "", &bytes.Buffer{}); !errors.Is(err, board.ErrNoBoard) {
		t.Fatalf("err = %v", err)
	}
	if err := config.Save(config.File{KazanBoards: map[string]string{c.OrgID: idKagi}}); err != nil {
		t.Fatal(err)
	}
	got, err := Resolve(c, idTicket, "", &bytes.Buffer{})
	if err != nil || got != (Target{Ref: idTicket, ID: idTicket, BoardID: idKagi}) {
		t.Fatalf("got %+v, %v", got, err)
	}
	got, err = Resolve(c, idTicket, "ops", &bytes.Buffer{})
	if err != nil || got.BoardID != idOps {
		t.Fatalf("got %+v, %v", got, err)
	}
	if strings.Join(api.Calls(), ",") != "GET /kaizen/boards" {
		t.Errorf("calls = %v", api.Calls())
	}
}

func TestResolveTicketNotFoundIsA404(t *testing.T) {
	api, c := newAPI(t)
	api.Respond("GET /kaizen/tickets/by-key/OPS-99", http.StatusNotFound, `{"error":{"code":"KAI_NF","message":"nope"}}`)
	for ref, want := range map[string]string{
		"OPS-99":  `ticket "OPS-99" not found`,
		"NOPE-1":  `no board with prefix "NOPE" is available to you`,
		"OPS":     `invalid ticket "OPS"`,
		"OPS-1a":  `invalid ticket "OPS-1a"`,
		"-12":     `invalid ticket "-12"`,
		"OPS-12-": `invalid ticket "OPS-12-"`,
	} {
		_, err := Resolve(c, ref, "", &bytes.Buffer{})
		if err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("%s: err = %v, want %q", ref, err, want)
			continue
		}
		wantCode := 1
		if !strings.HasPrefix(want, "invalid") {
			wantCode = 2
		}
		if code := client.ExitCode(err); code != wantCode {
			t.Errorf("%s: exit code %d, want %d", ref, code, wantCode)
		}
	}
}
