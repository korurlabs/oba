package member

import (
	"strings"
	"testing"
)

func TestParseRole(t *testing.T) {
	if role, err := ParseRole(" viewer "); err != nil || role != "VIEWER" {
		t.Errorf("role %q err %v", role, err)
	}
	if _, err := ParseRole(""); err == nil || !strings.Contains(err.Error(), "--role is required") {
		t.Errorf("err = %v", err)
	}
	if _, err := ParseRole("guest"); err == nil || !strings.Contains(err.Error(), `invalid role "guest"`) {
		t.Errorf("err = %v", err)
	}
}
