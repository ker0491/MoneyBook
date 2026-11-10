package com.k.moneybook;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputFilter;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.function.Consumer;

/**
 * 「＋ 自定义」小类弹窗。
 *
 * 快捷记账、编辑页、小票确认、语音确认共用一套：输入名字 → 写进 subs 表 →
 * 立刻选中。下次这个小类会出现在对应大类的 chips 里。
 */
public final class SubUi {

    private static final int MAX_LENGTH = 12;

    private SubUi() {
    }

    public static void showAdd(final Context ctx, final String scene,
                               final Consumer<String> onAdded) {
        if (ctx == null || scene == null) return;

        final EditText input = new EditText(ctx);
        input.setHint("小类名，如 咖啡 / 宠物粮");
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_LENGTH)});

        int pad = Ui.dp(ctx, 20);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Ui.dp(ctx, 10), pad, 0);
        box.addView(input);

        final AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle("给「" + scene + "」加一个小类")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", null)
                .create();

        dialog.setOnShowListener(d -> {
            input.requestFocus();
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    input.setError("先写个小类名");
                    return;
                }
                if (name.contains("/")) {
                    input.setError("小类名里不要带 /");
                    return;
                }

                DB db = DB.get(ctx);
                boolean exists = db.isSub(scene, name);
                if (!exists) db.addSub(scene, name);
                dialog.dismiss();
                if (onAdded != null) onAdded.accept(name);
                Toast.makeText(ctx, exists ? "已选：" + scene + "/" + name
                        : "已添加：" + scene + "/" + name, Toast.LENGTH_SHORT).show();
            });
        });
        dialog.show();
    }
}
