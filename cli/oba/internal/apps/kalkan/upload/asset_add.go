package upload

import (
	"fmt"
	"path/filepath"
	"strings"
)

// MaxAssetBytes mirrors the server's multipart limit of 12 MB per file and per request; a larger
// asset fails its page before the upload is tried.
const MaxAssetBytes int64 = 12 * 1024 * 1024

// Asset is a local file a page links to by a relative path. It is uploaded to the page under
// Name, the first 12 hex digits of its SHA-256 and its base name, so a changed file gets a new
// name and an unchanged one is found again on the next run.
type Asset struct {
	Source string
	Name   string
	Size   int64
}

// addAsset hashes one linked file and adds it to the page once; a file the server would refuse
// sets the page's AssetErr instead.
func (r *reader) addAsset(full string, size int64, page *Page, assetIndex map[string]int) int {
	if index, ok := assetIndex[full]; ok {
		return index
	}
	name := filepath.Base(full)
	var problem error
	switch {
	case size == 0:
		problem = fmt.Errorf("the asset %s is empty; the server refuses empty files", full)
	case size > MaxAssetBytes:
		problem = fmt.Errorf("the asset %s is larger than the 12 MB upload limit", full)
	case strings.ContainsAny(name, "\r\n\""):
		problem = fmt.Errorf("the asset %s has a quote or line break in its name, which cannot be uploaded", full)
	}
	digest := ""
	if problem == nil {
		var err error
		digest, err = hashFile(full)
		if err != nil {
			problem = err
		}
	}
	if problem != nil {
		if page.AssetErr == nil {
			page.AssetErr = problem
		}
		assetIndex[full] = -1
		return -1
	}
	page.Assets = append(page.Assets, Asset{Source: full, Name: digest[:12] + "-" + name, Size: size})
	assetIndex[full] = len(page.Assets) - 1
	return len(page.Assets) - 1
}
