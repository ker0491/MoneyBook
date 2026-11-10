import com.k.moneybook.OcrRows;

import java.util.ArrayList;
import java.util.List;

/** OCR 行列还原的回归测试（纯 JVM）。 */
public class OcrRowsTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        columnLayout();
        mixedOrder();
        emptyInput();

        System.out.println("\n通过 " + passed + " / " + (passed + failed));
        if (failed > 0) System.exit(1);
    }

    private static void columnLayout() {
        // 小票常见排版：左边商品名，右边金额，OCR 会拆成两段甚至两行
        List<OcrRows.Piece> pieces = new ArrayList<>();
        pieces.add(new OcrRows.Piece(40, 30, 220, 60, "永辉超市"));
        pieces.add(new OcrRows.Piece(40, 90, 400, 120, "1 薯片 原味 2 x 6.90"));
        pieces.add(new OcrRows.Piece(480, 90, 600, 120, "13.80"));
        pieces.add(new OcrRows.Piece(40, 150, 300, 180, "2 可乐 330ml"));
        pieces.add(new OcrRows.Piece(500, 150, 600, 180, "3.50"));
        pieces.add(new OcrRows.Piece(40, 210, 200, 240, "合计"));
        pieces.add(new OcrRows.Piece(480, 208, 600, 240, "45.80"));

        eq("列合并",
                "永辉超市\n1 薯片 原味 2 x 6.90 13.80\n2 可乐 330ml 3.50\n合计 45.80\n",
                OcrRows.build(pieces));
    }

    private static void mixedOrder() {
        List<OcrRows.Piece> pieces = new ArrayList<>();
        pieces.add(new OcrRows.Piece(480, 60, 600, 90, "9.90"));
        pieces.add(new OcrRows.Piece(40, 58, 300, 90, "抽纸 3层"));
        eq("乱序输入", "抽纸 3层 9.90\n", OcrRows.build(pieces));
    }

    private static void emptyInput() {
        eq("空输入", "", OcrRows.build(new ArrayList<>()));
    }

    private static void eq(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            fail(label + "\n实际[" + actual + "]\n期望[" + expected + "]");
        } else {
            passed++;
        }
    }

    private static void fail(String msg) {
        failed++;
        System.out.println("FAIL " + msg);
    }
}
