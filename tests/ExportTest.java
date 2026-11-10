import com.k.moneybook.Export;
import com.k.moneybook.Record;

import java.util.ArrayList;
import java.util.List;

/** 导出文本的回归测试：CSV 转义、金额格式、JSON 字段完整。 */
public class ExportTest {

    private static int failed = 0;
    private static int passed = 0;

    public static void main(String[] args) {
        Record r = new Record();
        r.id = 1;
        r.type = Record.EXPENSE;
        r.amount = 1200;
        r.scene = "买菜";
        r.sub = "水果";
        r.item = "苹果";
        r.qty = 2.5;
        r.unit = "斤";
        r.place = "西安";
        r.merchant = "永辉超市";
        r.note = "旅行测试";
        r.date = "2026-11-02";
        r.time = "18:20";
        r.createdAt = 1793587486000L;

        List<Record> list = new ArrayList<>();
        list.add(r);

        String csv = Export.csv(list);
        check("CSV 带 BOM", csv.startsWith("\ufeff"));
        check("CSV 表头", csv.contains("日期,时间,类型,大类,小类,商品,金额(元),数量,单位,地点,商家,备注,录入时间"));
        check("CSV 内容", csv.contains(
                "2026-11-02,18:20,支出,买菜,水果,苹果,12.00,2.5,斤,西安,永辉超市,旅行测试,"));
        check("CSV 行数", csv.split("\r\n").length == 2);

        Record tricky = new Record();
        tricky.type = Record.INCOME;
        tricky.amount = 500;
        tricky.scene = "其他";
        tricky.note = "带,逗号 和\"引号\"\n换行";
        tricky.date = "2026-11-01";
        List<Record> one = new ArrayList<>();
        one.add(tricky);
        String trickyCsv = Export.csv(one);
        check("CSV 逗号加引号", trickyCsv.contains("\"带,逗号 和\"\"引号\"\""));
        check("CSV 换行加引号", trickyCsv.contains("\n换行\""));

        String json = Export.json(list);
        check("JSON 数量", json.contains("\"count\": 1"));
        check("JSON 大类", json.contains("\"major\": \"买菜\""));
        check("JSON 小类", json.contains("\"sub\": \"水果\""));
        check("JSON 地点", json.contains("\"place\": \"西安\""));
        check("JSON 金额是数字", json.contains("\"amount_cents\": 1200"));
        check("JSON 数量是数字", json.contains("\"qty\": 2.5"));
        check("JSON 中文不转义", json.contains("苹果"));

        List<Record> empty = new ArrayList<>();
        check("空 CSV 只有表头", Export.csv(empty).split("\r\n").length == 1);
        check("空 JSON", Export.json(empty).contains("\"count\": 0"));

        check("金额两位小数", "12.00".equals(Export.yuan(1200)));
        check("文件后缀 CSV", Export.fileName(true).endsWith(".csv"));
        check("文件后缀 JSON", Export.fileName(false).endsWith(".json"));

        System.out.println("ExportTest: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
        } else {
            failed++;
            System.out.println("FAIL " + name);
        }
    }
}
