package invite

import (
	"bytes"
	"testing"
	"time"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestPrintCreatedSanitizes(t *testing.T) {
	expires := "2026-11-22T08:00:00Z"
	inv := client.Invitation{Email: "ayse@example.com\r\nOK", Role: "ADMIN", ExpiresAt: &expires}
	var out bytes.Buffer
	PrintCreated(&out, inv, "Acme\x1b]0;x\x07", time.UTC)
	want := "Invited ayse@example.com OK as ADMIN to Acme. They join when they accept in Oba; the invitation expires 2026-11-22.\n"
	if out.String() != want {
		t.Errorf("out = %q", out.String())
	}
}
