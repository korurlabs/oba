package label

import (
	"errors"
	"testing"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/terminal"
)

func TestUpdateWithoutAFieldMakesNoCall(t *testing.T) {
	// A client without a server: any call would fail with a connection error.
	c := client.NewWithToken("http://127.0.0.1:1", "org", "tok")
	if _, err := Update(c, "b", "l", nil, nil); !errors.Is(err, terminal.ErrNothingToUpdate) {
		t.Fatalf("err = %v", err)
	}
}
