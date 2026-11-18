package sprint

import (
	"strings"
	"testing"
)

func TestDeleteOptionsCheck(t *testing.T) {
	tests := []struct {
		name    string
		opts    DeleteOptions
		want    string
		wantErr string
	}{
		{name: "defaults to the backlog", want: "MOVE_TO_BACKLOG"},
		{name: "sprint", opts: DeleteOptions{TicketsTo: "sprint", TargetSprint: "Sprint 2"}, want: "MOVE_TO_SPRINT"},
		{name: "delete with consent", opts: DeleteOptions{TicketsTo: "delete", Yes: true}, want: "DELETE"},
		{name: "delete without consent", opts: DeleteOptions{TicketsTo: "delete"}, wantErr: "pass --yes to confirm"},
		{name: "sprint without a target", opts: DeleteOptions{TicketsTo: "sprint"}, wantErr: "needs --target-sprint"},
		{name: "unknown action", opts: DeleteOptions{TicketsTo: "x"}, wantErr: "invalid --tickets-to"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := tt.opts.Check()
			if tt.wantErr != "" {
				if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
					t.Fatalf("err = %v, want %q", err, tt.wantErr)
				}
				return
			}
			if err != nil || got != tt.want {
				t.Errorf("got %q, %v", got, err)
			}
		})
	}
}
