package attachment

import (
	"errors"

	"github.com/korurlabs/oba-cli/internal/dojo/attachment"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// ReadFiles reads and checks every path for one upload as attachment.ReadFiles does. A page is
// never given an empty upload, so no path at all is refused.
func ReadFiles(paths []string) ([]client.AttachmentFile, error) {
	files, err := attachment.ReadFiles(paths)
	if err != nil {
		return nil, err
	}
	if len(files) == 0 {
		return nil, errors.New("pass --file with the file to attach; repeat it for more files")
	}
	return files, nil
}
