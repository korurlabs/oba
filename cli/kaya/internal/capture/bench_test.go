package capture

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sort"
	"testing"
	"time"
)

// BenchmarkRecordFixtures replays the 15 hook events of the fixtures session and reports the
// median and the 95th percentile of one Record call. The target for the median is 50 ms on a
// developer laptop; it is reported, not a gate.
func BenchmarkRecordFixtures(b *testing.B) {
	var c corpusCase
	readCorpus(b, filepath.Join("testdata", "cases", "fixtures-session", "case.json"), &c)
	transcripts, err := filepath.Abs(filepath.Join("testdata", "transcripts"))
	if err != nil {
		b.Fatal(err)
	}
	_, repo, state := scratchCheckout(b)
	subst := map[string]string{"{{REPO}}": repo, "{{STATE}}": state, "{{TRANSCRIPTS}}": transcripts,
		"{{OUTSIDE}}": filepath.Join(filepath.Dir(repo), "outside"), "{{HOME}}": filepath.Join(filepath.Dir(repo), "home")}
	if c.Account {
		data, _ := json.Marshal(map[string]any{"oauthAccount": goldenAccount})
		writeFile(b, filepath.Join(os.Getenv("CLAUDE_CONFIG_DIR"), ".claude.json"), string(data))
	}
	var inputs []map[string]any
	for _, step := range c.Steps {
		for _, f := range step.Files {
			path := filepath.Join(repo, expand(f.Path, subst).(string))
			writeFile(b, path, expand(f.Content, subst).(string))
		}
		if step.RawStdin != nil {
			continue
		}
		data, err := json.Marshal(expand(any(step.Hook), subst))
		if err != nil {
			b.Fatal(err)
		}
		in, err := Decode(data)
		if err != nil {
			b.Fatal(err)
		}
		inputs = append(inputs, in)
	}
	if len(inputs) != 15 {
		b.Fatalf("%d hook fixtures, want 15", len(inputs))
	}

	var durations []time.Duration
	now := time.Unix(1_800_000_000, 0)
	for i := 0; b.Loop(); i++ {
		// A fresh queue per pass keeps every pass the cost of a session's first 15 events.
		b.Setenv("XDG_STATE_HOME", filepath.Join(filepath.Dir(repo), "xdg-run", time.Now().Format("150405.000000000")))
		for _, in := range inputs {
			start := time.Now()
			if _, err := Record(in, now, nil); err != nil {
				b.Fatal(err)
			}
			durations = append(durations, time.Since(start))
		}
	}
	sort.Slice(durations, func(i, j int) bool { return durations[i] < durations[j] })
	b.ReportMetric(float64(durations[len(durations)/2].Microseconds())/1000, "median-ms")
	b.ReportMetric(float64(durations[len(durations)*95/100].Microseconds())/1000, "p95-ms")
}
