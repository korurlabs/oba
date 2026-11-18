package updates

import (
	"bytes"
	"errors"
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
)

func newTestPrompter(lines ...string) (Prompter, *bytes.Buffer) {
	var out bytes.Buffer
	return NewPrompter(clitest.Typed(lines...), &out), &out
}

func TestPercentageAsksAgainOnABadValue(t *testing.T) {
	p, out := newTestPrompter("150\n", "60\n")
	n, err := p.Percentage("Percentage", 30)
	if err != nil || n != 60 {
		t.Fatalf("n %d err %v", n, err)
	}
	if !strings.Contains(out.String(), "Percentage [30]: ") || !strings.Contains(out.String(), "whole number") {
		t.Errorf("out = %q", out.String())
	}
}

func TestPercentageEnterKeepsTheCurrentValue(t *testing.T) {
	p, _ := newTestPrompter("\n")
	if n, err := p.Percentage("Percentage", 30); err != nil || n != 30 {
		t.Fatalf("n %d err %v", n, err)
	}
}

func TestOptionalTextKeepsOrClears(t *testing.T) {
	current := "old"
	p, _ := newTestPrompter("\n", "-\n", "new\n")
	kept, _ := p.OptionalText("Note", &current)
	cleared, _ := p.OptionalText("Note", &current)
	changed, _ := p.OptionalText("Note", &current)
	if kept == nil || *kept != "old" || cleared != nil || changed == nil || *changed != "new" {
		t.Errorf("kept %v cleared %v changed %v", kept, cleared, changed)
	}
}

func TestRequiredTextRepeatsUntilAnswered(t *testing.T) {
	p, out := newTestPrompter("\n", "Focus\n")
	got, err := p.RequiredText("Focus", nil)
	if err != nil || got == nil || *got != "Focus" || strings.Count(out.String(), "Focus: ") != 2 {
		t.Fatalf("got %v err %v out %q", got, err, out.String())
	}
}

func TestRequiredTextAtTheEndOfInputIsCancelled(t *testing.T) {
	p, _ := newTestPrompter()
	if _, err := p.RequiredText("Focus", nil); !errors.Is(err, ErrCancelled) {
		t.Fatalf("err = %v", err)
	}
}

func TestLinesSkipsWhatAFlagSet(t *testing.T) {
	note := "set"
	lines := []Line{
		{Key: "OPS-7", Percentage: 10, PercentSet: true, Note: &note, NoteSet: true},
		{Key: "KAGI-2", Percentage: 20, PercentSet: true},
	}
	p, out := newTestPrompter("typed\n")
	if err := p.Lines(lines); err != nil {
		t.Fatal(err)
	}
	if strings.Contains(out.String(), "OPS-7") || strings.Contains(out.String(), "Percentage") {
		t.Errorf("out = %q", out.String())
	}
	if lines[1].Note == nil || *lines[1].Note != "typed" {
		t.Errorf("line = %+v", lines[1])
	}
}
