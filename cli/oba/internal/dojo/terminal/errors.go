package terminal

import "errors"

// ErrNothingToUpdate is printed as it is, so it keeps the capital and the full stop.
var ErrNothingToUpdate = errors.New("Nothing to update.") //nolint:staticcheck // ST1005: shown to the user verbatim

// ErrDeleteNeedsYes is printed as it is, so it keeps the capital and the full stop.
var ErrDeleteNeedsYes = errors.New("Pass --yes to delete without a prompt.") //nolint:staticcheck // ST1005: shown to the user verbatim
