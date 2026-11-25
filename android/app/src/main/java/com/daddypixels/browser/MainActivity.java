package com.daddypixels.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/** Daddy Pixels for Android: a small, fast WebView browser with the Pixels / Fallout skins. */
public class MainActivity extends Activity {

    static final String CARD_DROP_URL = "https://pking4111-sys.github.io/daddy-pixels/carddrop/";
    static final String RELEASES_API = "https://api.github.com/repos/pking4111-sys/daddy-pixels/releases";
    static final String HOME_PIXELS = "file:///android_asset/start_pixels.html";
    static final String HOME_FALLOUT = "file:///android_asset/start_fallout.html";
    static final int FILE_CHOOSER = 7;

    // ---- skin colours
    static class Skin {
        int bar, barText, field, fieldText, accent, page, border;
        Typeface font;
        boolean terminal;
    }

    SharedPreferences prefs;
    Skin skin;
    LinearLayout root, topBar;
    FrameLayout content;
    EditText urlBox;
    ProgressBar progress;
    TextView tabCount;
    final List<WebView> tabs = new ArrayList<>();
    WebView current;
    View fullscreenView;
    WebChromeClient.CustomViewCallback fullscreenCallback;
    ValueCallback<Uri[]> fileCallback;
    IntroView intro;
    final Handler ui = new Handler(Looper.getMainLooper());

    // =================================================================== lifecycle
    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        prefs = getSharedPreferences("dp", MODE_PRIVATE);
        skin = makeSkin(prefs.getBoolean("fallout", false));
        buildUi();

        Uri start = getIntent() != null ? getIntent().getData() : null;
        newTab(start != null ? start.toString() : homeUrl());

        if (prefs.getBoolean("intro", true) && saved == null) {
            intro = new IntroView(this, skin.terminal, skin.font, () -> {
                ((ViewGroup) intro.getParent()).removeView(intro);
                intro = null;
            });
            addContentView(intro, new ViewGroup.LayoutParams(-1, -1));
        }
        ui.postDelayed(() -> checkForUpdate(false), 4000);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getData() != null) newTab(intent.getData().toString());
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (WebView w : tabs) if (w != current) w.onPause();   // sleeping tabs
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (current != null) current.onResume();
    }

    @Override
    public void onBackPressed() {
        if (intro != null) { intro.finish(); return; }
        if (fullscreenView != null) { exitFullscreen(); return; }
        if (current != null && current.canGoBack()) { current.goBack(); return; }
        if (tabs.size() > 1) { closeTab(current); return; }
        super.onBackPressed();
    }

    // =================================================================== skins
    Skin makeSkin(boolean fallout) {
        Skin s = new Skin();
        s.terminal = fallout;
        try {
            s.font = Typeface.createFromAsset(getAssets(), fallout ? "fonts/VT323-Regular.ttf" : "fonts/Silkscreen-Regular.ttf");
        } catch (Exception e) {
            s.font = Typeface.MONOSPACE;
        }
        if (fallout) {
            s.bar = Color.parseColor("#07140b"); s.barText = Color.parseColor("#3cff7a");
            s.field = Color.parseColor("#0f2a17"); s.fieldText = Color.parseColor("#3cff7a");
            s.accent = Color.parseColor("#3cff7a"); s.page = Color.parseColor("#07140b");
            s.border = Color.parseColor("#1f9a48");
        } else {
            s.bar = Color.parseColor("#ffd3ea"); s.barText = Color.parseColor("#2f5fe0");
            s.field = Color.parseColor("#d3f1ff"); s.fieldText = Color.parseColor("#2f5fe0");
            s.accent = Color.parseColor("#ff6fb5"); s.page = Color.parseColor("#e3f5ff");
            s.border = Color.parseColor("#2f5fe0");
        }
        return s;
    }

    String homeUrl() { return skin.terminal ? HOME_FALLOUT : HOME_PIXELS; }

    int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    GradientDrawable box(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(2), stroke);
        return g;
    }

    TextView barButton(String label, View.OnClickListener click) {
        TextView b = new TextView(this);
        b.setText(skin.terminal ? "[" + label + "]" : label);
        b.setTypeface(skin.font, Typeface.BOLD);
        b.setTextColor(skin.barText);
        b.setTextSize(skin.terminal ? 22 : 16);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), 0, dp(8), 0);
        if (!skin.terminal) b.setBackground(box(Color.WHITE, skin.border));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(36));
        lp.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(lp);
        return b;
    }

    void buildUi() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(skin.terminal ? Color.BLACK : skin.bar);
            w.setNavigationBarColor(skin.terminal ? Color.BLACK : skin.bar);
            if (!skin.terminal && Build.VERSION.SDK_INT >= 23)
                w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(skin.page);

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(4), dp(6), dp(4), dp(6));
        topBar.setBackground(skin.terminal ? box(skin.bar, skin.border) : box(skin.bar, skin.border));

        topBar.addView(barButton("<", v -> { if (current != null && current.canGoBack()) current.goBack(); }));
        topBar.addView(barButton(">", v -> { if (current != null && current.canGoForward()) current.goForward(); }));

        urlBox = new EditText(this);
        urlBox.setSingleLine(true);
        urlBox.setHint(skin.terminal ? "> ENTER ADDRESS_" : "search or type a URL...");
        urlBox.setHintTextColor(skin.terminal ? Color.parseColor("#1f9a48") : Color.parseColor("#7f9be8"));
        urlBox.setTextColor(skin.fieldText);
        urlBox.setTypeface(skin.terminal ? skin.font : Typeface.MONOSPACE);
        urlBox.setTextSize(skin.terminal ? 20 : 14);
        urlBox.setBackground(box(skin.field, skin.border));
        urlBox.setPadding(dp(8), 0, dp(8), 0);
        urlBox.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        urlBox.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBox.setSelectAllOnFocus(true);
        urlBox.setOnEditorActionListener((v, id, ev) -> {
            go(urlBox.getText().toString());
            return true;
        });
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        ulp.setMargins(dp(3), 0, dp(3), 0);
        topBar.addView(urlBox, ulp);

        tabCount = barButton("1", v -> showTabs());
        topBar.addView(tabCount);
        topBar.addView(barButton(skin.terminal ? "≡" : "☰", this::showMenu));
        root.addView(topBar, new LinearLayout.LayoutParams(-1, -2));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        if (Build.VERSION.SDK_INT >= 21) {
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(skin.accent));
            progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(skin.page));
        }
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
    }

    void setSkin(boolean fallout) {
        prefs.edit().putBoolean("fallout", fallout).apply();
        boolean onHome = current != null && isHome(current.getUrl());
        skin = makeSkin(fallout);
        for (WebView t : tabs) ((ViewGroup) t.getParent()).removeView(t);
        buildUi();
        for (WebView t : tabs) { content.addView(t, new FrameLayout.LayoutParams(-1, -1)); t.setVisibility(t == current ? View.VISIBLE : View.GONE); }
        updateTabCount();
        if (onHome) current.loadUrl(homeUrl());
        updateUrlBox();
    }

    // =================================================================== tabs
    void newTab(String url) {
        WebView w = makeWebView();
        tabs.add(w);
        content.addView(w, new FrameLayout.LayoutParams(-1, -1));
        switchTo(w);
        go(url);
    }

    void switchTo(WebView w) {
        for (WebView t : tabs) {
            t.setVisibility(t == w ? View.VISIBLE : View.GONE);
            if (t == w) t.onResume(); else t.onPause();
        }
        current = w;
        updateTabCount();
        updateUrlBox();
    }

    void closeTab(WebView w) {
        int i = tabs.indexOf(w);
        tabs.remove(w);
        content.removeView(w);
        w.destroy();
        if (tabs.isEmpty()) { newTab(homeUrl()); return; }
        switchTo(tabs.get(Math.max(0, i - 1)));
    }

    void updateTabCount() { tabCount.setText(skin.terminal ? "[" + tabs.size() + "]" : String.valueOf(tabs.size())); }

    void showTabs() {
        String[] names = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            WebView t = tabs.get(i);
            String title = isHome(t.getUrl()) ? "NEW TAB" : (t.getTitle() == null || t.getTitle().isEmpty() ? t.getUrl() : t.getTitle());
            names[i] = (t == current ? "▶ " : "   ") + title;
        }
        new AlertDialog.Builder(this)
                .setTitle("TABS")
                .setItems(names, (d, i) -> switchTo(tabs.get(i)))
                .setPositiveButton("+ NEW TAB", (d, i) -> newTab(homeUrl()))
                .setNegativeButton("CLOSE THIS TAB", (d, i) -> closeTab(current))
                .show();
    }

    // =================================================================== navigation
    boolean isHome(String url) { return url != null && url.startsWith("file:///android_asset/start_"); }

    void go(String text) {
        text = text == null ? "" : text.trim();
        String url;
        if (text.isEmpty()) url = homeUrl();
        else if (text.equals("daddypixels://carddrop")) url = CARD_DROP_URL;
        else if (text.startsWith("file:///android_asset/") || text.matches("(?i)^(https?|about|data):.*")) url = text;
        else if (text.matches("(?i)^[^\\s]+\\.[a-z]{2,}(:\\d+)?(/.*)?$")) url = "https://" + text;
        else url = "https://duckduckgo.com/?q=" + Uri.encode(text);
        if (current != null) {
            if (url.startsWith("http") && handleUrl(current, url, true)) { urlBox.clearFocus(); return; }
            current.loadUrl(url);
            current.requestFocus();
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlBox.getWindowToken(), 0);
        urlBox.clearFocus();
    }

    void updateUrlBox() {
        if (current == null || urlBox.hasFocus()) return;
        String u = current.getUrl();
        urlBox.setText(u == null || isHome(u) || u.startsWith("data:") ? "" : u);
    }

    WebView makeWebView() {
        WebView w = new WebView(this);
        w.setBackgroundColor(skin.page);
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setAllowFileAccess(false);           // assets still work; no reading phone files from web pages
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        if (prefs.getBoolean("desktop", false)) s.setUserAgentString(desktopUa());
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().setAcceptThirdPartyCookies(w, !prefs.getBoolean("adblock", true));

        w.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return handleUrl(view, req.getUrl().toString(), req.isForMainFrame());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(view, url, true);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                if (prefs.getBoolean("adblock", true) && !req.isForMainFrame()) {
                    String host = req.getUrl().getHost();
                    if (host != null && AdBlock.blocked(host.toLowerCase()))
                        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (view == current) updateUrlBox();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (view == current) updateUrlBox();
            }
        });

        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                if (view != current) return;
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.INVISIBLE : View.VISIBLE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (view == current) updateUrlBox();
            }

            @Override
            public void onShowCustomView(View v, CustomViewCallback cb) {
                if (fullscreenView != null) { cb.onCustomViewHidden(); return; }
                fullscreenView = v;
                fullscreenCallback = cb;
                ((ViewGroup) getWindow().getDecorView()).addView(v, new FrameLayout.LayoutParams(-1, -1));
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }

            @Override
            public void onHideCustomView() { exitFullscreen(); }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        w.setDownloadListener((url, ua, disposition, mime, length) -> {
            try {
                DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                String name = URLUtil.guessFileName(url, disposition, mime);
                r.setMimeType(mime);
                r.addRequestHeader("User-Agent", ua);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) r.addRequestHeader("Cookie", cookies);
                r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                toast("Downloading " + name);
            } catch (Exception e) {
                openOutside(url);
            }
        });
        return w;
    }

    /** Decides what happens when a page navigates somewhere. true = we handled it. */
    boolean handleUrl(WebView view, String url, boolean mainFrame) {
        Uri u = Uri.parse(url);
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
        if (url.equals("daddypixels://carddrop")) { view.loadUrl(CARD_DROP_URL); return true; }
        if (scheme.equals("daddypixels") && "continue".equals(u.getHost())) {
            String target = u.getQueryParameter("to");
            if (target != null) { Shield.allow(Uri.parse(target).getHost()); view.loadUrl(target); }
            return true;
        }
        if (scheme.equals("daddypixels") && "back".equals(u.getHost())) {
            if (view.canGoBack()) view.goBack(); else view.loadUrl(homeUrl());
            return true;
        }
        if (!scheme.equals("http") && !scheme.equals("https")) {
            if (scheme.equals("file") || scheme.equals("about") || scheme.equals("data")) return false;
            openOutside(url);    // tel:, mailto:, intent:, market: ...
            return true;
        }
        if (mainFrame && prefs.getBoolean("shield", true)) {
            String why = Shield.check(u);
            if (why != null) {
                view.loadDataWithBaseURL("https://daddypixels.warning/", Shield.warningPage(url, why, skin.terminal),
                        "text/html", "utf-8", null);
                return true;
            }
        }
        return false;
    }

    void openOutside(String url) {
        try {
            Intent i;
            if (url.startsWith("intent:")) {
                i = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setComponent(null);
                i.setSelector(null);
            } else {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
            startActivity(i);
        } catch (Exception e) {
            toast("No app can open that link");
        }
    }

    void exitFullscreen() {
        if (fullscreenView == null) return;
        ((ViewGroup) getWindow().getDecorView()).removeView(fullscreenView);
        fullscreenView = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(!skin.terminal && Build.VERSION.SDK_INT >= 23
                ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0);
        if (fullscreenCallback != null) fullscreenCallback.onCustomViewHidden();
        fullscreenCallback = null;
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request == FILE_CHOOSER && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(request, result, data);
    }

    // =================================================================== menu
    void showMenu(View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "New tab");
        m.getMenu().add(0, 2, 0, "Home");
        m.getMenu().add(0, 3, 0, "Reload");
        m.getMenu().add(0, 4, 0, "♥ Card Drop");
        m.getMenu().add(0, 5, 0, skin.terminal ? "Skin: switch to Daddy Pixels" : "Skin: switch to Daddy Fallout");
        m.getMenu().add(0, 6, 0, (prefs.getBoolean("adblock", true) ? "✓ " : "   ") + "Ad + tracker blocker");
        m.getMenu().add(0, 7, 0, (prefs.getBoolean("shield", true) ? "✓ " : "   ") + "Scam / phishing shield");
        m.getMenu().add(0, 8, 0, (prefs.getBoolean("desktop", false) ? "✓ " : "   ") + "Desktop site");
        m.getMenu().add(0, 9, 0, (prefs.getBoolean("intro", true) ? "✓ " : "   ") + "Startup intro");
        m.getMenu().add(0, 10, 0, "Share page");
        m.getMenu().add(0, 11, 0, "Clear history + cookies");
        m.getMenu().add(0, 12, 0, "Check for updates");
        m.getMenu().add(0, 13, 0, "About");
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: newTab(homeUrl()); break;
                case 2: current.loadUrl(homeUrl()); break;
                case 3: current.reload(); break;
                case 4: current.loadUrl(CARD_DROP_URL); break;
                case 5: setSkin(!skin.terminal); break;
                case 6: toggle("adblock", true, "Ad blocker"); break;
                case 7: toggle("shield", true, "Phishing shield"); break;
                case 8:
                    boolean d = toggle("desktop", false, "Desktop site");
                    for (WebView t : tabs) t.getSettings().setUserAgentString(d ? desktopUa() : null);
                    current.reload();
                    break;
                case 9: toggle("intro", true, "Startup intro"); break;
                case 10:
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT, current.getUrl());
                    startActivity(Intent.createChooser(share, "Share"));
                    break;
                case 11:
                    for (WebView t : tabs) { t.clearHistory(); t.clearCache(true); }
                    CookieManager.getInstance().removeAllCookies(null);
                    android.webkit.WebStorage.getInstance().deleteAllData();
                    toast("History, cache and cookies cleared");
                    break;
                case 12: checkForUpdate(true); break;
                case 13:
                    new AlertDialog.Builder(this).setTitle("DADDY PIXELS")
                            .setMessage("Version " + BuildConfig.VERSION_NAME + "\nSurf the web, pixel by pixel.\n\nMade for fun by pking4111-sys.")
                            .setPositiveButton("OK", null).show();
                    break;
            }
            return true;
        });
        m.show();
    }

    boolean toggle(String key, boolean def, String label) {
        boolean v = !prefs.getBoolean(key, def);
        prefs.edit().putBoolean(key, v).apply();
        toast(label + (v ? " ON" : " OFF"));
        return v;
    }

    String desktopUa() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    // =================================================================== updater
    /** Looks for a newer Daddy Pixels APK on the GitHub releases page. */
    void checkForUpdate(boolean manual) {
        if (manual) toast("Checking for updates...");
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(RELEASES_API).openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                InputStream in = c.getInputStream();
                Scanner sc = new Scanner(in, "UTF-8").useDelimiter("\\A");
                JSONArray releases = new JSONArray(sc.hasNext() ? sc.next() : "[]");
                in.close();
                String bestVer = null, bestUrl = null;
                for (int i = 0; i < releases.length(); i++) {
                    JSONObject r = releases.getJSONObject(i);
                    if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue;
                    JSONArray assets = r.optJSONArray("assets");
                    if (assets == null) continue;
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject a = assets.getJSONObject(j);
                        String name = a.optString("name");
                        if (!name.toLowerCase().endsWith(".apk")) continue;
                        String ver = versionFromName(name, r.optString("tag_name"));
                        if (bestVer == null || newer(ver, bestVer)) { bestVer = ver; bestUrl = a.optString("browser_download_url"); }
                    }
                }
                final String ver = bestVer, url = bestUrl;
                ui.post(() -> {
                    if (ver != null && newer(ver, BuildConfig.VERSION_NAME)) {
                        new AlertDialog.Builder(this).setTitle("UPDATE AVAILABLE")
                                .setMessage("Daddy Pixels " + ver + " is out (you have " + BuildConfig.VERSION_NAME + ").\n\n"
                                        + "Tap DOWNLOAD, then open the file to install it over this version.")
                                .setPositiveButton("DOWNLOAD", (d, i) -> openOutside(url))
                                .setNegativeButton("LATER", null).show();
                    } else if (manual) {
                        toast("You're on the newest version (" + BuildConfig.VERSION_NAME + ")");
                    }
                });
            } catch (Exception e) {
                if (manual) ui.post(() -> toast("Couldn't check for updates"));
            }
        }).start();
    }

    /** "DaddyPixels-1.0.4.apk" -> 1.0.4, otherwise the release tag (v1.0.4 -> 1.0.4). */
    static String versionFromName(String name, String tag) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)+)").matcher(name);
        if (m.find()) return m.group(1);
        return tag.replaceFirst("^[vV]", "");
    }

    static boolean newer(String a, String b) {
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? parse(x[i]) : 0, q = i < y.length ? parse(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    static int parse(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (Exception e) { return 0; }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) { showMenu(topBar); return true; }
        return super.onKeyDown(keyCode, event);
    }
}
