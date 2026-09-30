package com.wink.xgjhome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 嗅探页：复刻抖音直播解析主页面（WebView + 顶栏 + 右下角半透明FAB + 记录面板） */
public class SniffActivity extends Activity {

    private static final Pattern MEDIA = Pattern.compile(
        "\\.(m3u8|mp4|flv|mkv|avi|ts|webm|mp3|m4a|aac|flac|mov)(\\?|$)|\\.ts\\?|/stream/|media-worker", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_IN_TEXT = Pattern.compile(
        "(https?://|www\\.)[\\w\\-./?:#=&%+~@!$'*;,\\[\\]]+", Pattern.CASE_INSENSITIVE);

    private WebView webView;
    private EditText etUrl;
    private LinearLayout topBar;
    private LinearLayout bottomPanel;
    private LinearLayout layoutRecords;
    private View fab;
    private Button btnSwitchUa;

    private boolean isMobileUa = false;
    private boolean isRecordsVisible = false;
    private boolean isTopBarVisible = true;

    private final ArrayList<String> foundUrls = new ArrayList<>();
    private final LinkedHashSet<String> recordKeys = new LinkedHashSet<>();

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sniff);

        webView = findViewById(R.id.webview);
        etUrl = findViewById(R.id.et_url);
        topBar = findViewById(R.id.top_bar);
        bottomPanel = findViewById(R.id.bottom_panel);
        layoutRecords = findViewById(R.id.layout_records);
        fab = findViewById(R.id.btn_toggle_top);
        btnSwitchUa = findViewById(R.id.btn_switch_ua);
        Button btnGo = findViewById(R.id.btn_go);
        Button btnRecords = findViewById(R.id.btn_records);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    if (!"GET".equalsIgnoreCase(request.getMethod())) return null;
                    Uri u = request.getUrl();
                    if (!"http".equals(u.getScheme()) && !"https".equals(u.getScheme())) return null;
                    String url = u.toString();
                    if (isMediaUrl(url) && recordKeys.add(url)) {
                        foundUrls.add(url);
                        final String page = webView.getTitle();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                addRecord(url, page == null ? "" : page);
                                if (!isRecordsVisible) toggleRecords();
                            }
                        });
                    }
                } catch (Exception ignored) {
                }
                return null;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                etUrl.setText(url);
            }
        });

        // 回车按钮（及键盘 Go）：打开网址（B站/抖音/其他网站都能进）
        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openInputUrl();
            }
        });
        etUrl.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                openInputUrl();
                return true;
            }
        });

        btnSwitchUa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchUa();
            }
        });
        btnRecords.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRecords();
            }
        });

        // FAB：切换顶部栏
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTopBar();
            }
        });

        // 点屏幕（非FAB区域）→ FAB 出现；再点一下 → 消失
        webView.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    x0 = event.getX(); y0 = event.getY();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (Math.abs(event.getX() - x0) < 12 && Math.abs(event.getY() - y0) < 12) {
                        fab.setVisibility(fab.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                    }
                }
                return false;
            }
        });

        webView.loadUrl("https://live.douyin.com/");
    }

    void openInputUrl() {
        String raw = etUrl.getText().toString().trim();
        if (raw.length() == 0) return;

        String url = extractUrl(raw);
        if (url == null) {
            if (raw.matches("\\d+")) {
                url = "https://live.douyin.com/" + raw;
            } else {
                return;
            }
        }
        if (!url.startsWith("http")) url = "https://" + url;
        webView.loadUrl(url);
    }

    static String extractUrl(String text) {
        Matcher m = URL_IN_TEXT.matcher(text);
        if (m.find()) {
            String u = m.group();
            if (u.startsWith("www.")) u = "https://" + u;
            return u;
        }
        if (text.matches("[\\w\\-./?:#=&%+~@!$'*;,\\[\\]]+")) return text;
        return null;
    }

    static boolean isMediaUrl(String url) {
        return MEDIA.matcher(url.toLowerCase()).find();
    }

    void switchUa() {
        if (isMobileUa) {
            webView.getSettings().setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            btnSwitchUa.setText("切手机UA");
            btnSwitchUa.setBackgroundColor(0x4D3742fa);
            isMobileUa = false;
        } else {
            webView.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
            btnSwitchUa.setText("切电脑UA");
            btnSwitchUa.setBackgroundColor(0x4D2ed573);
            isMobileUa = true;
        }
        webView.reload();
    }

    void toggleRecords() {
        if (isRecordsVisible) {
            bottomPanel.setVisibility(View.GONE);
            ((Button) findViewById(R.id.btn_records)).setBackgroundColor(0x4Dff4757);
            isRecordsVisible = false;
        } else {
            bottomPanel.setVisibility(View.VISIBLE);
            ((Button) findViewById(R.id.btn_records)).setBackgroundColor(0x4Dff4757);
            isRecordsVisible = true;
        }
    }

    void toggleTopBar() {
        isTopBarVisible = !isTopBarVisible;
        topBar.setVisibility(isTopBarVisible ? View.VISIBLE : View.GONE);
    }

    void addRecord(final String url, String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(16, 12, 16, 12);
        row.setBackgroundColor(0x33222244);

        TextView tv = new TextView(this);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(13);
        String label = (title == null || title.length() == 0) ? "嗅探 " + (foundUrls.size()) : title;
        tv.setText(label);
        tv.setSingleLine(true);
        row.addView(tv);

        TextView tvUrl = new TextView(this);
        tvUrl.setTextColor(0xFF8AB4F8);
        tvUrl.setTextSize(11);
        tvUrl.setText(url);
        tvUrl.setSingleLine(true);
        row.addView(tvUrl);

        // 点行复制，长按删除
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("url", url));
                Toast.makeText(SniffActivity.this, "已复制", Toast.LENGTH_SHORT).show();
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ViewGroup p = (ViewGroup) v.getParent();
                if (p != null) p.removeView(v);
                recordKeys.remove(url);
                foundUrls.remove(url);
                Toast.makeText(SniffActivity.this, "已删除", Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        layoutRecords.addView(row, 0);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
