package cyberscore

import (
	"encoding/json"
	"strings"
	"testing"
)

func intPtr(n int) *int { return &n }

func validCreate() CreateInput {
	return CreateInput{Name: "MFA enforced", Expected: "Every account signs in with MFA", Weight: intPtr(10)}
}

func TestCreateRequestOmitsUnsetOptionals(t *testing.T) {
	req, err := validCreate().request()
	if err != nil {
		t.Fatal(err)
	}
	body, _ := json.Marshal(req)
	if string(body) != `{"name":"MFA enforced","expected":"Every account signs in with MFA","weight":10}` {
		t.Errorf("body = %s", body)
	}
}

func TestCreateRequestParsesEnums(t *testing.T) {
	in := validCreate()
	in.Category, in.Outcome, in.Status, in.SortOrder, in.Description = "network-edge", "partial", "in progress", intPtr(0), "Why"
	req, err := in.request()
	if err != nil {
		t.Fatal(err)
	}
	body, _ := json.Marshal(req)
	want := `{"name":"MFA enforced","description":"Why","expected":"Every account signs in with MFA","weight":10,` +
		`"sortOrder":0,"category":"NETWORK_EDGE","outcome":"PARTIAL","status":"IN_PROGRESS"}`
	if string(body) != want {
		t.Errorf("body = %s", body)
	}
}

func TestCreateRequiredFlags(t *testing.T) {
	for flag, edit := range map[string]func(*CreateInput){
		"--name":     func(in *CreateInput) { in.Name = " " },
		"--expected": func(in *CreateInput) { in.Expected = "" },
		"--weight":   func(in *CreateInput) { in.Weight = nil },
	} {
		in := validCreate()
		edit(&in)
		if err := in.Check(); err == nil || err.Error() != flag+" is required" {
			t.Errorf("%s: err = %v", flag, err)
		}
	}
}

func TestCreateLimits(t *testing.T) {
	long := func(n int) string { return strings.Repeat("ü", n) }
	for _, tc := range []struct {
		name string
		edit func(*CreateInput)
		ok   bool
	}{
		{"name at 255", func(in *CreateInput) { in.Name = long(255) }, true},
		{"name at 256", func(in *CreateInput) { in.Name = long(256) }, false},
		{"expected at 5000", func(in *CreateInput) { in.Expected = long(5000) }, true},
		{"expected at 5001", func(in *CreateInput) { in.Expected = long(5001) }, false},
		{"description at 5000", func(in *CreateInput) { in.Description = long(5000) }, true},
		{"description at 5001", func(in *CreateInput) { in.Description = long(5001) }, false},
		{"weight 1", func(in *CreateInput) { in.Weight = intPtr(1) }, true},
		{"weight 0", func(in *CreateInput) { in.Weight = intPtr(0) }, false},
		{"weight 100", func(in *CreateInput) { in.Weight = intPtr(100) }, true},
		{"weight 101", func(in *CreateInput) { in.Weight = intPtr(101) }, false},
		{"sort order 0", func(in *CreateInput) { in.SortOrder = intPtr(0) }, true},
		{"sort order -1", func(in *CreateInput) { in.SortOrder = intPtr(-1) }, false},
		{"sort order 9999", func(in *CreateInput) { in.SortOrder = intPtr(9999) }, true},
		{"sort order 10000", func(in *CreateInput) { in.SortOrder = intPtr(10000) }, false},
		{"unknown category", func(in *CreateInput) { in.Category = "GDPR" }, false},
		{"unknown outcome", func(in *CreateInput) { in.Outcome = "OK" }, false},
		{"unknown status", func(in *CreateInput) { in.Status = "OPEN" }, false},
	} {
		in := validCreate()
		tc.edit(&in)
		if err := in.Check(); (err == nil) != tc.ok {
			t.Errorf("%s: err = %v", tc.name, err)
		}
	}
}
