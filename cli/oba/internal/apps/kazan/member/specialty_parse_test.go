package member

import (
	"errors"
	"strings"
	"testing"
)

func TestParseSpecialties(t *testing.T) {
	got, err := ParseSpecialties(" developer,, QA_engineer ")
	if err != nil || strings.Join(got, ",") != "DEVELOPER,QA_ENGINEER" {
		t.Errorf("got %v err %v", got, err)
	}
	if _, err := ParseSpecialties(" , "); !errors.Is(err, errNoSpecialties) {
		t.Errorf("err = %v", err)
	}
	if _, err := ParseSpecialties("developer,tester"); err == nil || !strings.Contains(err.Error(), `invalid specialty "tester"`) {
		t.Errorf("err = %v", err)
	}
}
