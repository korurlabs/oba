package upload

import (
	"errors"
	"fmt"
	"regexp"
	"strings"

	storage "github.com/korurlabs/oba-cli/internal/dojo/attachment"
	"github.com/korurlabs/oba-cli/internal/dojo/client"
)

// assetNamePattern is the name an upload gives an asset: 12 hex digits of its SHA-256, a dash,
// its base name.
var assetNamePattern = regexp.MustCompile(`^[0-9a-f]{12}-`)

// resolveAssets returns the URL of every asset of the page, uploading the ones the page does not
// hold yet. Every page holds its own copy of a file, so deleting one page never breaks the links
// of another; a file the page links twice, or under two names with the same content, is uploaded
// once, since the server refuses content a page already holds. A dry run uploads nothing and
// leaves a missing asset's link as written.
func (r *run) resolveAssets(pageID string, page *Page) (map[string]string, error) {
	attachments, err := r.api.ListAttachments(client.StorageEntityTypeKalkanPage, pageID)
	if err != nil {
		return nil, err
	}
	urls := make(map[string]string, len(page.Assets))
	byHash := make(map[string]string, len(attachments)+len(page.Assets))
	for _, attachment := range attachments {
		link := unsigned(attachment.URL)
		urls[attachment.FileName] = link
		rememberAsset(byHash, attachment.FileName, link)
	}
	for _, asset := range page.Assets {
		if _, ok := urls[asset.Name]; ok {
			continue
		}
		if link, ok := byHash[asset.Name[:12]]; ok {
			urls[asset.Name] = link
			continue
		}
		if r.dryRun {
			continue
		}
		link, err := r.uploadAsset(pageID, asset)
		if err != nil {
			return nil, err
		}
		urls[asset.Name] = link
		rememberAsset(byHash, asset.Name, link)
	}
	return urls, nil
}

// uploadAsset attaches one asset to the page under its hashed name and returns its unsigned URL.
func (r *run) uploadAsset(pageID string, asset Asset) (string, error) {
	files, err := storage.ReadFiles([]string{asset.Source})
	if err != nil {
		return "", fmt.Errorf("read the asset %s: %w", asset.Source, err)
	}
	files[0].FileName = asset.Name
	added, err := r.api.AddAttachments(client.StorageEntityTypeKalkanPage, pageID, files)
	if err != nil {
		var apiErr *client.APIError
		if errors.As(err, &apiErr) && apiErr.Code == client.CodeDuplicateContent {
			return "", fmt.Errorf("upload %s: this Kalkan page already holds a file with the same content; link to the copy it holds instead: %w", asset.Source, err)
		}
		return "", fmt.Errorf("upload %s: %w", asset.Source, err)
	}
	if len(added) != 1 {
		return "", fmt.Errorf("upload %s: the API answered %d attachments for one file", asset.Source, len(added))
	}
	return unsigned(added[0].URL), nil
}

// rememberAsset records the URL of an attachment an upload named, under its hash prefix.
func rememberAsset(byHash map[string]string, fileName, link string) {
	if assetNamePattern.MatchString(fileName) {
		byHash[fileName[:12]] = link
	}
}

// unsigned drops the query string of an attachment URL. The API signs the URL anew on every
// answer, so a body holding the signed URL would change on every upload and never be unchanged;
// the signature is added again when the page is read.
func unsigned(link string) string {
	if i := strings.Index(link, "?"); i >= 0 {
		return link[:i]
	}
	return link
}
