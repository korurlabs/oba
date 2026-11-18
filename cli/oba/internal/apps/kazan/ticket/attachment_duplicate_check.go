package ticket

import (
	"fmt"
	"io"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

// CheckAttachmentDuplicates asks the API, per file, whether the same content is already attached
// to a ticket, and refuses the upload when it is, naming the file: the upload would otherwise
// fail as a whole without saying which file. A failed check is written to warn and the next file
// is checked, since the upload itself refuses a duplicate anyway and an API without the check
// must not block attaching. The checks stay out of the --json output.
func CheckAttachmentDuplicates(c *client.Client, files []client.AttachmentFile, warn io.Writer) error {
	lookup := c.Unrecorded()
	for _, f := range files {
		name := guardrail.OneLine(f.FileName)
		exists, err := lookup.AttachmentExists(AttachmentEntityType, f)
		if err != nil {
			_, _ = fmt.Fprintf(warn, "Warning: could not check %q for duplicates (%v); continuing\n", name, err)
			continue
		}
		if exists {
			return fmt.Errorf("%q has the same content as a file already attached to a ticket; the API refuses the same content on two tickets", name)
		}
	}
	return nil
}
