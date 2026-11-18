// Package label lists, creates, changes and deletes the labels of a Kazan board.
package label

import (
	"github.com/korurlabs/oba-cli/internal/dojo/guardrail"
)

func orDash(value *string) string {
	if value == nil {
		return "-"
	}
	cleaned := guardrail.OneLine(*value)
	if cleaned == "" {
		return "-"
	}
	return cleaned
}
