package milestone

import (
	"encoding/json"
	"errors"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

func ptr(s string) *string { return &s }

func TestUpdateRequest(t *testing.T) {
	tests := []struct {
		name    string
		in      UpdateInput
		want    string
		wantErr string
	}{
		{name: "empty values clear", in: UpdateInput{Description: ptr(""), StartDate: ptr("")},
			want: `{"clearStartDate":true,"clearDescription":true}`},
		{name: "values are sent", in: UpdateInput{Name: ptr("GA"), StartDate: ptr(" 2026-10-01"), TargetDate: ptr("2026-12-01"), Color: ptr("#22C55E")},
			want: `{"name":"GA","startDate":"2026-10-01","targetDate":"2026-12-01","color":"#22C55E"}`},
		{name: "blank name", in: UpdateInput{Name: ptr(" ")}, wantErr: "--name cannot be empty"},
		{name: "bad target", in: UpdateInput{TargetDate: ptr("")}, wantErr: "--target-date: invalid date"},
		{name: "bad color", in: UpdateInput{Color: ptr("red")}, wantErr: "#RRGGBB"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			req, err := tt.in.Request()
			if tt.wantErr != "" {
				if err == nil || !strings.Contains(err.Error(), tt.wantErr) {
					t.Fatalf("err = %v", err)
				}
				return
			}
			body, _ := json.Marshal(req)
			if err != nil || string(body) != tt.want {
				t.Errorf("body %s, err %v", body, err)
			}
		})
	}
}

func TestUpdateRequestWithNothing(t *testing.T) {
	if _, err := (UpdateInput{}).Request(); !errors.Is(err, terminal.ErrNothingToUpdate) {
		t.Errorf("err = %v", err)
	}
}
