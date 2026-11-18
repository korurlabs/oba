package redact

import (
	"strings"
	"testing"
	"time"
)

// pathologicalWord is a 200 000 character word made of one repeated secret-looking name, the
// input a backtracking pattern would take quadratic time on.
func pathologicalWord(chars int) string {
	return strings.Repeat("A_KEY=", chars/len("A_KEY="))
}

func TestTextAndFindFinishLongRepeatedNamesInBoundedTime(t *testing.T) {
	input := pathologicalWord(200_000)
	if len(input) < 199_990 {
		t.Fatalf("input has %d characters", len(input))
	}
	start := time.Now()
	out := Text(input)
	textTime := time.Since(start)
	start = time.Now()
	lines := Find(input)
	findTime := time.Since(start)
	if textTime > 5*time.Second || findTime > 5*time.Second {
		t.Errorf("Text took %v and Find %v on %d characters, want linear time well under 5 s", textTime, findTime, len(input))
	}
	if out == input {
		t.Error("the repeated name was not redacted")
	}
	if len(lines) != 1 || lines[0] != 1 {
		t.Errorf("Find = %v, want line 1 only", lines)
	}
}

// TestTextScalesLinearlyWithInputSize compares a 4 times larger input with the smaller one; a
// quadratic pass would take about 16 times as long, a linear one about 4.
func TestTextScalesLinearlyWithInputSize(t *testing.T) {
	measure := func(chars int) time.Duration {
		input := pathologicalWord(chars)
		best := time.Duration(1<<63 - 1)
		for range 3 {
			start := time.Now()
			Text(input)
			best = min(best, time.Since(start))
		}
		return best
	}
	small, large := measure(100_000), measure(400_000)
	if small < time.Millisecond {
		small = time.Millisecond
	}
	if ratio := float64(large) / float64(small); ratio > 10 {
		t.Errorf("a 4x larger input took %.1fx as long (%v then %v), want near 4x", ratio, small, large)
	}
}

func BenchmarkTextRepeatedSecretNames(b *testing.B) {
	input := pathologicalWord(200_000)
	b.SetBytes(int64(len(input)))
	for b.Loop() {
		Text(input)
	}
}
