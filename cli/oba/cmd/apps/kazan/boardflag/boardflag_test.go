package boardflag

import (
	"errors"
	"fmt"
	"net/http"
	"testing"

	"github.com/spf13/cobra"

	"github.com/korurlabs/oba-cli/internal/apps/kazan/board"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/clitest"
	"github.com/korurlabs/oba-cli/internal/dojo/config"
)

const (
	idOps  = "0a000000-0000-4000-8000-000000000001"
	idKagi = "0a000000-0000-4000-8000-000000000003"
)

// newRoot builds "oba kazan show", whose show prints the board Selected returns.
func newRoot(c *client.Client) *cobra.Command {
	group := &cobra.Command{Use: "kazan"}
	Add(group)
	group.AddCommand(&cobra.Command{
		Use: "show",
		RunE: func(cmd *cobra.Command, _ []string) error {
			ref, err := Selected(cmd, c)
			if err != nil {
				return err
			}
			_, _ = fmt.Fprint(cmd.OutOrStdout(), ref.ID)
			return nil
		},
	})
	root := &cobra.Command{Use: "oba", SilenceUsage: true, SilenceErrors: true}
	root.AddCommand(group)
	return root
}

func setup(t *testing.T) (*clitest.MockAPI, *client.Client) {
	t.Helper()
	clitest.SetupHome(t)
	api := clitest.NewMockAPI(t)
	api.Respond("GET /kaizen/boards", http.StatusOK, `{"data":[{"id":"`+idOps+`","name":"Operations","prefix":"OPS"}],`+
		`"pagination":{"currentPage":1,"last":true}}`)
	return api, client.NewWithToken(api.URL(), clitest.OrgIDAcme, "tok")
}

func TestSelectedWithoutBoardOrDefault(t *testing.T) {
	_, c := setup(t)
	_, _, err := clitest.RunCLI(t, newRoot(c), "", "kazan", "show")
	if !errors.Is(err, board.ErrNoBoard) || err.Error() != "No board selected. Run 'oba kazan board use <board>'." {
		t.Errorf("err = %v", err)
	}
}

func TestSelectedTakesTheFlagBeforeTheDefault(t *testing.T) {
	_, c := setup(t)
	if err := config.Save(config.File{KazanBoards: map[string]string{clitest.OrgIDAcme: idKagi}}); err != nil {
		t.Fatal(err)
	}
	out, _, err := clitest.RunCLI(t, newRoot(c), "", "kazan", "--board", "ops", "show")
	if err != nil || out != idOps {
		t.Errorf("out %q err %v", out, err)
	}
	out, _, err = clitest.RunCLI(t, newRoot(c), "", "kazan", "show")
	if err != nil || out != idKagi {
		t.Errorf("out %q err %v", out, err)
	}
}

func TestSelectedIgnoresTheDefaultOfAnotherOrganization(t *testing.T) {
	_, c := setup(t)
	if err := config.Save(config.File{KazanBoards: map[string]string{clitest.OrgIDBeta: idKagi}}); err != nil {
		t.Fatal(err)
	}
	if _, _, err := clitest.RunCLI(t, newRoot(c), "", "kazan", "show"); !errors.Is(err, board.ErrNoBoard) {
		t.Errorf("err = %v", err)
	}
}

func TestValueWithoutTheFlag(t *testing.T) {
	if got := Value(&cobra.Command{Use: "x"}); got != "" {
		t.Errorf("value = %q", got)
	}
}
