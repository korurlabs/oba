// Package page reads and writes one Kalkan page by its path of slugs: the tree of the pages the
// caller can see, a page with its translations, the upsert of one language and the delete of a
// page with every page below it. Paths reach it normalized (client.NormalizeKalkanPagePath), so
// that a typo fails before the first call.
package page
