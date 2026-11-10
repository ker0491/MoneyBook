package com.k.moneybook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OCR 返回的是「文本块/行」，小票这种多列排版会被拆成好几段。
 * 这里按纵坐标把同一行的片段合并回一行，再按从左到右排序。
 */
public class OcrRows {

    public static class Piece {
        public final int left;
        public final int top;
        public final int right;
        public final int bottom;
        public final String text;

        public Piece(int left, int top, int right, int bottom, String text) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.text = text == null ? "" : text;
        }

        public int height() {
            return Math.max(1, bottom - top);
        }
    }

    public static String build(List<Piece> pieces) {
        if (pieces == null || pieces.isEmpty()) return "";

        List<Piece> sorted = new ArrayList<>(pieces);
        Collections.sort(sorted, (a, b) -> Integer.compare(a.top, b.top));

        List<List<Piece>> rows = new ArrayList<>();
        for (Piece p : sorted) {
            List<Piece> target = null;
            for (List<Piece> row : rows) {
                Piece rep = row.get(0);
                int overlap = Math.min(rep.bottom, p.bottom) - Math.max(rep.top, p.top);
                int minHeight = Math.min(rep.height(), p.height());
                if (overlap > minHeight * 0.5) {
                    target = row;
                    break;
                }
            }
            if (target == null) {
                target = new ArrayList<>();
                rows.add(target);
            }
            target.add(p);
        }

        Collections.sort(rows, (a, b) -> Integer.compare(a.get(0).top, b.get(0).top));

        StringBuilder out = new StringBuilder();
        for (List<Piece> row : rows) {
            Collections.sort(row, (a, b) -> Integer.compare(a.left, b.left));
            StringBuilder line = new StringBuilder();
            for (Piece p : row) {
                String text = p.text.trim();
                if (text.isEmpty()) continue;
                if (line.length() > 0) line.append(' ');
                line.append(text);
            }
            if (line.length() > 0) out.append(line).append('\n');
        }
        return out.toString();
    }
}
