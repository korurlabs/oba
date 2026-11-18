package com.korur.oba.apps.kaya.render.helper;

// 1. Framework-level

// 2. Third-party

// 3. Java SDK
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 4. Internal
import com.korur.oba.apps.kaya.validation.constant.ValidationConstant;
import com.korur.oba.apps.kaya.validation.helper.PathRules;

/**
 * Finds the links a delivered Markdown file makes to other paths Kaya delivers, so a member sees
 * what a change to one file affects. Two forms count:
 *
 * - a Markdown link or reference definition, whose target resolves from the source's directory,
 *   or from the repository root when it starts with a slash, as a Markdown viewer resolves it;
 * - an inline code span whose whole content is a path, which resolves from the repository root,
 *   ./ included, because an agent reads such a path from the repository root, its working
 *   directory, and the rules cite the convention docs this way. In a convention doc, a bare
 *   Markdown file name or a path that starts with ../ that the root does not deliver resolves from
 *   the source's directory instead, as the convention index docs name their sibling docs.
 *
 * Fenced code blocks are skipped: they hold examples, not references. A fragment or query is
 * dropped, a target with a URL scheme is skipped, and only a target that
 * {@link PathRules#isAllowedPath} accepts is kept, so a link never names a file outside the
 * delivered config. A directory target ends with a slash and is kept when a Markdown file below it
 * could be delivered.
 *
 * Kept final with a private constructor to prevent instantiation or extension.
 */
public final class LinkExtractor {

    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})");
    private static final Pattern CODE_SPAN = Pattern.compile("(`+)(.+?)\\1");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("]\\(\\s*(<[^>]*>|[^\\s)]+)(?:\\s+(?:\"[^\"]*\"|'[^']*'))?\\s*\\)");
    private static final Pattern REFERENCE_DEFINITION = Pattern.compile("^ {0,3}\\[[^\\]]+]:\\s*(<[^>]*>|\\S+)");
    private static final Pattern PATH_TEXT = Pattern.compile("^[A-Za-z0-9._~@+/-]+$");
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
    private static final Pattern NESTED_AGENTS_FILE = Pattern.compile("^([A-Za-z0-9_][A-Za-z0-9._-]*/){1,4}AGENTS\\.md$");
    private static final Pattern LINE_BREAK = Pattern.compile("\r?\n");
    private static final String BYTE_ORDER_MARK = "\uFEFF";
    private static final String ANGLE_OPEN = "<";
    private static final String ANGLE_CLOSE = ">";
    private static final String CURRENT = ".";
    private static final String PARENT = "..";
    private static final String NETWORK_PREFIX = "//";
    private static final String DIRECTORY_PROBE = "x.md";
    private static final char FRAGMENT = '#';
    private static final char QUERY = '?';
    private static final char BLANK = ' ';

    private LinkExtractor() {
    }

    /**
     * Extracts the links of several delivered Markdown files.
     *
     * @param contentsByPath the content of each source, by its path
     * @return the links, by source path, line and target; a source that is not valid UTF-8 has none
     */
    public static List<LinkCandidate> extractAll(Map<String, byte[]> contentsByPath) {
        List<LinkCandidate> links = new ArrayList<>();
        contentsByPath.forEach((path, content) -> decodeUtf8(content)
                .ifPresent(text -> links.addAll(extract(path, text))));
        links.sort(Comparator.comparing(LinkCandidate::sourcePath)
                .thenComparingInt(LinkCandidate::line)
                .thenComparing(LinkCandidate::targetPath));
        return links;
    }

    /**
     * Extracts the links of one delivered Markdown file.
     *
     * @param sourcePath the repository-relative path of the file
     * @param markdown   its text
     * @return the links in the order they appear, each target once per line; a link to the file
     *         itself is left out
     */
    public static List<LinkCandidate> extract(String sourcePath, String markdown) {
        String sourceDirectory = sourcePath.contains(ValidationConstant.PATH_SEPARATOR)
                ? sourcePath.substring(0, sourcePath.lastIndexOf(ValidationConstant.PATH_SEPARATOR))
                : "";
        String text = markdown.startsWith(BYTE_ORDER_MARK) ? markdown.substring(1) : markdown;
        String[] lines = LINE_BREAK.split(text, -1);
        Set<LinkCandidate> links = new LinkedHashSet<>();
        String openFence = null;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            Matcher fence = FENCE.matcher(line);
            if (openFence != null) {
                if (fence.find() && fence.group(1).charAt(0) == openFence.charAt(0)
                        && fence.group(1).length() >= openFence.length() && line.substring(fence.end()).isBlank()) {
                    openFence = null;
                }
                continue;
            }
            if (fence.find()) {
                openFence = fence.group(1);
                continue;
            }
            int lineNumber = index + 1;
            StringBuilder withoutCode = new StringBuilder(line);
            Matcher codeSpan = CODE_SPAN.matcher(line);
            while (codeSpan.find()) {
                String content = codeSpan.group(2).strip();
                if (PATH_TEXT.matcher(content).matches()) {
                    resolveCodeSpan(sourceDirectory, content)
                            .ifPresent(target -> links.add(new LinkCandidate(sourcePath, target, lineNumber)));
                }
                for (int position = codeSpan.start(); position < codeSpan.end(); position++) {
                    withoutCode.setCharAt(position, BLANK);
                }
            }
            String prose = withoutCode.toString();
            Matcher reference = REFERENCE_DEFINITION.matcher(prose);
            if (reference.find()) {
                resolve(sourceDirectory, reference.group(1), false)
                        .ifPresent(target -> links.add(new LinkCandidate(sourcePath, target, lineNumber)));
            }
            Matcher link = MARKDOWN_LINK.matcher(prose);
            while (link.find()) {
                resolve(sourceDirectory, link.group(1), false)
                        .ifPresent(target -> links.add(new LinkCandidate(sourcePath, target, lineNumber)));
            }
        }
        return links.stream().filter(link -> !link.targetPath().equals(sourcePath)).toList();
    }

    /**
     * Whether a link target is delivered: the path itself, or for a directory, written with or
     * without the closing slash, any path below it.
     *
     * @param targetPath     the resolved target
     * @param deliveredPaths every path the scope delivers
     * @return true when the target is found among them
     */
    public static boolean isDelivered(String targetPath, NavigableSet<String> deliveredPaths) {
        if (deliveredPaths.contains(targetPath)) {
            return true;
        }
        String prefix = targetPath.endsWith(ValidationConstant.PATH_SEPARATOR)
                ? targetPath
                : targetPath + ValidationConstant.PATH_SEPARATOR;
        String next = deliveredPaths.ceiling(prefix);
        return next != null && next.startsWith(prefix);
    }

    /**
     * Whether a link is listed. Only the AGENTS.md of a pack mounted in a folder is delivered
     * there; every other AGENTS.md in a folder stays in git, so a rule that cites one names a file
     * the repository holds. Such a link is left out unless the delivered files hold its target,
     * instead of being listed as broken.
     *
     * @param targetPath the resolved target of the link
     * @param delivered  whether {@link #isDelivered} finds the target
     * @return false only for an AGENTS.md in a folder that nothing delivers
     */
    public static boolean isListed(String targetPath, boolean delivered) {
        return delivered
                || !NESTED_AGENTS_FILE.matcher(targetPath).matches()
                || targetPath.startsWith(ValidationConstant.CONVENTIONS_PREFIX);
    }

    /**
     * Resolves a code span from the repository root, and only when that names nothing Kaya
     * delivers, from the source's directory. The second try serves the convention docs, which name
     * their sibling docs by file name alone and the other convention areas by ../ paths. It is
     * limited to those two forms in a convention doc: rules and skills cite repo-root paths, so a
     * path such as .github/workflows/ or SKILL.md in them must never become a broken link beside
     * the source.
     *
     * @param sourceDirectory the directory of the source; empty for a file at the root
     * @param content         the content of the code span
     * @return the path, ending with a slash for a directory; empty when neither resolution gives a
     *         deliverable path
     */
    private static Optional<String> resolveCodeSpan(String sourceDirectory, String content) {
        Optional<String> fromRoot = resolve(sourceDirectory, content, true);
        boolean conventionDoc = (sourceDirectory + ValidationConstant.PATH_SEPARATOR).startsWith(ValidationConstant.CONVENTIONS_PREFIX);
        boolean siblingDoc = content.endsWith(ValidationConstant.MARKDOWN_SUFFIX) && !content.contains(ValidationConstant.PATH_SEPARATOR);
        boolean parentPath = content.startsWith(PARENT + ValidationConstant.PATH_SEPARATOR);
        if (fromRoot.isPresent() || !conventionDoc || !(siblingDoc || parentPath)) {
            return fromRoot;
        }
        return resolve(sourceDirectory, content, false);
    }

    /**
     * Resolves a raw target to a repository-relative path Kaya may deliver.
     *
     * @param sourceDirectory the directory of the source; empty for a file at the root
     * @param raw             the target as written
     * @param fromRoot        whether every target resolves from the repository root
     * @return the path, ending with a slash for a directory; empty when the target is a URL, leaves
     *         the repository or is not a deliverable path
     */
    private static Optional<String> resolve(String sourceDirectory, String raw, boolean fromRoot) {
        String target = raw.startsWith(ANGLE_OPEN) && raw.endsWith(ANGLE_CLOSE) ? raw.substring(1, raw.length() - 1).strip() : raw;
        int cut = indexOfAny(target, FRAGMENT, QUERY);
        if (cut >= 0) {
            target = target.substring(0, cut);
        }
        if (target.isEmpty() || target.startsWith(NETWORK_PREFIX) || SCHEME.matcher(target).find()) {
            return Optional.empty();
        }

        Deque<String> segments = new ArrayDeque<>();
        if (target.startsWith(ValidationConstant.PATH_SEPARATOR)) {
            target = target.substring(1);
        } else if (!fromRoot) {
            for (String segment : sourceDirectory.split(ValidationConstant.PATH_SEPARATOR)) {
                if (!segment.isEmpty()) {
                    segments.addLast(segment);
                }
            }
        }
        boolean directory = target.endsWith(ValidationConstant.PATH_SEPARATOR);
        for (String segment : target.split(ValidationConstant.PATH_SEPARATOR)) {
            if (segment.isEmpty() || segment.equals(CURRENT)) {
                continue;
            }
            if (segment.equals(PARENT)) {
                if (segments.isEmpty()) {
                    return Optional.empty();
                }
                segments.removeLast();
                continue;
            }
            segments.addLast(segment);
        }
        if (segments.isEmpty()) {
            return Optional.empty();
        }

        String path = String.join(ValidationConstant.PATH_SEPARATOR, segments);
        if (directory) {
            String directoryPath = path + ValidationConstant.PATH_SEPARATOR;
            return PathRules.isAllowedPath(directoryPath + DIRECTORY_PROBE) ? Optional.of(directoryPath) : Optional.empty();
        }
        return PathRules.isAllowedPath(path) ? Optional.of(path) : Optional.empty();
    }

    private static int indexOfAny(String text, char first, char second) {
        int firstIndex = text.indexOf(first);
        int secondIndex = text.indexOf(second);
        if (firstIndex < 0) {
            return secondIndex;
        }
        return secondIndex < 0 ? firstIndex : Math.min(firstIndex, secondIndex);
    }

    /**
     * Decodes strictly, so a file that is not valid UTF-8 is never read through replacement
     * characters as if it were text.
     */
    private static Optional<String> decodeUtf8(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return Optional.of(decoder.decode(ByteBuffer.wrap(content)).toString());
        } catch (CharacterCodingException e) {
            // rule-10-no-op -- reason: a Markdown file that is not text holds no links; it is listed
            // with none instead of failing the links of every other file.
            return Optional.empty();
        }
    }
}
