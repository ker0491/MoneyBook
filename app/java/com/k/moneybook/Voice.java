package com.k.moneybook;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * 应用内语音输入。
 *
 * 之前用的是 ACTION_RECOGNIZE_SPEECH 拉起小爱的界面，在 HyperOS 上会出现
 * 「界面弹出来了但录不到声音」——因为麦克风权限是按调用方 App 检查的，而本 App
 * 连 RECORD_AUDIO 都没声明。现在改成在自己进程里录：
 *
 *   - 要麦克风权限（RECORD_AUDIO），申请不到就不硬撑，直接提示改用键盘
 *   - 自己显示「正在听…」，不用跳到别的 App
 *   - 识别失败能拿到错误码，能告诉你到底是没听到、没权限还是服务坏了
 */
public class Voice implements RecognitionListener {

    public interface Listener {
        void onSpeech(String text);

        /** 失败原因（已经是人话），调用方负责提示 / 转手输 */
        void onFailed(String message);
    }

    private final Activity activity;
    private final Listener listener;

    private SpeechRecognizer recognizer;
    private AlertDialog dialog;
    private TextView status;
    private boolean finished;
    /** 用户自己点了取消，之后到的错误/结果都不再回调 */
    private boolean canceled;

    public Voice(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public static boolean hasMic(Context ctx) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** 系统里有没有能用的识别服务 */
    public static boolean canRecognize(Context ctx) {
        try {
            return SpeechRecognizer.isRecognitionAvailable(ctx);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 系统里有没有「选定的」识别服务。
     * 有些 ROM（比如 HyperOS）虽然能查到 AsrService，但没有给第三方 App 指定默认服务，
     * 这时 SpeechRecognizer 一创建就报 ERROR_CLIENT，只能走系统识别界面。
     */
    public static boolean hasSelectedService(Context ctx) {
        try {
            String service = Settings.Secure.getString(
                    ctx.getContentResolver(), "voice_recognition_service");
            return service != null && !service.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** 能在自己进程里录音就用应用内识别，否则用系统识别界面 */
    public static boolean preferInApp(Context ctx) {
        return canRecognize(ctx) && hasSelectedService(ctx);
    }

    /** 系统识别界面的备用 intent */
    public static Intent systemIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                "比如：今天在永辉超市买了薯片20 可乐5块");
        return intent;
    }

    public void start() {
        if (!hasMic(activity)) {
            listener.onFailed("没有麦克风权限，没法录音");
            return;
        }
        if (!canRecognize(activity)) {
            listener.onFailed("这个手机没有可用的语音识别服务");
            return;
        }
        try {
            showDialog();
            recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
            recognizer.setRecognitionListener(this);
            recognizer.startListening(systemIntent());
        } catch (Exception e) {
            finish();
            listener.onFailed("语音识别启动失败");
        }
    }

    /** 界面被关掉 / Activity 结束时要调用，避免泄漏麦克风 */
    public void destroy() {
        canceled = true;
        if (recognizer != null) {
            try {
                recognizer.cancel();
            } catch (Exception ignored) {
            }
            try {
                recognizer.destroy();
            } catch (Exception ignored) {
            }
            recognizer = null;
        }
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
        dialog = null;
        finished = true;
    }

    private void showDialog() {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_voice, null);
        status = view.findViewById(R.id.voiceStatus);

        dialog = new AlertDialog.Builder(activity).setView(view).create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnCancelListener(d -> {
            canceled = true;
            finish();
        });
        view.findViewById(R.id.voiceCancel).setOnClickListener(v -> {
            canceled = true;
            finish();
        });
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(Ui.dp(activity, 280), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void setStatus(String text) {
        if (status != null) status.setText(text);
    }

    private void finish() {
        if (finished) return;
        finished = true;
        if (recognizer != null) {
            try {
                recognizer.destroy();
            } catch (Exception ignored) {
            }
            recognizer = null;
        }
        if (dialog != null && dialog.isShowing()) {
            try {
                dialog.dismiss();
            } catch (Exception ignored) {
            }
        }
        dialog = null;
        status = null;
    }

    // ---------------- RecognitionListener ----------------

    @Override
    public void onReadyForSpeech(Bundle params) {
        setStatus("请说…");
    }

    @Override
    public void onBeginningOfSpeech() {
        setStatus("在听…");
    }

    @Override
    public void onRmsChanged(float rmsdB) {
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
        setStatus("正在识别…");
    }

    @Override
    public void onError(int error) {
        if (canceled) return;
        finish();
        listener.onFailed(messageOf(error));
    }

    @Override
    public void onResults(Bundle results) {
        if (canceled) return;
        ArrayList<String> texts = results == null ? null
                : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        finish();
        if (texts == null || texts.isEmpty()) {
            listener.onFailed("没听到内容");
            return;
        }
        String best = texts.get(0);
        if (best == null || best.trim().isEmpty()) {
            listener.onFailed("没听到内容");
            return;
        }
        listener.onSpeech(best.trim());
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        ArrayList<String> texts = partialResults == null ? null
                : partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (texts != null && !texts.isEmpty() && texts.get(0) != null) {
            setStatus(texts.get(0));
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
    }

    /** 错误码翻成人话 */
    static String messageOf(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "没听到声音，再说一次？";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "等太久没听到声音";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "没有麦克风权限";
            case SpeechRecognizer.ERROR_AUDIO:
                return "录音失败，可能被别的应用占着麦克风";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "识别服务正忙，稍等一下";
            case SpeechRecognizer.ERROR_CLIENT:
                return "语音识别服务不可用";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "识别服务连不上";
            case SpeechRecognizer.ERROR_SERVER:
                return "识别服务出错了";
            default:
                return "没听清（错误码 " + error + "）";
        }
    }
}
