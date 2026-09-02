package cn.net.pap.common.file.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code .gitignore} 匹配器（零依赖），支持嵌套：每个目录下的 {@code .gitignore} 只作用于该目录及以下，
 * 由外层到内层按序求值、后匹配生效（最近目录优先级最高，符合 git 语义）。
 * <p>
 * 支持：{@code #} 注释、{@code !} 取反、末尾 {@code /} 目录限定、{@code *} / {@code **} / {@code ?} 通配。
 * 不支持（简化取舍，在 {@link FileSearch} 默认不启用）：字符类 {@code []}、反斜杠转义等完整 git 语义。
 */
final class GitIgnoreMatcher {

    private static final Logger log = LoggerFactory.getLogger(GitIgnoreMatcher.class);

    private final Path root;
    private final List<Rule> rules;

    private GitIgnoreMatcher(Path root, List<Rule> rules) {
        this.root = root;
        this.rules = rules;
    }

    /** 加载根目录下的 {@code .gitignore}；不存在则返回仅含空规则集合的匹配器。 */
    static GitIgnoreMatcher load(Path root) {
        return new GitIgnoreMatcher(root, new ArrayList<>()).extend(root);
    }

    /** 在现有规则后追加 {@code dir} 目录下的 {@code .gitignore}（如有），返回新匹配器（不改原实例）。 */
    GitIgnoreMatcher extend(Path dir) {
        List<Rule> all = new ArrayList<>(rules.size() + 4);
        all.addAll(rules);
        Path ignoreFile = dir.resolve(".gitignore");
        if (Files.isRegularFile(ignoreFile)) {
            try {
                for (String raw : Files.readAllLines(ignoreFile, StandardCharsets.UTF_8)) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    boolean negated = line.startsWith("!");
                    if (negated) {
                        line = line.substring(1);
                    }
                    if (line.isEmpty()) {
                        continue;
                    }
                    Rule rule = compile(dir, line, negated);
                    if (rule != null) {
                        all.add(rule);
                    }
                }
            } catch (IOException e) {
                log.error("读取 .gitignore 失败: {}", ignoreFile, e);
            }
        }
        return new GitIgnoreMatcher(root, all);
    }

    /**
     * 判断相对路径是否被忽略（git 语义：外层→内层规则按序求值，最后一条匹配的规则生效）。
     *
     * @param relative 相对根目录的路径
     * @param isDir    该路径是否为目录（用于目录限定规则）
     */
    boolean ignores(Path relative, boolean isDir) {
        Path abs = root.resolve(relative);
        boolean ignored = false;
        for (Rule rule : rules) {
            if (!abs.startsWith(rule.base)) {
                continue;
            }
            String relToBase = rule.base.relativize(abs).toString().replace('\\', '/');
            if (rule.matches(relToBase, isDir)) {
                ignored = !rule.negated;
            }
        }
        return ignored;
    }

    /**
     * 供无状态场景（{@link FileSearch#stream}）使用：从 root 到 rel 的父目录沿途加载各层
     * {@code .gitignore} 后判断是否忽略。
     */
    static boolean isIgnored(Path root, Path relative, boolean isDir) {
        GitIgnoreMatcher matcher = load(root);
        Path abs = root.resolve(relative);
        Path dir = isDir ? abs : abs.getParent();
        if (dir == null || dir.equals(root)) {
            return matcher.ignores(relative, isDir);
        }
        Path walk = root;
        for (Path segment : root.relativize(dir)) {
            walk = walk.resolve(segment);
            matcher = matcher.extend(walk);
        }
        return matcher.ignores(relative, isDir);
    }

    private static Rule compile(Path base, String line, boolean negated) {
        boolean dirOnly = line.endsWith("/");
        if (dirOnly) {
            line = line.substring(0, line.length() - 1);
        }
        boolean anchored = line.startsWith("/");
        if (anchored) {
            line = line.substring(1);
        }
        if (line.isEmpty()) {
            return null;
        }
        StringBuilder regex = new StringBuilder("^");
        // 无 '/' 的模式匹配任意层级下的同名条目（git 语义），例如 .git 匹配任意目录下的 .git
        boolean noSlash = !line.contains("/");
        if (noSlash && !anchored) {
            regex.append("(?:.*/)?");
        }
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '*') {
                if (i + 1 < line.length() && line.charAt(i + 1) == '*') {
                    if (i + 2 < line.length() && line.charAt(i + 2) == '/') {
                        // '**/'：匹配零个或多个目录层级
                        regex.append("(?:.*/)?");
                        i += 2;
                    } else {
                        regex.append(".*");
                        i++;
                    }
                } else {
                    regex.append("[^/]*");
                }
            } else if (c == '?') {
                regex.append("[^/]");
            } else if (c == '/' || c == '-') {
                regex.append(c);
            } else if ("\\.[]{}()+^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append('$');
        return new Rule(base, negated, dirOnly, Pattern.compile(regex.toString()));
    }

    private static final class Rule {

        private final Path base;
        private final boolean negated;
        private final boolean dirOnly;
        private final Pattern pattern;

        Rule(Path base, boolean negated, boolean dirOnly, Pattern pattern) {
            this.base = base;
            this.negated = negated;
            this.dirOnly = dirOnly;
            this.pattern = pattern;
        }

        boolean matches(String relToBase, boolean isDir) {
            if (dirOnly && !isDir) {
                return false;
            }
            Matcher matcher = pattern.matcher(relToBase);
            return matcher.matches();
        }
    }
}
