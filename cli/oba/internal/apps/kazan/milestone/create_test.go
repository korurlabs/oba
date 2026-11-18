package milestone

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestCreateCheck(t *testing.T) {
	tests := []struct {
		name    string
		in      CreateInput
		want    string
		wantErr string
	}{
		{name: "required only", in: CreateInput{Name: "GA", TargetDate: "2027-01-15"}, want: `{"name":"GA","targetDate":"2027-01-15"}`},
		{name: "all", in: CreateInput{Name: "Beta", TargetDate: "2026-12-01", StartDate: "2026-10-15", Description: "d", Color: "#22C55E"},
			want: `{"name":"Beta","description":"d","startDate":"2026-10-15","targetDate":"2026-12-01","color":"#22C55E"}`},
		{name: "no name", in: CreateInput{TargetDate: "2027-01-15"}, wantErr: "--name is required"},
		{name: "no target", in: CreateInput{Name: "GA"}, wantErr: "--target-date is required"},
		{name: "bad start", in: CreateInput{Name: "GA", TargetDate: "2027-01-15", StartDate: "soon"}, wantErr: "--start-date: invalid date"},
		{name: "bad color", in: CreateInput{Name: "GA", TargetDate: "2027-01-15", Color: "green"}, wantErr: "#RRGGBB"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			req, err := tt.in.check()
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
