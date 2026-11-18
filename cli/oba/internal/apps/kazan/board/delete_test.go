package board

import (
	"strings"
	"testing"
)

func TestCheckDelete(t *testing.T) {
	tests := []struct {
		name                  string
		opts                  DeleteOptions
		wantTicket, wantChild string
		wantErr               string
	}{
		{name: "nothing", opts: DeleteOptions{}},
		{name: "backlog", opts: DeleteOptions{TicketsTo: "Backlog", TargetBoard: "KAGI"}, wantTicket: ticketsToBacklog},
		{name: "sprint", opts: DeleteOptions{TicketsTo: "sprint", TargetBoard: "KAGI", TargetSprint: "S1"}, wantTicket: ticketsToSprint},
		{name: "delete with yes", opts: DeleteOptions{TicketsTo: "delete", Yes: true}, wantTicket: ticketsDelete},
		{name: "delete without yes", opts: DeleteOptions{TicketsTo: "delete"}, wantErr: "pass --yes"},
		{name: "target without action", opts: DeleteOptions{TargetBoard: "KAGI"}, wantErr: "need --tickets-to"},
		{name: "backlog without target", opts: DeleteOptions{TicketsTo: "backlog"}, wantErr: "needs --target-board"},
		{name: "sprint without sprint", opts: DeleteOptions{TicketsTo: "sprint", TargetBoard: "KAGI"}, wantErr: "needs --target-sprint"},
		{name: "backlog with sprint", opts: DeleteOptions{TicketsTo: "backlog", TargetBoard: "KAGI", TargetSprint: "S1"}, wantErr: "only valid with --tickets-to sprint"},
		{name: "unknown tickets-to", opts: DeleteOptions{TicketsTo: "void"}, wantErr: "invalid --tickets-to"},
		{name: "promote", opts: DeleteOptions{Children: "promote"}, wantChild: childrenPromote},
		{name: "move", opts: DeleteOptions{Children: "move", NewParent: "KAGI"}, wantChild: childrenMove},
		{name: "move without parent", opts: DeleteOptions{Children: "move"}, wantErr: "needs --new-parent"},
		{name: "parent without move", opts: DeleteOptions{Children: "promote", NewParent: "KAGI"}, wantErr: "only valid with --children move"},
		{name: "children delete without yes", opts: DeleteOptions{Children: "delete"}, wantErr: "pass --yes"},
		{name: "unknown children", opts: DeleteOptions{Children: "orphan"}, wantErr: "invalid --children"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			ticket, child, err := checkDelete(tt.opts)
			if tt.wantErr != "" {
				if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
					t.Fatalf("err = %v, want %q", err, tt.wantErr)
				}
				return
			}
			if err != nil || ticket != tt.wantTicket || child != tt.wantChild {
				t.Errorf("got %q %q %v", ticket, child, err)
			}
		})
	}
}
