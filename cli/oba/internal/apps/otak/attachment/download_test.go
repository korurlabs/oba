package attachment

import "testing"

func TestLocalNameStaysInTheDirectory(t *testing.T) {
	for _, tt := range []struct{ in, want string }{
		{"beeld 1.png", "beeld 1.png"},
		{"../../etc/passwd", "passwd"},
		{`..\..\evil.exe`, "evil.exe"},
		{"/abs/path.jpg", "path.jpg"},
		{"..", "fallback"},
		{"", "fallback"},
		{"dir/", "dir"},
	} {
		if got := localName(tt.in, "fallback"); got != tt.want {
			t.Errorf("localName(%q) = %q, want %q", tt.in, got, tt.want)
		}
	}
}
