package sprint

import "github.com/korurlabs/oba-cli/internal/dojo/client"

// Start starts the sprint of board boardID that name names and returns the sprint it found.
func Start(c *client.Client, boardID, name string) (client.Sprint, error) {
	found, err := Find(c, boardID, name)
	if err != nil {
		return client.Sprint{}, err
	}
	return found, c.StartSprint(boardID, found.ID)
}
