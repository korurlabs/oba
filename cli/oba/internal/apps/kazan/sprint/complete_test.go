package sprint

import (
	"strings"
	"testing"
)

func TestCompleteOptionsCheck(t *testing.T) {
	tests := []struct {
		name    string
		opts    CompleteOptions
		want    string
		wantErr string
	}{
		{name: "defaults to the backlog", want: "MOVE_TO_BACKLOG"},
		{name: "upper case value", opts: CompleteOptions{TicketsTo: " BACKLOG "}, want: "MOVE_TO_BACKLOG"},
		{name: "sprint", opts: CompleteOptions{TicketsTo: "sprint", TargetSprint: "Sprint 2"}, want: "MOVE_TO_SPRINT"},
		{name: "delete with consent", opts: CompleteOptions{TicketsTo: "delete", Yes: true}, want: "DELETE"},
		{name: "delete without consent", opts: CompleteOptions{TicketsTo: "delete"}, wantErr: "pass --yes to confirm"},
		{name: "delete with a target board", opts: CompleteOptions{TicketsTo: "delete", Yes: true, TargetBoard: "MOB"},
			wantErr: "--target-board is not valid with --tickets-to delete"},
		{name: "sprint without a target", opts: CompleteOptions{TicketsTo: "sprint"}, wantErr: "--tickets-to sprint needs --target-sprint"},
		{name: "target with the backlog", opts: CompleteOptions{TargetSprint: "Sprint 2"}, wantErr: "--target-sprint is only valid with --tickets-to sprint"},
		{name: "unknown action", opts: CompleteOptions{TicketsTo: "archive"}, wantErr: `invalid --tickets-to "archive"`},
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
