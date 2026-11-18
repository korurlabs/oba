package client

import "net/url"

// kazanPath is the root of every Kazan endpoint, so this one constant is the only place the CLI
// spells it.
const kazanPath = "/kazan"

// boardsPath is the collection of the boards of the organization.
const boardsPath = kazanPath + "/boards"

func boardPath(id string) string {
	return boardsPath + "/" + url.PathEscape(id)
}
