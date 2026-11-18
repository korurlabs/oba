package docfile

import (
	"errors"
	"fmt"
	"os"
	"strings"
)

// byteOrderMark is the UTF-8 BOM some editors put before the first line.
const byteOrderMark = "\ufeff"

// maxMailFileBytes bounds a mail file: a subject of 255 characters and a body of 10000, in up to
// four bytes each.
const maxMailFileBytes = 64 * 1024

// Mail is the content of a mail template or of the mail of an outreach.
type Mail struct {
	Subject string
	Body    string
}

// ReadMail reads a mail file: see ParseMail.
func ReadMail(path string) (Mail, error) {
	info, err := os.Stat(path)
	if err != nil {
		return Mail{}, fmt.Errorf("failed to read %s: %w", path, err)
	}
	if info.Size() > maxMailFileBytes {
		return Mail{}, fmt.Errorf("%s is larger than %d bytes; a mail body holds at most 10000 characters", path, maxMailFileBytes)
	}
	data, err := os.ReadFile(path)
	if err != nil {
		return Mail{}, fmt.Errorf("failed to read %s: %w", path, err)
	}
	mail, err := ParseMail(string(data))
	if err != nil {
		return Mail{}, fmt.Errorf("%s: %w", path, err)
	}
	return mail, nil
}

// ParseMail reads the mail file form: the subject on the first line, then the body. The blank
// lines between them and the trailing white space of the body are not part of the body. Windows
// line endings are read as plain ones.
func ParseMail(text string) (Mail, error) {
	text = strings.ReplaceAll(text, "\r\n", "\n")
	text = strings.TrimPrefix(text, byteOrderMark)
	subject, body, _ := strings.Cut(text, "\n")
	subject = strings.TrimSpace(subject)
	body = strings.TrimRight(dropBlankLines(body), " \t\n")
	if subject == "" {
		return Mail{}, errors.New("the first line, the subject, is empty")
	}
	if strings.TrimSpace(body) == "" {
		return Mail{}, errors.New("the body after the subject line is empty")
	}
	return Mail{Subject: subject, Body: body}, nil
}

// Format writes the mail in the mail file form: the subject, a blank line, the body and a final
// line break. ParseMail reads it back unchanged.
func (m Mail) Format() string {
	return m.Subject + "\n\n" + m.Body + "\n"
}

// dropBlankLines removes the empty or white-space-only lines at the start of text.
func dropBlankLines(text string) string {
	for {
		line, rest, found := strings.Cut(text, "\n")
		if !found || strings.TrimSpace(line) != "" {
			return text
		}
		text = rest
	}
}
