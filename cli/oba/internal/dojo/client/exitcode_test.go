package client

import (
	"errors"
	"fmt"
	"net/http"
	"testing"
)

func TestExitCode(t *testing.T) {
	tests := []struct {
		name string
		err  error
		want int
	}{
		{"404", &APIError{Status: http.StatusNotFound}, 2},
		{"403", &APIError{Status: http.StatusForbidden}, 3},
		{"wrapped 403", fmt.Errorf("wrap: %w", &APIError{Status: http.StatusForbidden}), 3},
		{"400", &APIError{Status: http.StatusBadRequest}, 1},
		{"500", &APIError{Status: http.StatusInternalServerError}, 1},
		{"plain", errors.New("x"), 1},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := ExitCode(tt.err); got != tt.want {
				t.Errorf("ExitCode = %d, want %d", got, tt.want)
			}
		})
	}
}
