package httplog

import (
	"bytes"
	"net/http"
	"net/url"
	"strings"
	"testing"
)

func TestRedactURL(t *testing.T) {
	tests := []struct{ in, want string }{
		{"https://api.example/otak/events?page=1&search=acme", "https://api.example/otak/events?page=1&search=acme"},
		{"https://blob.example/c/f.pdf?sv=2024&sig=SECRET", "https://blob.example/c/f.pdf?sig=REDACTED&sv=2024"},
		{"https://api.example/x?Token=abc", "https://api.example/x?Token=REDACTED"},
	}
	for _, tt := range tests {
		u, err := url.Parse(tt.in)
		if err != nil {
			t.Fatal(err)
		}
		if got := redactURL(u); got != tt.want {
			t.Errorf("redactURL(%s) = %s, want %s", tt.in, got, tt.want)
		}
	}
}

func TestWriteHeadersRedactsCredentials(t *testing.T) {
	var out bytes.Buffer
	writeHeaders(&out, ">", http.Header{
		"Authorization":     {"Bearer secret-token"},
		"Cookie":            {"session=secret"},
		"X-Organization-Id": {"org-1"},
	})
	got := out.String()
	for _, want := range []string{"> Authorization: Bearer <redacted>\n", "> Cookie: <redacted>\n", "> X-Organization-Id: org-1\n"} {
		if !strings.Contains(got, want) {
			t.Errorf("missing %q in:\n%s", want, got)
		}
	}
	if strings.Contains(got, "secret") {
		t.Errorf("credential logged:\n%s", got)
	}
}
