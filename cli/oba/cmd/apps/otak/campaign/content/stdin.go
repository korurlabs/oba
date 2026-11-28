package content

import (
	"fmt"
	"io"

	"github.com/spf13/cobra"
)

// contentStdin returns the input of create and update. A terminal on stdin means nothing was
// piped in, so the command fails at once instead of waiting for typed input.
func contentStdin(cmd *cobra.Command) (io.Reader, error) {
	if stdinIsTerminal() {
		return nil, fmt.Errorf("no JSON on stdin; pass one JSON object, as 'oba otak campaign content %s --help' shows", cmd.Name())
	}
	return cmd.InOrStdin(), nil
}
