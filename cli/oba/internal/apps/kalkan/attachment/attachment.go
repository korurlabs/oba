// Package attachment holds what is Kalkan's own about the evidence files of a page; the storage
// calls themselves are in internal/dojo/attachment. A page is named by its path of slugs, which
// reaches this package normalized (client.NormalizeKalkanPagePath), and is found in the tree of
// pages the caller can see; the API then checks the caller's level on the page's root.
package attachment
