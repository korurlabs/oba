package com.korur.oba.apps.kaya.repository.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.dojo.constant.CommonConstant;
import com.korur.oba.apps.kaya.repository.constant.RepositoryConstant;

/**
 * Normalises a git remote to the form repositories are stored in: host/owner/name in lower case,
 * without scheme, credentials, port or .git suffix.
 *
 * Every developer's clone of a repository may name its remote differently (HTTPS or SSH, with or
 * without .git), and all of them must match one repository row. Credentials are dropped so that a
 * token embedded in a remote URL is never stored.
 */
public final class RemoteUrlHelper {

    private static final Pattern URL_FORM = Pattern.compile(RepositoryConstant.REMOTE_URL_FORM_PATTERN);
    private static final Pattern SCP_FORM = Pattern.compile(RepositoryConstant.REMOTE_SCP_FORM_PATTERN);

    private RemoteUrlHelper() {
        throw new UnsupportedOperationException(CommonConstant.UTILITY_CLASS_MESSAGE);
    }

    /**
     * Normalises a git remote.
     *
     * Accepted forms: https://host/owner/name(.git), http://, ssh://[user@]host[:port]/owner/name,
     * git://, and the scp-like user@host:owner/name(.git). The path needs at least two segments
     * and no "." or ".." segment.
     *
     * Example: git@GitHub.com:korurlabs/Village.git becomes github.com/korurlabs/village.
     *
     * @param remoteUrl the remote as the recorder reported it, may be null
     * @return the normalised remote, or null when it is null, has none of the accepted forms or is
     *         longer than 500 characters
     */
    public static String normalize(String remoteUrl) {
        if (remoteUrl == null) {
            return null;
        }
        String trimmed = remoteUrl.strip();

        Matcher matcher = trimmed.contains(RepositoryConstant.SCHEME_SEPARATOR)
                ? URL_FORM.matcher(trimmed)
                : SCP_FORM.matcher(trimmed);
        if (!matcher.matches()) {
            return null;
        }

        String host = matcher.group(1).toLowerCase(Locale.ROOT);
        String path = normalizePath(matcher.group(2));
        if (path == null) {
            return null;
        }

        String normalized = host + RepositoryConstant.PATH_SEPARATOR + path;
        return normalized.length() > RepositoryConstant.REMOTE_URL_MAX_LENGTH ? null : normalized;
    }

    /**
     * Returns the display name a newly connected repository gets: the given name, trimmed and cut
     * to 100 characters, or the last path segment of the normalised remote when none is given.
     *
     * @param name                the name the recorder reported, may be null or blank
     * @param normalizedRemoteUrl a remote returned by normalize
     * @return the display name
     */
    public static String displayName(String name, String normalizedRemoteUrl) {
        String candidate = name == null ? "" : name.strip();
        if (candidate.isEmpty()) {
            candidate = normalizedRemoteUrl.substring(
                    normalizedRemoteUrl.lastIndexOf(RepositoryConstant.PATH_SEPARATOR) + 1);
        }
        return candidate.length() > RepositoryConstant.NAME_MAX_LENGTH
                ? candidate.substring(0, RepositoryConstant.NAME_MAX_LENGTH)
                : candidate;
    }

    private static String normalizePath(String rawPath) {
        if (rawPath == null) {
            return null;
        }
        String path = stripSeparators(rawPath.toLowerCase(Locale.ROOT));
        if (path.endsWith(RepositoryConstant.GIT_SUFFIX)) {
            path = stripSeparators(path.substring(0, path.length() - RepositoryConstant.GIT_SUFFIX.length()));
        }

        String[] segments = path.split(RepositoryConstant.PATH_SEPARATOR, -1);
        if (segments.length < RepositoryConstant.REMOTE_URL_MIN_PATH_SEGMENTS) {
            return null;
        }
        for (String segment : segments) {
            if (segment.isEmpty()
                    || segment.equals(RepositoryConstant.CURRENT_SEGMENT)
                    || segment.equals(RepositoryConstant.PARENT_SEGMENT)) {
                return null;
            }
        }
        return path;
    }

    private static String stripSeparators(String path) {
        int start = 0;
        int end = path.length();
        while (start < end && path.charAt(start) == RepositoryConstant.PATH_SEPARATOR_CHAR) {
            start++;
        }
        while (end > start && path.charAt(end - 1) == RepositoryConstant.PATH_SEPARATOR_CHAR) {
            end--;
        }
        return path.substring(start, end);
    }
}
