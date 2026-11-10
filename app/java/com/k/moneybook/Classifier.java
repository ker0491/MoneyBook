package com.k.moneybook;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 自动归类：把「商家 + 商品 + 备注」映射到「大类 + 小类」。
 *
 * 判定顺序（前面的优先）：
 *   1. 用户改过的商品记忆（item_rule：这个商品归到哪个大类/小类）
 *   2. 关键词：在商品/商家/备注里找最长命中的词
 *      - 每个关键词同时带着自己的大类和小类，所以不会出现「小类跑出大类」的情况
 *      - 最长词优先，处理「苹果手机」「充电宝」「请客吃饭」这类歧义
 *   3. 兜底「其他」，小类留空
 *
 * 纯 Java，不依赖 Android，方便在 JVM 上跑回归测试。
 */
public class Classifier {

    /** 商品记忆：某个商品固定归到 scene/sub */
    public static class Rule {
        public final String scene;
        public final String sub;

        public Rule(String scene, String sub) {
            this.scene = scene;
            this.sub = sub;
        }
    }

    /** 被用户删掉的大类：不再参与归类，命中就落到「其他」 */
    private static final Set<String> HIDDEN_EXPENSE = new HashSet<>();
    private static final Set<String> HIDDEN_INCOME = new HashSet<>();
    /** 被用户删掉的小类：大类保留，小类留空 */
    private static final Map<String, Set<String>> HIDDEN_SUBS = new LinkedHashMap<>();

    public static synchronized void setHidden(int type, Collection<String> names) {
        Set<String> target = type == Record.INCOME ? HIDDEN_INCOME : HIDDEN_EXPENSE;
        target.clear();
        if (names != null) target.addAll(names);
    }

    public static synchronized boolean isHidden(String scene, int type) {
        if (scene == null) return false;
        Set<String> set = type == Record.INCOME ? HIDDEN_INCOME : HIDDEN_EXPENSE;
        return set.contains(scene);
    }

    /** 删掉的小类：scene -> 小类名集合 */
    public static synchronized void setHiddenSubs(Map<String, Set<String>> hidden) {
        HIDDEN_SUBS.clear();
        if (hidden == null) return;
        for (Map.Entry<String, Set<String>> e : hidden.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            HIDDEN_SUBS.put(e.getKey(), new HashSet<>(e.getValue()));
        }
    }

    public static synchronized boolean isHiddenSub(String scene, String sub) {
        if (scene == null || sub == null || sub.trim().isEmpty()) return false;
        Set<String> set = HIDDEN_SUBS.get(scene);
        return set != null && set.contains(sub.trim());
    }

    /** 归类结果 + 为什么（用来在界面上告诉你判定的依据） */
    public static class Verdict {
        public final String scene;
        public final String sub;
        public final String kind;   // memory / keyword / fallback / deleted
        public final String word;

        Verdict(String scene, String sub, String kind, String word) {
            this.scene = scene;
            this.sub = sub;
            this.kind = kind;
            this.word = word;
        }

        /** 「按商品：熟食」「你改过：薯片」 */
        public String label() {
            switch (kind) {
                case "memory":
                    return "你改过：" + word;
                case "keyword":
                    return "按商品：" + word;
                case "deleted":
                    return "「" + word + "」归类已删除";
                default:
                    return "没认出来，点一下改";
            }
        }

        public String title() {
            return sub == null || sub.trim().isEmpty() ? scene : scene + "/" + sub;
        }
    }

    public static String scene(String merchant, String item, String note) {
        return scene(merchant, item, note, null);
    }

    public static String scene(String merchant, String item, String note,
                               Map<String, Rule> memory) {
        return explain(merchant, item, note, memory).scene;
    }

    public static String sub(String merchant, String item, String note,
                             Map<String, Rule> memory) {
        return explain(merchant, item, note, memory).sub;
    }

    public static Verdict explain(String merchant, String item, String note,
                                  Map<String, Rule> memory) {
        String m = clean(merchant);
        String it = clean(item);
        String nt = clean(note);

        // 1. 用户改过的：商品名 / 备注精确命中
        if (memory != null && !memory.isEmpty()) {
            for (String key : new String[]{it, nt}) {
                if (key == null) continue;
                Rule hit = memory.get(key);
                if (hit == null) continue;
                if (!Scenes.isScene(hit.scene, Record.EXPENSE)) continue;
                String sub = hit.sub == null || hit.sub.trim().isEmpty()
                        ? Subs.guess(hit.scene, key, key) : hit.sub.trim();
                return guard(new Verdict(hit.scene, sub, "memory", key));
            }
        }

        // 2. 关键词：商品 / 商家 / 备注一起看，最长词优先
        String haystack = join(m, it, nt);
        if (haystack == null) return new Verdict("其他", null, "fallback", null);

        Subs.Rule best = null;
        for (Subs.Rule r : Subs.rules()) {
            if (!haystack.contains(r.word)) continue;
            if (best == null
                    || r.word.length() > best.word.length()
                    || (r.word.length() == best.word.length() && best.sub == null)) {
                best = r;
            }
        }
        if (best == null) return new Verdict("其他", null, "fallback", null);
        return guard(new Verdict(best.scene, best.sub, "keyword", best.word));
    }

    /** 命中的大类/小类被用户删掉了：大类没了退回「其他」，小类没了只清空小类 */
    private static Verdict guard(Verdict v) {
        if (isHidden(v.scene, Record.EXPENSE)) {
            return new Verdict("其他", null, "deleted", v.scene);
        }
        if (isHiddenSub(v.scene, v.sub)) {
            return new Verdict(v.scene, null, v.kind, v.word);
        }
        return v;
    }

    /**
     * 只有「在店里买东西」这类大类，才适合用「上次去过的地点」兜底。
     * 住 / 行 / 人际说「上次的永辉超市」明显不对。
     */
    public static boolean usesStoreFallback(String scene) {
        if (scene == null) return true;
        switch (scene) {
            case "住":
            case "行":
            case "人际":
                return false;
            default:
                return true;
        }
    }

    /** 收入场景：工资 / 奖金 / 理财 / 兼职 / 红包 / 其他 */
    public static String incomeScene(String text) {
        String s = clean(text);
        if (s == null) return "其他";
        String best = "其他";
        int bestLen = 0;
        for (Scenes.Def d : Scenes.INCOME) {
            for (String word : d.words) {
                if (word.length() > bestLen && s.contains(word)) {
                    bestLen = word.length();
                    best = d.name;
                }
            }
        }
        if (isHidden(best, Record.INCOME)) return "其他";
        return best;
    }

    /** 「30度」「2个」这类量词，说明这是数量而不是金额 */
    public static String unitOf(String word) {
        if (word == null) return null;
        switch (word) {
            case "度": case "个": case "件": case "瓶": case "包": case "袋":
            case "斤": case "盒": case "箱": case "杯": case "碗": case "份":
            case "张": case "条": case "双": case "罐": case "桶": case "只":
                return "个".equals(word) || "件".equals(word) ? null : word;
            default:
                return null;
        }
    }

    private static String join(String a, String b, String c) {
        StringBuilder sb = new StringBuilder();
        if (a != null) sb.append(a);
        if (b != null) sb.append(' ').append(b);
        if (c != null) sb.append(' ').append(c);
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String clean(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 便于测试：空记忆 */
    public static Map<String, Rule> emptyMemory() {
        return new LinkedHashMap<>();
    }
}
