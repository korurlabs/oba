package session

import "errors"

// CurrentUserID reads the id of the signed-in user from GET /users/me, for a filter or a role
// that names the caller as "me".
func CurrentUserID() (string, error) {
	_, user, err := CurrentUser()
	if err != nil {
		return "", err
	}
	if user.ID == "" {
		return "", errors.New("the API named no id for the signed-in user")
	}
	return user.ID, nil
}
