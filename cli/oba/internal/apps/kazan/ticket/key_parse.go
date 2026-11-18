package ticket

import (
	"fmt"
	"strings"
)

// ParseKey splits a ticket key at its last "-" into the board prefix and the ticket number,
// which must be digits only.
func ParseKey(key string) (prefix, number string, err error) {
	idx := strings.LastIndex(key, "-")
	if idx <= 0 || idx == len(key)-1 {
		return "", "", invalidKey(key)
	}
	prefix, number = key[:idx], key[idx+1:]
	for _, r := range number {
		if r < '0' || r > '9' {
			return "", "", invalidKey(key)
		}
	}
	return prefix, number, nil
}

func invalidKey(key string) error {
	return fmt.Errorf("invalid ticket %q: use a key such as KAGI-40 or a ticket id", key)
}
