package docfile

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestParseMail(t *testing.T) {
	tests := []struct {
		name, in, subject, body string
	}{
		{"subject, blank line, body", "Kennismaking\n\nBeste Jan,\n\nGroet,\nMert\n", "Kennismaking", "Beste Jan,\n\nGroet,\nMert"},
		{"no blank line", "Hi\nBody line", "Hi", "Body line"},
		{"windows line endings and BOM", "\ufeffHi there \r\n\r\n  \r\nLine 1\r\nLine 2\r\n\r\n", "Hi there", "Line 1\nLine 2"},
		{"body indentation kept", "S\n\n    indented\n", "S", "    indented"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			mail, err := ParseMail(tt.in)
			if err != nil {
				t.Fatal(err)
			}
			if mail.Subject != tt.subject || mail.Body != tt.body {
				t.Errorf("got %q / %q", mail.Subject, mail.Body)
			}
		})
	}
}

func TestParseMailRefusesEmptyParts(t *testing.T) {
	for in, want := range map[string]string{
		"":                "subject, is empty",
		"\n\nBody":        "subject, is empty",
		"Subject only":    "body after the subject line is empty",
		"Subject\n\n  \n": "body after the subject line is empty",
	} {
		if _, err := ParseMail(in); err == nil || !strings.Contains(err.Error(), want) {
			t.Errorf("%q: err = %v, want %q", in, err, want)
		}
	}
}

func TestMailFormatRoundTrip(t *testing.T) {
	mail := Mail{Subject: "Offerte", Body: "Beste Jan,\n\nZie bijlage."}
	got, err := ParseMail(mail.Format())
	if err != nil || got != mail {
		t.Fatalf("got %+v, %v", got, err)
	}
	if mail.Format() != "Offerte\n\nBeste Jan,\n\nZie bijlage.\n" {
		t.Errorf("format = %q", mail.Format())
	}
}

func TestReadMailNamesTheFile(t *testing.T) {
	path := filepath.Join(t.TempDir(), "mail.md")
	if err := os.WriteFile(path, []byte("Only a subject"), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := ReadMail(path); err == nil || !strings.HasPrefix(err.Error(), path+": ") {
		t.Fatalf("err = %v", err)
	}
}
