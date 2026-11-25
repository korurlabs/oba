package page

import (
	"errors"
	"io"
	"strings"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// PutInput is one page put as the flags give it: the raw path, the language (tr or en), the
// title, the body file (- for stdin), the compliance mark (nil keeps the stored one) and whether
// to overwrite a page edited in Kalkan since the last upload.
type PutInput struct {
	Path       string
	Lang       string
	Title      string
	File       string
	Compliance *bool
	Force      bool
}

// BuildUpsert checks the input and reads the body, before any call, so that a typo or a missing
// file sends nothing. stdin is read when the file is -.
func BuildUpsert(in PutInput, stdin io.Reader) (client.KalkanPageUpsert, error) {
	path, err := client.NormalizeKalkanPagePath(in.Path)
	if err != nil {
		return client.KalkanPageUpsert{}, err
	}
	locale, err := client.KalkanLocale(in.Lang)
	if err != nil {
		return client.KalkanPageUpsert{}, err
	}
	if strings.TrimSpace(in.Title) == "" {
		return client.KalkanPageUpsert{}, errors.New("--title is required")
	}
	if in.File == "" {
		return client.KalkanPageUpsert{}, errors.New("--file is required; use - to read the body from stdin")
	}
	body, err := readBody(stdin, in.File)
	if err != nil {
		return client.KalkanPageUpsert{}, err
	}
	return client.KalkanPageUpsert{
		Path:         path,
		Compliance:   in.Compliance,
		Translations: []client.KalkanTranslationInput{{Locale: locale, Title: in.Title, Body: body}},
		Force:        in.Force,
	}, nil
}
