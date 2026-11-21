package seed

import (
	"regexp"
	"strings"
)

// The forms the server's RemoteUrlHelper accepts: a URL remote (https, http, ssh, git) and the
// scp-like user@host:owner/name. Group 1 is the host, group 2 the path.
var (
	normalURLRemote = regexp.MustCompile(`^(?i)(?:https?|ssh|git)://(?:[^@/\s]*@)?([^/:@\s]+)(?::\d+)?(/[^?#\s]*)?(?:[?#]\S*)?$`)
	normalSCPRemote = regexp.MustCompile(`^(?:[^@/\s]+@)?([^:/@\s]+):(\S+)$`)
)

// NormalizeRemote gives the remote as the server stores it and signs it into a manifest:
// host/owner/name in lower case, without user, port and ".git". It answers "" for a remote the
// server would refuse, so a manifest can never match it. The seed matches its repositories to
// the server's by it, and delivery matches a checkout to its manifest.
func NormalizeRemote(remote string) string {
	trimmed := strings.TrimSpace(remote)
	var match []string
	if strings.Contains(trimmed, "://") {
		match = normalURLRemote.FindStringSubmatch(trimmed)
	} else {
		match = normalSCPRemote.FindStringSubmatch(trimmed)
		if match != nil && strings.HasPrefix(match[2], "//") {
			match = nil
		}
	}
	if match == nil {
		return ""
	}
	path := strings.Trim(strings.ToLower(match[2]), "/")
	if strings.HasSuffix(path, ".git") {
		path = strings.Trim(strings.TrimSuffix(path, ".git"), "/")
	}
	segments := strings.Split(path, "/")
	if len(segments) < 2 {
		return ""
	}
	for _, segment := range segments {
		if segment == "" || segment == "." || segment == ".." {
			return ""
		}
	}
	normalized := strings.ToLower(match[1]) + "/" + path
	if len(normalized) > 500 {
		return ""
	}
	return normalized
}
