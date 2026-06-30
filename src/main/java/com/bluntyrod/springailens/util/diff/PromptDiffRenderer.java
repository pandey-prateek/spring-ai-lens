package com.bluntyrod.springailens.util.diff;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a word-level diff between two prompt strings as HTML, wrapping removed words in
 * {@code <del>} and added words in {@code <ins>} using a simple longest-common-subsequence
 * algorithm. Intended for the Phase 4 side-by-side prompt diff viewer.
 */
public final class PromptDiffRenderer {

    private PromptDiffRenderer() {}

    /** Renders {@code previous} with deletions struck through and {@code current} with insertions highlighted. */
    public static String renderCurrent(String previous, String current) {
        return render(previous, current, true);
    }

    /** Renders {@code previous} with words removed in {@code current} struck through. */
    public static String renderPrevious(String previous, String current) {
        return render(previous, current, false);
    }

    private static String render(String previous, String current, boolean forCurrent) {
        String[] a = safe(previous).split("(?<=\\s)|(?=\\s)");
        String[] b = safe(current).split("(?<=\\s)|(?=\\s)");

        int n = a.length, m = b.length;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j])
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        List<String> out = new ArrayList<>();
        StringBuilder pendingTag = new StringBuilder();
        String pendingTagName = null;
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                flushPending(out, pendingTag, pendingTagName);
                pendingTag.setLength(0);
                pendingTagName = null;
                out.add(escape(a[i]));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                if (!forCurrent) {
                    pendingTagName = appendPending(out, pendingTag, pendingTagName, "del", escape(a[i]));
                }
                i++;
            } else {
                if (forCurrent) {
                    pendingTagName = appendPending(out, pendingTag, pendingTagName, "ins", escape(b[j]));
                }
                j++;
            }
        }
        while (i < n) {
            if (!forCurrent) {
                pendingTagName = appendPending(out, pendingTag, pendingTagName, "del", escape(a[i]));
            }
            i++;
        }
        while (j < m) {
            if (forCurrent) {
                pendingTagName = appendPending(out, pendingTag, pendingTagName, "ins", escape(b[j]));
            }
            j++;
        }
        flushPending(out, pendingTag, pendingTagName);

        return String.join("", out);
    }

    private static String appendPending(List<String> out, StringBuilder pendingTag, String pendingTagName,
                                         String tagName, String token) {
        if (pendingTagName != null && !pendingTagName.equals(tagName)) {
            flushPending(out, pendingTag, pendingTagName);
            pendingTag.setLength(0);
        }
        pendingTag.append(token);
        return tagName;
    }

    private static void flushPending(List<String> out, StringBuilder pendingTag, String pendingTagName) {
        if (pendingTagName != null && pendingTag.length() > 0) {
            out.add("<" + pendingTagName + ">" + pendingTag + "</" + pendingTagName + ">");
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
