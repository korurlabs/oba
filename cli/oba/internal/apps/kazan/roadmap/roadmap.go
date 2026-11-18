// Package roadmap reads the roadmap of a Kazan board and draws it as plain text: a timeline of
// month ticks and one lane per board, with one row per milestone.
package roadmap

const (
	// DefaultWidth is the width drawn for when stdout is not a terminal.
	DefaultWidth    = 80
	minChartColumns = 20
	maxChartColumns = 200
	// textReserve is the width kept free to the right of the chart for the milestone name,
	// progress, health and target date.
	textReserve = 36
	indent      = "  "
)
