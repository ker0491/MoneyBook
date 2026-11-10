package com.k.moneybook;

import java.util.List;

/**
 * 商家名纠正：
 *   OCR 第一次认错，用户手动改对后记住「认错的名字 -> 正确的名字」，
 *   下次同一张小票/同一家店再认错时自动换成正确名字。
 *
 * 匹配规则故意保守：
 *   - 去掉空格、标点、括号里的分店名、电话号码后再精确比较；
 *   - 只有 4 个字以上的名字才做模糊匹配，且只允许很小的差异；
 *   - 短名字（超市、便利店）只认精确结果，避免把不同店改混。
 *
 * 纯 Java，方便在 JVM 上跑测试。
 */
public final class Merchants {

    private Merchants() {
    }

    /** 用于比较和存储的键：去掉空格、标点、分店括号、电话 */
    public static String normalize(String s) {
        if (s == null) return "";
        String t = s;

        // 全角转半角
        StringBuilder half = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '！' && c <= '～') c = (char) (c - 0xFEE0);
            half.append(c);
        }
        t = half.toString();

        // 分店名/备注括号通常不影响是不是同一家店
        t = t.replaceAll("[（(【\\[].*?[）)】\\]]", " ");
        // 电话号码、手机号
        t = t.replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", " ");
        t = t.replaceAll("(?<!\\d)0\\d{2,3}[- ]?\\d{7,8}(?!\\d)", " ");
        // 开头的字段名
        t = t.replaceAll("^(商家|店名|名称|商户)[:：\\s]*", "");

        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isWhitespace(c)) continue;
            if (isPunctuation(c)) continue;
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static boolean isPunctuation(char c) {
        return "，。、；：？！“”‘’（）【】《》〈〉·—–-_/\\|@#￥$%^&*+=~`\"';:,.?!()[]{}<>".indexOf(c) >= 0;
    }

    /**
     * 从记忆里找正确名字；找不到返回原值。
     *
     * @param aliases 每项 {@code {normRaw, fixed}}
     */
    public static String correct(String raw, List<String[]> aliases) {
        if (raw == null || raw.trim().isEmpty()) return raw;
        if (aliases == null || aliases.isEmpty()) return raw;

        String key = normalize(raw);
        if (key.isEmpty()) return raw;

        // 1. 精确匹配最安全
        for (String[] a : aliases) {
            if (a == null || a.length < 2) continue;
            if (key.equals(a[0])) return a[1];
        }

        // 2. 长名字才做模糊匹配；短名字错一个字就可能是另一家店
        if (key.length() < 4) return raw;

        String best = null;
        double bestScore = 0;
        double secondScore = 0;
        for (String[] a : aliases) {
            if (a == null || a.length < 2) continue;
            String known = a[0];
            if (known == null || known.length() < 4) continue;
            if (!similar(key, known)) continue;

            double score = similarity(key, known);
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = a[1];
            } else if (score > secondScore) {
                secondScore = score;
            }
        }

        // 只有明显胜出的唯一候选才替换，避免猜错
        if (best != null && bestScore >= 0.75
                && (secondScore == 0 || bestScore - secondScore >= 0.05)) {
            return best;
        }
        return raw;
    }

    static boolean similar(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        if (a.length() < 4 || b.length() < 4) return false;
        if (a.contains(b) || b.contains(a)) return true;
        return similarity(a, b) >= 0.75;
    }

    static double similarity(String a, String b) {
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 1;
        return 1.0 - distance(a, b) / (double) max;
    }

    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }
        return prev[b.length()];
    }
}
