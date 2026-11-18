// Package terminal holds the input helpers the commands share: the questions asked on a
// terminal and the JSON object read from stdin.
package terminal

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"

	"golang.org/x/term"
)

// StdinIsTerminal reports whether stdin is a terminal.
func StdinIsTerminal() bool {
	return term.IsTerminal(int(os.Stdin.Fd()))
}

// StdoutIsTerminal reports whether stdout is a terminal.
func StdoutIsTerminal() bool {
	return term.IsTerminal(int(os.Stdout.Fd()))
}

// MaxTextBytes bounds the text read by ReadText; an ICP description is a few paragraphs.
const MaxTextBytes = 64 * 1024

// ReadText reads lines until the end of input, which on a terminal is Ctrl-D on a line of its
// own, and returns them trimmed. Blank lines between paragraphs are kept, so a pasted text
// arrives whole. Windows line ends become plain newlines.
func ReadText(in *bufio.Reader) (string, error) {
	data, err := io.ReadAll(io.LimitReader(in, MaxTextBytes+1))
	if err != nil {
		return "", fmt.Errorf("failed to read the text: %w", err)
	}
	if len(data) > MaxTextBytes {
		return "", fmt.Errorf("the text holds more than %d bytes", MaxTextBytes)
	}
	return strings.TrimSpace(strings.ReplaceAll(string(data), "\r\n", "\n")), nil
}

// Confirm asks "<question> (y/n) " until the answer is y, yes, n or no, in any case, and
// reports whether it was a yes. Before every ask it discards the input already waiting, both in
// in and, when src is a terminal, in the terminal itself: lines pasted earlier must never
// answer the question. Any other answer asks again; the end of input is a no.
func Confirm(in *bufio.Reader, src io.Reader, out io.Writer, question string) (bool, error) {
	for {
		answer, eof, err := Ask(in, src, out, question+" (y/n) ")
		if err != nil {
			return false, err
		}
		switch strings.ToLower(answer) {
		case "y", "yes":
			return true, nil
		case "n", "no":
			return false, nil
		}
		if eof {
			_, _ = fmt.Fprintln(out)
			return false, nil
		}
		_, _ = fmt.Fprintln(out, "Please answer y or n.")
	}
}

// Ask discards the input already waiting, as Confirm does, writes prompt to out and reads one
// answer, trimmed; eof reports that the input has ended. Every question of a menu goes through
// it, so a line pasted or typed ahead never answers a question it was not meant for.
func Ask(in *bufio.Reader, src io.Reader, out io.Writer, prompt string) (string, bool, error) {
	if err := DiscardPending(in, src); err != nil {
		return "", false, err
	}
	_, _ = fmt.Fprint(out, prompt)
	return ReadLine(in)
}

// DiscardPending drops the bytes in's buffer holds and, when src is a terminal, the input the
// terminal has received but nobody has read yet.
func DiscardPending(in *bufio.Reader, src io.Reader) error {
	if _, err := in.Discard(in.Buffered()); err != nil {
		return fmt.Errorf("failed to discard pending input: %w", err)
	}
	f, ok := src.(*os.File)
	if !ok || !term.IsTerminal(int(f.Fd())) {
		return nil
	}
	if err := flushTerminalInput(int(f.Fd())); err != nil {
		return fmt.Errorf("failed to discard pending terminal input: %w", err)
	}
	return nil
}

// ReadLine reads one answer, trimmed; eof reports that the input has ended.
func ReadLine(in *bufio.Reader) (string, bool, error) {
	line, err := in.ReadString('\n')
	if err != nil && !errors.Is(err, io.EOF) {
		return "", false, fmt.Errorf("failed to read the answer: %w", err)
	}
	return strings.TrimSpace(line), errors.Is(err, io.EOF), nil
}
