package capture

import (
	"encoding/json"
	"os"
	"testing"
	"time"
)

func TestCaptureUsageWritesChangedWindowsOnly(t *testing.T) {
	stateHome := t.TempDir()
	t.Setenv("XDG_STATE_HOME", stateHome)
	now := time.Unix(1_800_000_000, 0)
	input := func(used string) map[string]any {
		in, err := Decode([]byte(`{"session_id":"s-1","rate_limits":{` +
			`"five_hour":{"used_percentage":` + used + `,"resets_at":1800003600},` +
			`"seven_day":{"used_percentage":150,"resets_at":1800003600}}}`))
		if err != nil {
			t.Fatal(err)
		}
		return in
	}

	path, err := CaptureUsage(input("23"), now)
	if err != nil || path == "" {
		t.Fatalf("first capture: path %q, err %v", path, err)
	}
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	var written map[string]any
	if err := json.Unmarshal(data, &written); err != nil {
		t.Fatal(err)
	}
	want := map[string]any{
		"sessionId":  "s-1",
		"capturedAt": "2027-01-15T08:00:00Z",
		"fiveHour":   map[string]any{"usedPercentage": 23.0, "resetsAt": 1800003600.0},
	}
	if got, _ := json.Marshal(written); string(got) != mustJSON(t, want) {
		t.Fatalf("usage file %s, want %s", got, mustJSON(t, want))
	}

	if path, err := CaptureUsage(input("23.0"), now.Add(time.Minute)); err != nil || path != "" {
		t.Fatalf("unchanged windows: path %q, err %v", path, err)
	}
	if path, err := CaptureUsage(input("24"), now.Add(time.Minute)); err != nil || path == "" {
		t.Fatalf("changed window: path %q, err %v", path, err)
	}
}

func TestCaptureUsageSkipsInputWithoutValidWindow(t *testing.T) {
	t.Setenv("XDG_STATE_HOME", t.TempDir())
	now := time.Unix(1_800_000_000, 0)
	for name, raw := range map[string]string{
		"no session":      `{"rate_limits":{"five_hour":{"used_percentage":1,"resets_at":1800000000}}}`,
		"bad session id":  `{"session_id":"a/b","rate_limits":{"five_hour":{"used_percentage":1,"resets_at":1800000000}}}`,
		"far reset":       `{"session_id":"s","rate_limits":{"five_hour":{"used_percentage":1,"resets_at":1900000000}}}`,
		"percent as text": `{"session_id":"s","rate_limits":{"five_hour":{"used_percentage":"1","resets_at":1800000000}}}`,
		"no rate limits":  `{"session_id":"s"}`,
	} {
		in, err := Decode([]byte(raw))
		if err != nil {
			t.Fatal(err)
		}
		if path, err := CaptureUsage(in, now); err != nil || path != "" {
			t.Errorf("%s: path %q, err %v", name, path, err)
		}
	}
}

func mustJSON(t *testing.T, value any) string {
	t.Helper()
	data, err := json.Marshal(value)
	if err != nil {
		t.Fatal(err)
	}
	return string(data)
}
