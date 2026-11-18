package sprint

import (
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestUnlinkOptionsCheck(t *testing.T) {
	tests := []struct {
		name    string
		opts    UnlinkOptions
		wantErr string
	}{
		{name: "defaults to the backlog"},
		{name: "sprint", opts: UnlinkOptions{TicketsTo: "sprint", TargetSprint: "Sprint 2"}},
		{name: "delete the tickets", opts: UnlinkOptions{DeleteTickets: true}},
		{name: "delete with a destination", opts: UnlinkOptions{DeleteTickets: true, TicketsTo: "backlog"}, wantErr: "cannot be combined"},
		{name: "delete with a target", opts: UnlinkOptions{DeleteTickets: true, TargetSprint: "S"}, wantErr: "cannot be combined"},
		{name: "sprint without a target", opts: UnlinkOptions{TicketsTo: "sprint"}, wantErr: "--tickets-to sprint needs --target-sprint"},
		{name: "target with the backlog", opts: UnlinkOptions{TargetSprint: "S"}, wantErr: "--target-sprint is only valid with --tickets-to sprint"},
		{name: "delete is not a destination", opts: UnlinkOptions{TicketsTo: "delete"}, wantErr: `invalid --tickets-to "delete"`},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.opts.check()
			if tt.wantErr == "" {
				if err != nil {
					t.Fatalf("err = %v", err)
				}
				return
			}
			if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
				t.Fatalf("err = %v, want %q", err, tt.wantErr)
			}
		})
	}
}

func TestLinkedElsewhere(t *testing.T) {
	only := client.Sprint{Boards: []client.SprintBoard{{ID: boardID}}}
	both := client.Sprint{Boards: []client.SprintBoard{{ID: boardID}, {ID: "other"}}}
	if linkedElsewhere(only, strings.ToUpper(boardID)) {
		t.Error("a sprint on one board was reported as linked elsewhere")
	}
	if !linkedElsewhere(both, boardID) {
		t.Error("a sprint on two boards was not reported as linked elsewhere")
	}
	if linkedElsewhere(client.Sprint{}, boardID) {
		t.Error("a sprint with no boards was reported as linked elsewhere")
	}
}
