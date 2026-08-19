package com.xiaoai.llmredirect;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

public class ConfigActivity extends Activity {

    private static final String PREFS = "config";

    private EditText etUrl, etKey, etModel, etFastModel, etProModel, etRouterUrl, etRouterKey, etRouterModel;
    private CheckBox cbEnabled, cbRouter, cbAux, cbVerbose, cbTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, dp(32));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("小爱同学 LLM 重定向");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("把超级小爱智能体(OSBot Agent)的大模型请求指向自定义 OpenAI 兼容后端\n适配 小爱同学 8.0.17.3013");
        sub.setTextSize(12);
        sub.setTextColor(Color.GRAY);
        sub.setPadding(0, dp(4), 0, dp(12));
        root.addView(sub);

        cbEnabled = new CheckBox(this);
        cbEnabled.setText("启用重定向 (保存后需重启小爱同学)");
        root.addView(cbEnabled);

        root.addView(label("API Base URL (OpenAI 兼容, 如 https://your.host/v1)"));
        etUrl = new EditText(this);
        etUrl.setHint("https://your.host/v1");
        etUrl.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(etUrl);

        root.addView(label("API Key (发送 Authorization: Bearer <key>)"));
        etKey = new EditText(this);
        etKey.setHint("sk-...");
        root.addView(etKey);

        root.addView(label("主模型 (默认/未匹配档位)"));
        etModel = new EditText(this);
        etModel.setHint("如 deepseek-chat");
        root.addView(etModel);

        root.addView(label("快速模式模型 (留空=主模型, 自动模式的快速/直答档也用它)"));
        etFastModel = new EditText(this);
        etFastModel.setHint("小而快, 如 qwen2.5-7b-instruct");
        root.addView(etFastModel);

        root.addView(label("专家模式模型 (留空=主模型, 自动模式的深度档也用它)"));
        etProModel = new EditText(this);
        etProModel.setHint("能力强, 如 deepseek-chat / gpt-4o");
        root.addView(etProModel);

        cbRouter = new CheckBox(this);
        cbRouter.setText("同时重定向意图路由小模型 (推荐开启)");
        root.addView(cbRouter);

        root.addView(label("路由 Base URL (留空则用上面的主配置)"));
        etRouterUrl = new EditText(this);
        etRouterUrl.setHint("https://your.host/v1");
        etRouterUrl.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(etRouterUrl);

        root.addView(label("路由 API Key (留空则用主配置)"));
        etRouterKey = new EditText(this);
        etRouterKey.setHint("sk-...");
        root.addView(etRouterKey);

        root.addView(label("路由模型名 (留空则用主配置, 建议小而快的模型)"));
        etRouterModel = new EditText(this);
        etRouterModel.setHint("qwen2.5-7b-instruct 等");
        root.addView(etRouterModel);

        cbTimer = new CheckBox(this);
        cbTimer.setText("解锁定时任务 (绕过会员检测, 本地功能照常执行)");
        root.addView(cbTimer);

        cbAux = new CheckBox(this);
        cbAux.setText("重定向辅助服务 embeddings/rerank (自建后端支持 /embeddings 时才开)");
        root.addView(cbAux);

        cbVerbose = new CheckBox(this);
        cbVerbose.setText("详细日志 (每次取值都写 Xposed 日志)");
        root.addView(cbVerbose);

        Button btn = new Button(this);
        btn.setText("保存配置");
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(12);
        root.addView(btn, bp);

        TextView tip = new TextView(this);
        tip.setText("\n说明:\n• 路由/快速/专家各项留空 = 跟随主配置; 填了则独立生效\n• 自动模式按档位自动选: 快速/直答档→快速模型, 深度档→专家模型\n• 定时任务是本地实现(闹钟+cron), 解锁后由后台 AI 执行, 走你配置的模型\n• 生图/视频/文档生成走云端技能与独立接口, 不在本模块范围\n• 语音识别(ASR)与语音合成(TTS)仍走小米原服务\n• 自建后端需有效 TLS 证书(或 http 局域网地址)\n• 修改配置后请在 LSPosed 强制停止小爱同学再启动\n• 日志查看: LSPosed → 日志, 过滤 XiaoaiLLM");
        tip.setTextSize(12);
        tip.setTextColor(Color.GRAY);
        root.addView(tip);

        setContentView(scroll);
        load();
    }

    private TextView label(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setPadding(0, dp(12), 0, dp(4));
        return tv;
    }

    private void load() {
        SharedPreferences p = getPrefs();
        etUrl.setText(p.getString("base_url", ""));
        etKey.setText(p.getString("api_key", ""));
        etModel.setText(p.getString("model", ""));
        etFastModel.setText(p.getString("fast_model", ""));
        etProModel.setText(p.getString("pro_model", ""));
        etRouterUrl.setText(p.getString("router_base_url", ""));
        etRouterKey.setText(p.getString("router_api_key", ""));
        etRouterModel.setText(p.getString("router_model", ""));
        cbEnabled.setChecked(p.getBoolean("enabled", false));
        cbRouter.setChecked(p.getBoolean("hook_router", true));
        cbTimer.setChecked(p.getBoolean("unlock_timer", true));
        cbAux.setChecked(p.getBoolean("redirect_aux", false));
        cbVerbose.setChecked(p.getBoolean("verbose", false));
    }

    private void save() {
        SharedPreferences p = getPrefs();
        p.edit()
                .putString("base_url", etUrl.getText().toString().trim())
                .putString("api_key", etKey.getText().toString().trim())
                .putString("model", etModel.getText().toString().trim())
                .putString("fast_model", etFastModel.getText().toString().trim())
                .putString("pro_model", etProModel.getText().toString().trim())
                .putString("router_base_url", etRouterUrl.getText().toString().trim())
                .putString("router_api_key", etRouterKey.getText().toString().trim())
                .putString("router_model", etRouterModel.getText().toString().trim())
                .putBoolean("enabled", cbEnabled.isChecked())
                .putBoolean("hook_router", cbRouter.isChecked())
                .putBoolean("unlock_timer", cbTimer.isChecked())
                .putBoolean("redirect_aux", cbAux.isChecked())
                .putBoolean("verbose", cbVerbose.isChecked())
                .apply();

        // 兜底: 让 LSPosed(root) 之外的读取路径也能访问 (XSharedPreferences 主要靠 LSPosed 服务读取)
        try {
            File f = new File(getDataDir(), "shared_prefs/" + PREFS + ".xml");
            f.setReadable(true, false);
            getDataDir().setExecutable(true, false);
            new File(getDataDir(), "shared_prefs").setExecutable(true, false);
        } catch (Throwable ignored) {
        }

        Toast t = Toast.makeText(this, "已保存, 请强制停止并重启小爱同学", Toast.LENGTH_LONG);
        t.setGravity(Gravity.CENTER, 0, 0);
        t.show();
    }

    @SuppressWarnings({"WorldReadableFiles", "deprecation"})
    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, Context.MODE_WORLD_READABLE);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
