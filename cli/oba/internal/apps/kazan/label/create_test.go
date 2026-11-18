package label

import (
	"strings"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

func TestCreateNeedsAName(t *testing.T) {
	c := client.NewWithToken("http://127.0.0.1:1", "org", "tok")
	if _, err := Create(c, "b", " ", nil); err == nil || !strings.Contains(err.Error(), "--name is required") {
		t.Fatalf("err = %v", err)
	}
}
