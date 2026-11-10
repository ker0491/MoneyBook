package com.k.moneybook;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DB extends SQLiteOpenHelper {

    private static final String NAME = "moneybook.db";
    private static final int VERSION = 8;

    private static DB instance;

    public static synchronized DB get(Context ctx) {
        if (instance == null) {
            instance = new DB(ctx.getApplicationContext());
        }
        return instance;
    }

    private DB(Context ctx) {
        super(ctx, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type INTEGER NOT NULL," +
                "amount INTEGER NOT NULL," +
                "scene TEXT NOT NULL," +
                "icon TEXT NOT NULL," +
                "note TEXT," +
                "date TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "merchant TEXT," +
                "item TEXT," +
                "qty REAL NOT NULL DEFAULT 1," +
                "unit TEXT," +
                "time TEXT," +
                "place TEXT," +
                "sub TEXT)");
        db.execSQL("CREATE INDEX idx_records_date ON records(date)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_place ON records(place)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_scene ON records(scene)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_sub ON records(sub)");
        db.execSQL("CREATE TABLE categories(" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "type INTEGER NOT NULL," +
                "name TEXT NOT NULL," +
                "icon TEXT NOT NULL," +
                "sort INTEGER NOT NULL DEFAULT 0)");
        db.execSQL(ITEM_RULE_SQL);
        db.execSQL(PLACE_SQL);
        db.execSQL(SUB_SQL);
        db.execSQL(SUB_INDEX_SQL);
        db.execSQL(SPOT_SQL);
        db.execSQL(MERCHANT_ALIAS_SQL);
        seed(db);
    }

    private static final String SUB_SQL =
            "CREATE TABLE IF NOT EXISTS subs(" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "scene TEXT NOT NULL," +
                    "name TEXT NOT NULL," +
                    "builtin INTEGER NOT NULL DEFAULT 0," +
                    "hidden INTEGER NOT NULL DEFAULT 0," +
                    "sort INTEGER NOT NULL DEFAULT 0)";

    private static final String SUB_INDEX_SQL =
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_subs_scene_name ON subs(scene, name)";

    private static final String PLACE_SQL =
            "CREATE TABLE IF NOT EXISTS place(" +
                    "lat REAL NOT NULL," +
                    "lon REAL NOT NULL," +
                    "name TEXT NOT NULL," +
                    "updated_at INTEGER NOT NULL)";

    /** 用户自己维护的「我的地点」；同名地点直接更新坐标 */
    private static final String SPOT_SQL =
            "CREATE TABLE IF NOT EXISTS spots(" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "name TEXT NOT NULL UNIQUE," +
                    "lat REAL NOT NULL," +
                    "lon REAL NOT NULL," +
                    "radius INTEGER NOT NULL DEFAULT 10000," +
                    "updated_at INTEGER NOT NULL)";

    /** OCR 商家名纠正：raw 是标准化后的识别结果，fixed 是用户改对的名字 */
    private static final String MERCHANT_ALIAS_SQL =
            "CREATE TABLE IF NOT EXISTS merchant_alias(" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "raw TEXT NOT NULL UNIQUE," +
                    "fixed TEXT NOT NULL," +
                    "hits INTEGER NOT NULL DEFAULT 1," +
                    "updated_at INTEGER NOT NULL)";

    private static final String ITEM_RULE_SQL =
            "CREATE TABLE IF NOT EXISTS item_rule(" +
                    "item TEXT PRIMARY KEY," +
                    "scene TEXT NOT NULL," +
                    "sub TEXT," +
                    "hits INTEGER NOT NULL DEFAULT 1," +
                    "updated_at INTEGER NOT NULL)";

    private void seed(SQLiteDatabase db) {
        insertScenes(db, Record.EXPENSE, Scenes.expense());
        insertScenes(db, Record.INCOME, Scenes.income());
        seedSubs(db);
    }

    /** 把小类表按内置清单补齐；已有的（包括被删掉的）不动 */
    private void seedSubs(SQLiteDatabase db) {
        for (Scenes.Def def : Scenes.expense()) {
            String[] subs = Subs.subsFor(def.name);
            for (int i = 0; i < subs.length; i++) {
                ContentValues v = new ContentValues();
                v.put("scene", def.name);
                v.put("name", subs[i]);
                v.put("builtin", 1);
                v.put("hidden", 0);
                v.put("sort", i);
                db.insertWithOnConflict("subs", null, v, SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }

    private void insertScenes(SQLiteDatabase db, int type, Scenes.Def[] defs) {
        for (int i = 0; i < defs.length; i++) {
            insertCategory(db, type, defs[i].name, defs[i].icon, i);
        }
    }

    private void insertCategory(SQLiteDatabase db, int type, String name, String icon, int sort) {
        ContentValues v = new ContentValues();
        v.put("type", type);
        v.put("name", name);
        v.put("icon", icon);
        v.put("sort", sort);
        db.insert("categories", null, v);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // v1 -> v2：地点、商品、数量
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE records ADD COLUMN merchant TEXT");
            db.execSQL("ALTER TABLE records ADD COLUMN item TEXT");
            db.execSQL("ALTER TABLE records ADD COLUMN qty INTEGER NOT NULL DEFAULT 1");
        }
        // v2 -> v3：分类升级为场景，新增单位列和「改过的归类」记忆表
        if (oldVersion < 3) {
            // records 表重建：老库的 category 是 NOT NULL 且没有默认值，直接改列会写不进去，
            // 所以整表搬到新结构（顺便把老分类名翻成场景名）
            db.execSQL("CREATE TABLE records_v3(" +
                    "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "type INTEGER NOT NULL," +
                    "amount INTEGER NOT NULL," +
                    "scene TEXT NOT NULL," +
                    "icon TEXT NOT NULL," +
                    "note TEXT," +
                    "date TEXT NOT NULL," +
                    "created_at INTEGER NOT NULL," +
                    "merchant TEXT," +
                    "item TEXT," +
                    "qty REAL NOT NULL DEFAULT 1," +
                    "unit TEXT)");
            db.execSQL("INSERT INTO records_v3" +
                    "(_id, type, amount, scene, icon, note, date, created_at, merchant, item, qty, unit) " +
                    "SELECT _id, type, amount, " +
                    "CASE WHEN category IS NULL OR category = '' THEN '其他' " +
                    "     WHEN category = '餐饮' THEN '吃饭' " +
                    "     WHEN category = '交通' THEN '打车' " +
                    "     WHEN category = '居住' THEN '住房' " +
                    "     ELSE category END, " +
                    "icon, note, date, created_at, merchant, item, qty, NULL FROM records");
            db.execSQL("DROP TABLE records");
            db.execSQL("ALTER TABLE records_v3 RENAME TO records");
            db.execSQL("CREATE INDEX idx_records_date ON records(date)");

            // 老分类是「餐饮 / 交通 / 购物 / 居住」这种粗桶，按商品名再判一次会更准
            // （比如「餐饮」里的薯片，应该算零食）
            reclassifyGeneric(db);

            // 场景列表换成新的一套；用户自建的分类保留成场景
            List<String[]> custom = new ArrayList<>();
            Cursor k = db.rawQuery("SELECT DISTINCT type, name, icon FROM categories", null);
            while (k.moveToNext()) {
                String name = k.getString(1);
                if (!isBuiltinName(name, k.getInt(0))) {
                    custom.add(new String[]{String.valueOf(k.getInt(0)), name, k.getString(2)});
                }
            }
            k.close();

            db.execSQL("DELETE FROM categories");
            seed(db);
            for (String[] row : custom) {
                insertCategory(db, Integer.parseInt(row[0]),
                        Scenes.migrateName(row[1]),
                        row[2] == null ? "🏷" : row[2],
                        100);
            }

            db.execSQL(ITEM_RULE_SQL);
            db.execSQL(PLACE_SQL);
        }

                // v3 -> v4：记录时间、地点、小类
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE records ADD COLUMN time TEXT");
            db.execSQL("ALTER TABLE records ADD COLUMN place TEXT");
            db.execSQL("ALTER TABLE records ADD COLUMN sub TEXT");
            // 老记录没有单独时间，用录入时刻补一个 HH:mm
            db.execSQL("UPDATE records SET time = strftime('%H:%M', created_at / 1000, " +
                    "'unixepoch', 'localtime') WHERE time IS NULL OR time = ''");
            // 老记录的小类尽量从商品名/备注补出来
            Cursor c = db.rawQuery("SELECT _id, scene, item, note FROM records " +
                    "WHERE sub IS NULL OR sub = ''", null);
            List<Object[]> rows = new ArrayList<>();
            while (c.moveToNext()) {
                String sub = Subs.guess(c.getString(1), c.getString(2), c.getString(3));
                if (sub != null) rows.add(new Object[]{c.getLong(0), sub});
            }
            c.close();
            for (Object[] row : rows) {
                ContentValues v = new ContentValues();
                v.put("sub", (String) row[1]);
                db.update("records", v, "_id = ?", new String[]{String.valueOf(row[0])});
            }
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_place ON records(place)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_scene ON records(scene)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_sub ON records(sub)");
        }

        // v4 -> v5：分类升级为 9 个生活大类 + 小类，商品记忆也带上小类
        if (oldVersion < 5) {
            // 旧库的 item_rule 没有 sub 列；v1/v2 升级时已经按新结构建好了
            if (oldVersion >= 3) {
                try {
                    db.execSQL("ALTER TABLE item_rule ADD COLUMN sub TEXT");
                } catch (Exception ignored) {
                }
            }
            db.execSQL("DELETE FROM item_rule");

            // 支出大类重置为固定 9 个；收入大类保持不变
            db.delete("categories", "type = ?",
                    new String[]{String.valueOf(Record.EXPENSE)});
            insertScenes(db, Record.EXPENSE, Scenes.expense());

            // 旧记录按商品重新判一次大类和小类
            reclassifyExpense(db);
        }

        // v5 -> v6：小类升级成可自定义/可删除的表
        if (oldVersion < 6) {
            db.execSQL(SUB_SQL);
            db.execSQL(SUB_INDEX_SQL);
            seedSubs(db);
        }

        // v6 -> v7：我的地点（手动维护坐标表）
        if (oldVersion < 7) {
            db.execSQL(SPOT_SQL);
        }

        // v7 -> v8：OCR 商家名纠正记忆
        if (oldVersion < 8) {
            db.execSQL(MERCHANT_ALIAS_SQL);
        }
    }

    /** v5 迁移：把旧的宽泛场景（吃饭 / 购物 / 网购……）重判成新的大类 + 小类 */
    private void reclassifyExpense(SQLiteDatabase db) {
        Cursor c = db.rawQuery(
                "SELECT _id, merchant, item, note, scene, sub FROM records WHERE type = ?",
                new String[]{String.valueOf(Record.EXPENSE)});
        List<Object[]> rows = new ArrayList<>();
        while (c.moveToNext()) {
            long id = c.getLong(0);
            String merchant = c.getString(1);
            String item = c.getString(2);
            String note = c.getString(3);
            String oldScene = c.getString(4);
            String oldSub = c.getString(5);

            String context = (oldScene == null ? "" : oldScene)
                    + (note == null ? "" : " " + note);
            Classifier.Verdict v = Classifier.explain(merchant, item, context, null);
            String scene = v.scene;
            String sub = v.sub;
            if ("其他".equals(scene) && oldScene != null && !"其他".equals(oldScene)) {
                String mapped = Scenes.migrateName(oldScene);
                if (Scenes.isScene(mapped, Record.EXPENSE) && !"其他".equals(mapped)) {
                    scene = mapped;
                }
            }
            if (sub == null) sub = Subs.guess(scene, item, note);
            if (sub == null && oldSub != null && Subs.isSub(scene, oldSub)) sub = oldSub;

            if (!eq(scene, oldScene) || !eq(sub, oldSub)) {
                rows.add(new Object[]{scene, sub, Scenes.icon(scene, Record.EXPENSE), id});
            }
        }
        c.close();
        for (Object[] row : rows) {
            ContentValues v = new ContentValues();
            v.put("scene", (String) row[0]);
            v.put("sub", (String) row[1]);
            v.put("icon", (String) row[2]);
            db.update("records", v, "_id = ?", new String[]{String.valueOf(row[3])});
        }
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** 把从粗分类搬过来的记录，按商品名重新归一次类 */
    private void reclassifyGeneric(SQLiteDatabase db) {
        List<Object[]> updates = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT _id, type, merchant, item, note, scene FROM records", null);
        while (c.moveToNext()) {
            if (c.getInt(1) != Record.EXPENSE) continue;
            String scene = c.getString(5);
            if (!isGenericScene(scene)) continue;
            String better = Classifier.scene(c.getString(2), c.getString(3), c.getString(4));
            if (better != null && !"其他".equals(better) && !better.equals(scene)) {
                updates.add(new Object[]{better, c.getLong(0)});
            }
        }
        c.close();
        for (Object[] u : updates) {
            db.execSQL("UPDATE records SET scene = ? WHERE _id = ?", u);
        }
    }

    private static boolean isGenericScene(String scene) {
        if (scene == null) return false;
        return "吃饭".equals(scene) || "打车".equals(scene) || "购物".equals(scene)
                || "住房".equals(scene) || "其他".equals(scene);
    }

    /** 老版本内置的分类名（迁移时要换成场景，不算用户自建） */
    private static boolean isBuiltinName(String name, int type) {
        if (name == null) return true;
        if (type == Record.INCOME) {
            for (String n : new String[]{"工资", "奖金", "理财", "兼职", "红包", "其他"}) {
                if (n.equals(name)) return true;
            }
        } else {
            for (String n : new String[]{"餐饮", "交通", "购物", "日用", "居住",
                    "娱乐", "医疗", "学习", "人情", "其他"}) {
                if (n.equals(name)) return true;
            }
        }
        return false;
    }

    // ---------------- records ----------------

    public List<Record> recordsForMonth(String monthPrefix) {
        List<Record> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("records", null,
                "date LIKE ?", new String[]{monthPrefix + "%"},
                null, null, "date DESC, _id DESC");
        while (c.moveToNext()) {
            out.add(readRecord(c));
        }
        c.close();
        return out;
    }

    /** 最近几笔，给记账页的「最近记的」用 */
    public List<Record> recentRecords(int limit) {
        List<Record> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("records", null, null, null,
                null, null, "date DESC, _id DESC", String.valueOf(limit));
        while (c.moveToNext()) {
            out.add(readRecord(c));
        }
        c.close();
        return out;
    }

    /** 导出用：全部记录，按日期倒序 */
    public List<Record> allRecords() {
        List<Record> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("records", null, null, null,
                null, null, "date DESC, time DESC, _id DESC");
        while (c.moveToNext()) out.add(readRecord(c));
        c.close();
        return out;
    }

    public Record recordById(long id) {
        Cursor c = getReadableDatabase().query("records", null,
                "_id = ?", new String[]{String.valueOf(id)}, null, null, null);
        Record r = c.moveToFirst() ? readRecord(c) : null;
        c.close();
        return r;
    }

    private Record readRecord(Cursor c) {
        Record r = new Record();
        r.id = c.getLong(c.getColumnIndexOrThrow("_id"));
        r.type = c.getInt(c.getColumnIndexOrThrow("type"));
        r.amount = c.getLong(c.getColumnIndexOrThrow("amount"));
        int sceneIdx = c.getColumnIndex("scene");
        if (sceneIdx < 0) sceneIdx = c.getColumnIndex("category");
        r.scene = sceneIdx >= 0 ? c.getString(sceneIdx) : "其他";
        if (r.scene == null || r.scene.trim().isEmpty()) r.scene = "其他";
        r.icon = c.getString(c.getColumnIndexOrThrow("icon"));
        r.note = c.getString(c.getColumnIndexOrThrow("note"));
        r.date = c.getString(c.getColumnIndexOrThrow("date"));
        int timeIdx = c.getColumnIndex("time");
        r.time = timeIdx >= 0 ? c.getString(timeIdx) : null;
        if (r.time == null || r.time.trim().isEmpty()) {
            r.time = Dates.timeOf(c.getLong(c.getColumnIndexOrThrow("created_at")));
        }
        r.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        int placeIdx = c.getColumnIndex("place");
        r.place = placeIdx >= 0 ? c.getString(placeIdx) : null;
        int subIdx = c.getColumnIndex("sub");
        r.sub = subIdx >= 0 ? c.getString(subIdx) : null;
        r.merchant = c.getString(c.getColumnIndexOrThrow("merchant"));
        r.item = c.getString(c.getColumnIndexOrThrow("item"));
        int qtyIdx = c.getColumnIndex("qty");
        r.qty = qtyIdx >= 0 ? c.getDouble(qtyIdx) : 1;
        if (r.qty <= 0) r.qty = 1;
        int unitIdx = c.getColumnIndex("unit");
        r.unit = unitIdx >= 0 ? c.getString(unitIdx) : null;
        return r;
    }

    public long insert(Record r) {
        ContentValues v = new ContentValues();
        v.put("type", r.type);
        v.put("amount", r.amount);
        v.put("scene", r.scene == null ? "其他" : r.scene);
        v.put("icon", r.icon);
        v.put("note", r.note);
        v.put("date", r.date);
        v.put("time", r.time == null || r.time.trim().isEmpty()
                ? Dates.nowTime() : r.time.trim());
        v.put("created_at", System.currentTimeMillis());
        v.put("place", r.place);
        v.put("merchant", r.merchant);
        v.put("item", r.item);
        v.put("sub", r.sub == null || r.sub.trim().isEmpty()
                ? guessSub(r.scene, r.item, r.note) : r.sub.trim());
        v.put("qty", Math.max(0.01, r.qty));
        v.put("unit", r.unit);
        return getWritableDatabase().insert("records", null, v);
    }

    public void update(Record r) {
        ContentValues v = new ContentValues();
        v.put("type", r.type);
        v.put("amount", r.amount);
        v.put("scene", r.scene == null ? "其他" : r.scene);
        v.put("icon", r.icon);
        v.put("note", r.note);
        v.put("date", r.date);
        v.put("time", r.time == null || r.time.trim().isEmpty()
                ? Dates.nowTime() : r.time.trim());
        v.put("place", r.place);
        v.put("merchant", r.merchant);
        v.put("item", r.item);
        // 编辑时尊重用户的选择：清空小类就保持空，不再自动猜回来
        v.put("sub", r.sub == null || r.sub.trim().isEmpty() ? null : r.sub.trim());
        v.put("qty", Math.max(0.01, r.qty));
        v.put("unit", r.unit);
        getWritableDatabase().update("records", v, "_id = ?", new String[]{String.valueOf(r.id)});
    }

    public void delete(long id) {
        getWritableDatabase().delete("records", "_id = ?", new String[]{String.valueOf(id)});
    }

    /** 清空账单，保留归类设置 */
    public void clearRecords() {
        getWritableDatabase().delete("records", null, null);
    }

    /** 最近一笔用过哪个场景：快捷记账默认沿用它 */
    public String lastScene(int type) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT scene FROM records WHERE type = ? ORDER BY date DESC, _id DESC LIMIT 1",
                new String[]{String.valueOf(type)});
        String scene = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        return scene;
    }

    /** 最近一笔用过的小类 */
    public String lastSub(int type) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT sub FROM records WHERE type = ? AND sub IS NOT NULL AND sub != '' " +
                        "ORDER BY date DESC, _id DESC LIMIT 1",
                new String[]{String.valueOf(type)});
        String sub = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        return sub;
    }

    /** 最近输入过的地点（去重，新的在前） */
    public List<String> recentPlaces(int limit) {
        return recentValues("place", null, limit);
    }

    /** 最近输入过的商家（去重，新的在前） */
    public List<String> recentMerchants(int limit) {
        return recentValues("merchant", null, limit);
    }

    /** 最近输入过的商品；传 scene 时优先这个大类下的商品 */
    public List<String> recentItems(String scene, int limit) {
        return recentValues("item", scene, limit);
    }

    private List<String> recentValues(String column, String scene, int limit) {
        List<String> out = new ArrayList<>();
        String sql = "SELECT " + column + " FROM records WHERE " + column
                + " IS NOT NULL AND TRIM(" + column + ") != ''";
        List<String> args = new ArrayList<>();
        if (scene != null) {
            sql += " AND scene = ?";
            args.add(scene);
        }
        sql += " GROUP BY " + column + " ORDER BY MAX(_id) DESC LIMIT " + Math.max(1, limit);
        Cursor c = getReadableDatabase().rawQuery(sql, args.toArray(new String[0]));
        while (c.moveToNext()) out.add(c.getString(0));
        c.close();
        return out;
    }

    // ---------------- categories（现在存的是场景） ----------------

    public List<Category> categories(int type) {
        List<Category> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("categories", null,
                "type = ?", new String[]{String.valueOf(type)},
                null, null, "sort ASC, _id ASC");
        while (c.moveToNext()) {
            out.add(new Category(
                    c.getLong(c.getColumnIndexOrThrow("_id")),
                    c.getInt(c.getColumnIndexOrThrow("type")),
                    c.getString(c.getColumnIndexOrThrow("name")),
                    c.getString(c.getColumnIndexOrThrow("icon"))));
        }
        c.close();
        return out;
    }

    public Category addCategory(int type, String name, String icon) {
        int sort = 0;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT MAX(sort) FROM categories WHERE type = ?",
                new String[]{String.valueOf(type)});
        if (c.moveToFirst()) sort = c.getInt(0) + 1;
        c.close();

        ContentValues v = new ContentValues();
        v.put("type", type);
        v.put("name", name);
        v.put("icon", icon);
        v.put("sort", sort);
        long id = getWritableDatabase().insert("categories", null, v);
        return new Category(id, type, name, icon);
    }

    // ---------------- 小类：内置 + 自定义 ----------------

    /** 某个大类下可用的小类；被删掉的不返回 */
    public List<String> subsFor(String scene) {
        List<String> out = new ArrayList<>();
        if (scene == null) return out;
        Cursor c = getReadableDatabase().query("subs", new String[]{"name"},
                "scene = ? AND hidden = 0", new String[]{scene},
                null, null, "sort ASC, _id ASC");
        while (c.moveToNext()) out.add(c.getString(0));
        c.close();
        return out;
    }

    public boolean isSub(String scene, String sub) {
        if (scene == null || sub == null || sub.trim().isEmpty()) return false;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM subs WHERE scene = ? AND name = ? AND hidden = 0 LIMIT 1",
                new String[]{scene, sub.trim()});
        boolean found = c.moveToFirst();
        c.close();
        return found;
    }

    /** 自动猜小类：先按内置关键词猜，再确认这个小类还在 */
    public String guessSub(String scene, String item, String note) {
        String sub = Subs.guess(scene, item, note);
        return isSub(scene, sub) ? sub : null;
    }

    /** 添加自定义小类；同名小类存在（包括之前删掉的内置小类）就恢复它 */
    public void addSub(String scene, String name) {
        if (scene == null || name == null) return;
        String n = name.trim();
        if (n.isEmpty()) return;

        SQLiteDatabase db = getWritableDatabase();
        ContentValues show = new ContentValues();
        show.put("hidden", 0);
        int updated = db.update("subs", show, "scene = ? AND name = ?",
                new String[]{scene, n});
        if (updated == 0) {
            int sort = 0;
            Cursor c = db.rawQuery("SELECT MAX(sort) FROM subs WHERE scene = ?",
                    new String[]{scene});
            if (c.moveToFirst()) sort = c.getInt(0) + 1;
            c.close();

            ContentValues v = new ContentValues();
            v.put("scene", scene);
            v.put("name", n);
            v.put("builtin", 0);
            v.put("hidden", 0);
            v.put("sort", sort);
            db.insertWithOnConflict("subs", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
        syncHiddenScenes();
    }

    public int countInSub(String scene, String sub) {
        if (scene == null || sub == null) return 0;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM records WHERE scene = ? AND sub = ?",
                new String[]{scene, sub.trim()});
        int n = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return n;
    }

    /**
     * 删小类：内置小类标记隐藏（以后还能加回来），自定义小类直接删。
     * 已有记录只清空小类，大类保留；指向这个小类的商品记忆一起清掉。
     */
    public void deleteSub(String scene, String name) {
        if (scene == null || name == null) return;
        String n = name.trim();
        if (n.isEmpty()) return;

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues hidden = new ContentValues();
            hidden.put("hidden", 1);
            int n1 = db.update("subs", hidden,
                    "scene = ? AND name = ? AND builtin = 1", new String[]{scene, n});
            if (n1 == 0) {
                db.delete("subs", "scene = ? AND name = ?", new String[]{scene, n});
            }

            ContentValues clear = new ContentValues();
            clear.putNull("sub");
            db.update("records", clear, "scene = ? AND sub = ?", new String[]{scene, n});
            db.delete("item_rule", "scene = ? AND sub = ?", new String[]{scene, n});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        syncHiddenScenes();
    }

    // ---------------- 归类管理：删掉用不上的场景 ----------------

    /** 某个场景下一共有多少笔记录 */
    public int countInScene(String scene, int type) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM records WHERE scene = ? AND type = ?",
                new String[]{scene, String.valueOf(type)});
        int n = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return n;
    }

    /** 近一段时间每个场景用了多少次，用来把常用的归类排在前面 */
    public Map<String, Integer> sceneUsage(String fromIso, int type) {
        Map<String, Integer> out = new LinkedHashMap<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT scene, COUNT(*) FROM records WHERE type = ? AND date >= ? " +
                        "GROUP BY scene",
                new String[]{String.valueOf(type), fromIso});
        while (c.moveToNext()) {
            out.put(c.getString(0), c.getInt(1));
        }
        c.close();
        return out;
    }

    /**
     * 删除一个归类（内置的也能删）。
     * 它下面的记录会归到「其他」，相关的手动归类记忆一起清掉，
     * 之后的自动归类也不会再选中它。
     */
    public void deleteScene(long id, String scene, int type) {
        if (scene == null || "其他".equals(scene)) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (id > 0) {
                db.delete("categories", "_id = ?", new String[]{String.valueOf(id)});
            } else {
                db.delete("categories", "type = ? AND name = ?",
                        new String[]{String.valueOf(type), scene});
            }

            ContentValues v = new ContentValues();
            v.put("scene", "其他");
            v.put("sub", (String) null);
            v.put("icon", Scenes.icon("其他", type));
            db.update("records", v, "scene = ? AND type = ?",
                    new String[]{scene, String.valueOf(type)});

            db.delete("item_rule", "scene = ?", new String[]{scene});
            db.delete("subs", "scene = ?", new String[]{scene});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        syncHiddenScenes();
    }

    /** 把「内置但已经被删掉」的大类和小类同步给分类器，避免它又判回不存在的东西 */
    public void syncHiddenScenes() {
        for (int type : new int[]{Record.EXPENSE, Record.INCOME}) {
            List<String> missing = new ArrayList<>();
            for (String name : Scenes.names(type)) {
                if (!hasCategory(type, name)) missing.add(name);
            }
            Classifier.setHidden(type, missing);
        }

        Map<String, Set<String>> hiddenSubs = new LinkedHashMap<>();
        Cursor c = getReadableDatabase().query("subs",
                new String[]{"scene", "name"}, "hidden = 1",
                null, null, null, null);
        while (c.moveToNext()) {
            String scene = c.getString(0);
            Set<String> set = hiddenSubs.get(scene);
            if (set == null) {
                set = new HashSet<>();
                hiddenSubs.put(scene, set);
            }
            set.add(c.getString(1));
        }
        c.close();
        Classifier.setHiddenSubs(hiddenSubs);
    }

    private boolean hasCategory(int type, String name) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM categories WHERE type = ? AND name = ? LIMIT 1",
                new String[]{String.valueOf(type), name});
        boolean found = c.moveToFirst();
        c.close();
        return found;
    }


    // ---------------- 商家名纠正 ----------------

    /** 记住「OCR 认错的名字 -> 正确名字」；同名键被改多次时覆盖成最新结果 */
    public boolean rememberMerchant(String raw, String fixed) {
        String key = Merchants.normalize(raw);
        String value = fixed == null ? "" : fixed.trim();
        if (key.isEmpty() || value.isEmpty()) return false;

        if (key.equals(Merchants.normalize(value))) {
            // 用户又改回原名：清掉旧纠正，避免下次强行替换
            getWritableDatabase().delete("merchant_alias", "raw = ?", new String[]{key});
            return false;
        }

        Cursor c = getReadableDatabase().query("merchant_alias",
                new String[]{"hits"}, "raw = ?", new String[]{key}, null, null, null);
        int hits = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();

        ContentValues v = new ContentValues();
        v.put("raw", key);
        v.put("fixed", value);
        v.put("hits", hits + 1);
        v.put("updated_at", System.currentTimeMillis());

        long id;
        if (hits > 0) {
            id = getWritableDatabase().update("merchant_alias", v,
                    "raw = ?", new String[]{key});
        } else {
            id = getWritableDatabase().insertWithOnConflict("merchant_alias", null,
                    v, SQLiteDatabase.CONFLICT_REPLACE);
        }
        return id > 0 || hits > 0;
    }

    /** OCR 文本 -> 正确商家名；没有记忆时原样返回 */
    public String correctMerchant(String raw) {
        if (raw == null || raw.trim().isEmpty()) return raw;
        return Merchants.correct(raw, merchantAliases());
    }

    /** 设置页：商家名纠正列表，按最近更新排序 */
    public List<String[]> merchantAliases() {
        List<String[]> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("merchant_alias", null, null, null,
                null, null, "updated_at DESC");
        while (c.moveToNext()) {
            out.add(new String[]{
                    c.getString(c.getColumnIndexOrThrow("raw")),
                    c.getString(c.getColumnIndexOrThrow("fixed"))
            });
        }
        c.close();
        return out;
    }

    public void forgetMerchant(String raw) {
        String key = Merchants.normalize(raw);
        if (key.isEmpty()) return;
        getWritableDatabase().delete("merchant_alias", "raw = ?", new String[]{key});
    }

    // ---------------- 归类记忆 ----------------

    /** 用户改过的「商品 -> 大类/小类」 */
    public Map<String, Classifier.Rule> itemRules() {
        Map<String, Classifier.Rule> out = new LinkedHashMap<>();
        Cursor c = getReadableDatabase().query("item_rule", null, null, null, null, null, null);
        int subIdx = c.getColumnIndex("sub");
        while (c.moveToNext()) {
            out.put(c.getString(c.getColumnIndexOrThrow("item")),
                    new Classifier.Rule(
                            c.getString(c.getColumnIndexOrThrow("scene")),
                            subIdx >= 0 ? c.getString(subIdx) : null));
        }
        c.close();
        return out;
    }

    /** 记住一条归类；同样的商品被改过多次时覆盖成最新选择 */
    public void rememberItem(String item, String scene, String sub) {
        if (item == null || item.trim().isEmpty() || scene == null) return;
        String key = item.trim();
        ContentValues v = new ContentValues();
        v.put("item", key);
        v.put("scene", scene);
        if (sub == null || sub.trim().isEmpty() || !isSub(scene, sub)) {
            v.putNull("sub");
        } else {
            v.put("sub", sub.trim());
        }
        v.put("hits", 1);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("item_rule", null, v,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void rememberItem(String item, String scene) {
        rememberItem(item, scene, guessSub(scene, item, null));
    }

    public void forgetItem(String item) {
        if (item == null) return;
        getWritableDatabase().delete("item_rule", "item = ?", new String[]{item.trim()});
    }

    /** 设置页：商品记忆列表，按最近更新排序 */
    public List<String[]> itemRulesList() {
        List<String[]> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("item_rule", null, null, null,
                null, null, "updated_at DESC");
        int subIdx = c.getColumnIndex("sub");
        while (c.moveToNext()) {
            out.add(new String[]{
                    c.getString(c.getColumnIndexOrThrow("item")),
                    c.getString(c.getColumnIndexOrThrow("scene")),
                    subIdx >= 0 ? c.getString(subIdx) : null
            });
        }
        c.close();
        return out;
    }

    // ---------------- 地点记忆 ----------------

    /** 这个坐标附近记过的地点名，没有就返回 null */
    public String nearestPlace(double lat, double lon, double meters) {
        String best = null;
        double bestDistance = Double.MAX_VALUE;
        Cursor c = getReadableDatabase().query("place", null, null, null, null, null, null);
        while (c.moveToNext()) {
            double d = LocationHelper.distance(lat, lon, c.getDouble(0), c.getDouble(1));
            if (d <= meters && d < bestDistance) {
                bestDistance = d;
                best = c.getString(2);
            }
        }
        c.close();
        return best;
    }

    /** 记住「这个位置 = 这个地点」；附近已经有记录就更新它 */
    public void rememberPlace(double lat, double lon, String name) {
        if (name == null || name.trim().isEmpty()) return;
        String existing = nearestPlace(lat, lon, 100);
        ContentValues v = new ContentValues();
        v.put("lat", lat);
        v.put("lon", lon);
        v.put("name", name.trim());
        v.put("updated_at", System.currentTimeMillis());
        if (existing != null) {
            getWritableDatabase().update("place", v, "name = ?", new String[]{existing});
        } else {
            getWritableDatabase().insert("place", null, v);
        }
    }

    // ---------------- 我的地点（手动维护的坐标表） ----------------

    private Spot readSpot(Cursor c) {
        Spot s = new Spot();
        s.id = c.getLong(c.getColumnIndexOrThrow("_id"));
        s.name = c.getString(c.getColumnIndexOrThrow("name"));
        s.lat = c.getDouble(c.getColumnIndexOrThrow("lat"));
        s.lon = c.getDouble(c.getColumnIndexOrThrow("lon"));
        int radiusIdx = c.getColumnIndex("radius");
        s.radius = radiusIdx >= 0 ? c.getInt(radiusIdx) : 10000;
        if (s.radius <= 0) s.radius = 10000;
        int updatedIdx = c.getColumnIndex("updated_at");
        s.updatedAt = updatedIdx >= 0 ? c.getLong(updatedIdx) : 0;
        return s;
    }

    public List<Spot> spots() {
        List<Spot> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("spots", null, null, null,
                null, null, "updated_at DESC, _id DESC");
        while (c.moveToNext()) out.add(readSpot(c));
        c.close();
        return out;
    }

    public Spot spotByName(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        Cursor c = getReadableDatabase().query("spots", null,
                "name = ?", new String[]{name.trim()}, null, null, null);
        Spot s = c.moveToFirst() ? readSpot(c) : null;
        c.close();
        return s;
    }

    /** 新建地点；同名地点自动更新坐标和范围 */
    public long saveSpot(String name, double lat, double lon, int radius) {
        if (name == null || name.trim().isEmpty()) return -1;
        String n = name.trim();
        if (radius <= 0) radius = 10000;

        ContentValues v = new ContentValues();
        v.put("name", n);
        v.put("lat", lat);
        v.put("lon", lon);
        v.put("radius", radius);
        v.put("updated_at", System.currentTimeMillis());

        SQLiteDatabase db = getWritableDatabase();
        int updated = db.update("spots", v, "name = ?", new String[]{n});
        if (updated > 0) return -1;

        long id = db.insert("spots", null, v);
        if (id < 0) {
            // 并发或旧数据冲突：再更新一次
            db.update("spots", v, "name = ?", new String[]{n});
        }
        return id;
    }

    public void updateSpot(long id, String name, double lat, double lon, int radius) {
        if (id <= 0 || name == null || name.trim().isEmpty()) return;
        ContentValues v = new ContentValues();
        v.put("name", name.trim());
        v.put("lat", lat);
        v.put("lon", lon);
        v.put("radius", radius <= 0 ? 10000 : radius);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("spots", v, "_id = ?", new String[]{String.valueOf(id)});
    }

    public void deleteSpot(long id) {
        if (id > 0) getWritableDatabase().delete("spots", "_id = ?",
                new String[]{String.valueOf(id)});
    }

    /** 当前坐标落在哪个「我的地点」范围内；没有返回 null */
    public Spot nearestSpot(double lat, double lon, double accuracy) {
        return Spots.nearest(spots(), lat, lon, accuracy);
    }

    /** 某个地点名下记过多少笔，用来在列表里显示使用次数 */
    public int countRecordsAtPlace(String name) {
        if (name == null || name.trim().isEmpty()) return 0;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM records WHERE place = ?", new String[]{name.trim()});
        int n = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return n;
    }

    /** 最近一次记过的地点，语音没提地点时兜底用 */
    public String lastMerchant() {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT merchant FROM records WHERE merchant IS NOT NULL AND merchant != '' " +
                        "ORDER BY _id DESC LIMIT 1", null);
        String m = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        return m;
    }

    // ---------------- stats ----------------

    public List<Stat> categoryTotals(String monthPrefix, int type) {
        List<Stat> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT scene, icon, SUM(amount) AS total FROM records " +
                        "WHERE date LIKE ? AND type = ? " +
                        "GROUP BY scene, icon ORDER BY total DESC",
                new String[]{monthPrefix + "%", String.valueOf(type)});
        while (c.moveToNext()) {
            Stat s = new Stat();
            s.name = c.getString(0);
            s.icon = c.getString(1);
            s.amount = c.getLong(2);
            out.add(s);
        }
        c.close();
        return out;
    }

    public int countForMonth(String monthPrefix, int type) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM records WHERE date LIKE ? AND type = ?",
                new String[]{monthPrefix + "%", String.valueOf(type)});
        int n = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return n;
    }

    /** 批量插入（扫小票/语音一次存多条） */
    public int insertAll(List<Record> list) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Record r : list) insert(r);
            db.setTransactionSuccessful();
            return list.size();
        } finally {
            db.endTransaction();
        }
    }

    // ---------------- 区间统计（from / to 为 yyyy-MM-dd，含首尾） ----------------

    public List<Stat> categoryTotals(String from, String to, int type) {
        List<Stat> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT scene, icon, SUM(amount) AS total FROM records " +
                        "WHERE date >= ? AND date <= ? AND type = ? " +
                        "GROUP BY scene, icon ORDER BY total DESC",
                new String[]{from, to, String.valueOf(type)});
        while (c.moveToNext()) {
            Stat s = new Stat();
            s.name = c.getString(0);
            s.icon = c.getString(1);
            s.amount = c.getLong(2);
            out.add(s);
        }
        c.close();
        return out;
    }

    /** 小类排行：name 形如「食/水果」，只统计支出 */
    public List<Stat> subTotals(String from, String to, int type) {
        List<Stat> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT scene, COALESCE(NULLIF(TRIM(sub), ''), '未分类') AS s, " +
                        "SUM(amount) AS total, COUNT(*) AS n FROM records " +
                        "WHERE date >= ? AND date <= ? AND type = ? " +
                        "GROUP BY scene, s ORDER BY total DESC",
                new String[]{from, to, String.valueOf(type)});
        while (c.moveToNext()) {
            Stat s = new Stat();
            s.scene = c.getString(0);
            s.sub = c.getString(1);
            s.name = s.scene + "/" + s.sub;
            s.icon = Scenes.icon(s.scene, type);
            s.amount = c.getLong(2);
            s.count = c.getInt(3);
            out.add(s);
        }
        c.close();
        return out;
    }

    public List<Stat> merchantTotals(String from, String to, int type, int limit) {
        List<Stat> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT merchant, SUM(amount) AS total FROM records " +
                        "WHERE date >= ? AND date <= ? AND type = ? " +
                        "AND merchant IS NOT NULL AND merchant != '' " +
                        "GROUP BY merchant ORDER BY total DESC LIMIT " + limit,
                new String[]{from, to, String.valueOf(type)});
        while (c.moveToNext()) {
            Stat s = new Stat();
            s.name = c.getString(0);
            s.icon = "🏪";
            s.amount = c.getLong(1);
            out.add(s);
        }
        c.close();
        return out;
    }

    public long totalAmount(String from, String to, int type) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT SUM(amount) FROM records WHERE date >= ? AND date <= ? AND type = ?",
                new String[]{from, to, String.valueOf(type)});
        long v = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return v;
    }

    public int countInRange(String from, String to, int type) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM records WHERE date >= ? AND date <= ? AND type = ?",
                new String[]{from, to, String.valueOf(type)});
        int n = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return n;
    }

    /** 最早一条记录的日期，用于「全部」范围的日均 */
    public String earliestDate() {
        Cursor c = getReadableDatabase().rawQuery("SELECT MIN(date) FROM records", null);
        String d = c.moveToFirst() ? c.getString(0) : null;
        c.close();
        return d;
    }

    /** 按月汇总，name 为 yyyy-MM，按时间升序 */
    public List<Stat> monthlyTotals(String from, String to, int type) {
        List<Stat> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT substr(date, 1, 7) AS m, SUM(amount) AS total FROM records " +
                        "WHERE date >= ? AND date <= ? AND type = ? " +
                        "GROUP BY m ORDER BY m ASC",
                new String[]{from, to, String.valueOf(type)});
        while (c.moveToNext()) {
            Stat s = new Stat();
            s.name = c.getString(0);
            s.amount = c.getLong(1);
            out.add(s);
        }
        c.close();
        return out;
    }

    /** 区间内某一类型的全部记录，报表在内存里做筛选/分组 */
    public List<Record> recordsInRange(String from, String to, int type) {
        List<Record> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("records", null,
                "date >= ? AND date <= ? AND type = ?",
                new String[]{from, to, String.valueOf(type)},
                null, null, "date DESC, time DESC, _id DESC");
        while (c.moveToNext()) out.add(readRecord(c));
        c.close();
        return out;
    }

    /** 某个场景在区间内的明细，用于点场景看账单 */
    public List<Record> recordsInRange(String from, String to, String scene, int type) {
        List<Record> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("records", null,
                "date >= ? AND date <= ? AND scene = ? AND type = ?",
                new String[]{from, to, scene, String.valueOf(type)},
                null, null, "date DESC, _id DESC");
        while (c.moveToNext()) out.add(readRecord(c));
        c.close();
        return out;
    }

    /** 某个大类 + 小类在区间内的明细，用于小类排行点进去看账单 */
    public List<Record> recordsInRange(String from, String to,
                                       String scene, String sub, int type) {
        List<Record> out = new ArrayList<>();
        String subCond;
        String[] args;
        if (sub == null || sub.trim().isEmpty() || "未分类".equals(sub)) {
            subCond = "(sub IS NULL OR TRIM(sub) = '')";
            args = new String[]{from, to, scene, String.valueOf(type)};
        } else {
            subCond = "sub = ?";
            args = new String[]{from, to, scene, sub.trim(), String.valueOf(type)};
        }
        Cursor c = getReadableDatabase().query("records", null,
                "date >= ? AND date <= ? AND scene = ? AND " + subCond + " AND type = ?",
                args, null, null, "date DESC, _id DESC");
        while (c.moveToNext()) out.add(readRecord(c));
        c.close();
        return out;
    }
}
