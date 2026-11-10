package com.k.moneybook;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 导出落地：
 * - 分享：写到缓存目录，交给系统分享面板（发微信 / 发电脑 / 存文件）
 * - 保存到文件：走系统文件选择器，用户自己挑位置（下载 / 网盘目录）
 */
public class ExportShare {

    public static final int REQUEST_SAVE = 4711;

    private static String pendingText;
    private static String pendingName;
    private static String pendingMime;

    /** 分享：系统面板里选微信 / 邮件 / 文件管理器 */
    public static void share(Activity activity, List<Record> records, boolean csv) {
        try {
            File file = writeCache(activity, records, csv);
            Uri uri = FileProvider.getUriForFile(activity,
                    activity.getPackageName() + ".files", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(csv ? "text/csv" : "application/json");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "记账本导出 · " + records.size() + " 笔");
            intent.setClipData(ClipData.newUri(activity.getContentResolver(), "moneybook", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent,
                    csv ? "分享 CSV" : "分享 JSON"));
        } catch (Exception e) {
            Toast.makeText(activity, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 保存到文件：系统文件选择器，任何手机都有，不依赖装了微信/邮件 */
    public static void save(Activity activity, List<Record> records, boolean csv) {
        try {
            pendingText = csv ? Export.csv(records) : Export.json(records);
            pendingName = Export.fileName(csv);
            pendingMime = csv ? "text/csv" : "application/json";

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(pendingMime);
            intent.putExtra(Intent.EXTRA_TITLE, pendingName);
            activity.startActivityForResult(intent, REQUEST_SAVE);
        } catch (Exception e) {
            Toast.makeText(activity, "导出失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static void onActivityResult(Activity activity, int requestCode, int resultCode,
                                        Intent data) {
        if (requestCode != REQUEST_SAVE) return;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            pendingText = null;
            return;
        }
        Uri uri = data.getData();
        try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("无法写入");
            out.write(pendingText.getBytes(StandardCharsets.UTF_8));
            out.flush();
            Toast.makeText(activity, "已保存 " + pendingName, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            pendingText = null;
        }
    }

    private static File writeCache(Activity activity, List<Record> records, boolean csv)
            throws Exception {
        File dir = new File(activity.getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("无法创建导出目录");
        }
        File file = new File(dir, Export.fileName(csv));
        String content = csv ? Export.csv(records) : Export.json(records);
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(content);
        }
        return file;
    }
}
