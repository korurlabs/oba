package pr

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"strconv"

	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

// ErrCancelled reports that the user ended the input at a question; the command ends without a
// change and without an error.
var ErrCancelled = errors.New("cancelled")

// Prompter asks the questions of one interactive run on a terminal.
type Prompter struct {
	in  *bufio.Reader
	src io.Reader
	out io.Writer
}

// NewPrompter returns a prompter that reads answers from src and writes questions to out.
func NewPrompter(src io.Reader, out io.Writer) *Prompter {
	return &Prompter{in: bufio.NewReader(src), src: src, out: out}
}

// Out is where the questions go, for the lines a flow prints between them.
func (p *Prompter) Out() io.Writer {
	return p.out
}

// Choose lists options by number and returns the index of the one picked, asking again until
// a number in range is given. The end of input is ErrCancelled.
func (p *Prompter) Choose(label string, options []string) (int, error) {
	p.list(label, options)
	for {
		answer, eof, err := terminal.Ask(p.in, p.src, p.out, label+" number: ")
		if err != nil {
			return 0, err
		}
		if n, convErr := strconv.Atoi(answer); convErr == nil && n >= 1 && n <= len(options) {
			return n - 1, nil
		}
		if eof {
			_, _ = fmt.Fprintln(p.out)
			return 0, ErrCancelled
		}
		_, _ = fmt.Fprintf(p.out, "Please give a number from 1 to %d.\n", len(options))
	}
}

// ChooseMany lists options by number and returns the indexes picked, in list order: numbers
// and ranges such as 1,3-5, "all", or an empty answer for none. A wrong answer asks again; the
// end of input with nothing typed is ErrCancelled.
func (p *Prompter) ChooseMany(label string, options []string) ([]int, error) {
	p.list(label, options)
	for {
		answer, eof, err := terminal.Ask(p.in, p.src, p.out, label+" (numbers such as 1,3-5, all, or Enter for none): ")
		if err != nil {
			return nil, err
		}
		if eof && answer == "" {
			_, _ = fmt.Fprintln(p.out)
			return nil, ErrCancelled
		}
		picks, parseErr := terminal.ParsePicks(answer, len(options), nil, "")
		if parseErr == nil {
			indexes := make([]int, len(picks))
			for i, n := range picks {
				indexes[i] = n - 1
			}
			return indexes, nil
		}
		if eof {
			return nil, parseErr
		}
		_, _ = fmt.Fprintln(p.out, parseErr.Error())
	}
}

// Text asks for one line of text; Enter gives "". The end of input with nothing typed is
// ErrCancelled.
func (p *Prompter) Text(label string) (string, error) {
	answer, eof, err := terminal.Ask(p.in, p.src, p.out, label+": ")
	if err != nil {
		return "", err
	}
	if eof && answer == "" {
		_, _ = fmt.Fprintln(p.out)
		return "", ErrCancelled
	}
	return answer, nil
}

// OptionalText asks for an optional text. Without a current value Enter leaves it empty; with
// one, Enter keeps it and "-" clears it. nil is no text.
func (p *Prompter) OptionalText(label string, current *string) (*string, error) {
	if current == nil || *current == "" {
		answer, err := p.Text(label + " (optional)")
		if err != nil || answer == "" {
			return nil, err
		}
		return &answer, nil
	}
	answer, err := p.Text(fmt.Sprintf("%s [%s] (Enter keeps it, - clears it)", label, *current))
	if err != nil {
		return nil, err
	}
	switch answer {
	case "":
		return current, nil
	case "-":
		return nil, nil
	default:
		return &answer, nil
	}
}

// Confirm asks a yes or no question and reports whether the answer was yes.
func (p *Prompter) Confirm(question string) (bool, error) {
	return terminal.Confirm(p.in, p.src, p.out, question)
}

func (p *Prompter) list(label string, options []string) {
	_, _ = fmt.Fprintf(p.out, "%s:\n", label)
	for i, option := range options {
		_, _ = fmt.Fprintf(p.out, "  %d  %s\n", i+1, option)
	}
}
