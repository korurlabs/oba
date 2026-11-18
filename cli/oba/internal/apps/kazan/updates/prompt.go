package updates

import (
	"bufio"
	"fmt"
	"io"

	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// Prompter asks the questions of an update on a terminal. Src is the raw input that In buffers,
// so that typed-ahead lines can be discarded before each question.
type Prompter struct {
	In  *bufio.Reader
	Src io.Reader
	Out io.Writer
}

// NewPrompter returns a prompter that reads src and writes to out.
func NewPrompter(src io.Reader, out io.Writer) Prompter {
	return Prompter{In: bufio.NewReader(src), Src: src, Out: out}
}

// Confirm asks a yes or no question; the end of input is a no.
func (p Prompter) Confirm(question string) (bool, error) {
	return terminal.Confirm(p.In, p.Src, p.Out, question)
}

// ask asks one question and returns the trimmed answer.
func (p Prompter) ask(prompt string) (string, bool, error) {
	return terminal.Ask(p.In, p.Src, p.Out, prompt)
}

// Percentage asks for a percentage from 0 to 100; Enter or the end of input keeps current.
func (p Prompter) Percentage(label string, current int) (int, error) {
	for {
		answer, eof, err := p.ask(fmt.Sprintf("%s [%d]: ", label, current))
		if err != nil {
			return 0, err
		}
		if answer == "" {
			return current, nil
		}
		n, parseErr := ParsePercentage(answer)
		if parseErr == nil {
			return n, nil
		}
		if eof {
			return 0, parseErr
		}
		_, _ = fmt.Fprintf(p.Out, "  %s\n", parseErr)
	}
}

// OptionalText asks for an optional text. Without a current value, Enter leaves it empty; with
// one, Enter keeps it and "-" clears it.
func (p Prompter) OptionalText(label string, current *string) (*string, error) {
	if BlankText(current) {
		answer, _, err := p.ask(label + " (optional): ")
		if err != nil {
			return nil, err
		}
		return OptionalText(answer), nil
	}
	answer, _, err := p.ask(fmt.Sprintf("%s [%s] (Enter keeps it, - clears it): ", label, cell(*current)))
	if err != nil {
		return nil, err
	}
	switch answer {
	case "":
		return current, nil
	case "-":
		return nil, nil
	default:
		return OptionalText(answer), nil
	}
}

// RequiredText asks for a text that may not stay empty. Enter keeps a current value; without
// one, the question repeats until something is typed. The end of input with nothing typed is
// ErrCancelled.
func (p Prompter) RequiredText(label string, current *string) (*string, error) {
	if !BlankText(current) {
		answer, _, err := p.ask(fmt.Sprintf("%s [%s] (Enter keeps it): ", label, cell(*current)))
		if err != nil {
			return nil, err
		}
		if answer == "" {
			return current, nil
		}
		return &answer, nil
	}
	for {
		answer, eof, err := p.ask(label + ": ")
		if err != nil {
			return nil, err
		}
		if answer != "" {
			return &answer, nil
		}
		if eof {
			return nil, ErrCancelled
		}
	}
}

// Lines asks for the percentage and note of each line that no flag set, with the current value
// as the default.
func (p Prompter) Lines(lines []Line) error {
	for i := range lines {
		l := &lines[i]
		if l.PercentSet && l.NoteSet {
			continue
		}
		_, _ = fmt.Fprintf(p.Out, "%s  %s\n", cell(l.Key), cell(l.Title))
		if !l.PercentSet {
			n, err := p.Percentage("  Percentage", l.Percentage)
			if err != nil {
				return err
			}
			l.Percentage = n
		}
		if !l.NoteSet {
			note, err := p.OptionalText("  Note", l.Note)
			if err != nil {
				return err
			}
			l.Note = note
		}
	}
	return nil
}
