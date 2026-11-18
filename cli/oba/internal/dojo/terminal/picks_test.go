package terminal

import (
	"reflect"
	"strings"
	"testing"
)

func TestParsePicks(t *testing.T) {
	skipped := map[int]bool{2: true}
	tests := []struct {
		name, in string
		want     []int
		errPart  string
	}{
		{"empty cancels", "", nil, ""},
		{"blank cancels", "  ", nil, ""},
		{"single", "3", []int{3}, ""},
		{"list and range", "1,3-4", []int{1, 3, 4}, ""},
		{"sorted and unique", "4, 1,4,3-4", []int{1, 3, 4}, ""},
		{"all leaves out skipped", "ALL", []int{1, 3, 4, 5}, ""},
		{"out of range high", "6", nil, "out of range"},
		{"out of range zero", "0", nil, "out of range"},
		{"reversed range", "4-3", nil, "out of range"},
		{"skipped number", "1,2", nil, "2 is already in Otak and cannot be picked"},
		{"skipped inside range", "1-3", nil, "2 is already in Otak and cannot be picked"},
		{"not a number", "x", nil, "not a number"},
		{"empty part", "1,,3", nil, "not a number"},
		{"bad range end", "1-x", nil, "not a number"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := ParsePicks(tt.in, 5, skipped, "is already in Otak and cannot be picked")
			if tt.errPart != "" {
				if err == nil || !strings.Contains(err.Error(), tt.errPart) {
					t.Fatalf("err = %v, want containing %q", err, tt.errPart)
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("got %v, want %v", got, tt.want)
			}
		})
	}
}
