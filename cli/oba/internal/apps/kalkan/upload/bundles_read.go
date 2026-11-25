package upload

import (
	"fmt"

	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// BundlesInput names what to upload: Dir under the page path Target, or, with both empty, every
// entry of the kalkan.yaml that WorkDir finds. Lang (tr or en) is the language of files without
// a language suffix where no entry sets one.
type BundlesInput struct {
	Dir     string
	Target  string
	Lang    string
	WorkDir string
}

// ReadBundles reads every directory of the upload before any call, so an invalid kalkan.yaml, a
// missing directory or a file name that is not a slug ends the run with nothing written. It also
// returns the organization of the kalkan.yaml found, empty when there is none, so that a
// repository uploads to the organization it names also when the directory is given.
func ReadBundles(in BundlesInput) ([]*Bundle, string, error) {
	defaultLocale, err := client.KalkanLocale(in.Lang)
	if err != nil {
		return nil, "", err
	}
	proj, err := FindProject(in.WorkDir)
	if err != nil {
		return nil, "", err
	}
	organization := ""
	if proj != nil {
		organization = proj.Organization
	}
	if in.Dir != "" || in.Target != "" {
		target, err := client.NormalizeKalkanPagePath(in.Target)
		if err != nil {
			return nil, "", err
		}
		b, err := ReadBundle(in.Dir, target, defaultLocale)
		if err != nil {
			return nil, "", err
		}
		return []*Bundle{b}, organization, nil
	}
	if proj == nil {
		return nil, "", fmt.Errorf("no %s in this directory or at the git root; run oba kalkan upload <dir> <target>", ProjectFileName)
	}
	if len(proj.Upload) == 0 {
		return nil, "", fmt.Errorf("%s lists no upload entries", proj.Path)
	}
	bundles := make([]*Bundle, 0, len(proj.Upload))
	for _, mapping := range proj.Upload {
		target, err := client.NormalizeKalkanPagePath(mapping.To)
		if err != nil {
			return nil, "", fmt.Errorf("%s: %w", proj.Path, err)
		}
		locale := defaultLocale
		if mapping.Lang != "" {
			if locale, err = client.KalkanLocale(mapping.Lang); err != nil {
				return nil, "", fmt.Errorf("%s: %w", proj.Path, err)
			}
		}
		b, err := ReadBundle(proj.DirOf(mapping), target, locale)
		if err != nil {
			return nil, "", err
		}
		bundles = append(bundles, b)
	}
	return bundles, organization, nil
}
