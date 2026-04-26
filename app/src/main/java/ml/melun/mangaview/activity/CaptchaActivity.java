package ml.melun.mangaview.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.net.MalformedURLException;
import java.net.URL;

import ml.melun.mangaview.R;
import ml.melun.mangaview.Utils;

import static ml.melun.mangaview.MainApplication.httpClient;
import static ml.melun.mangaview.MainApplication.p;
import static ml.melun.mangaview.Utils.showErrorPopup;
import static ml.melun.mangaview.Utils.showPopup;

public class CaptchaActivity extends AppCompatActivity {
    WebView webView;
    public static final int RESULT_CAPTCHA = 15;
    public static final int REQUEST_CAPTCHA = 32;
    String domain;
    String staleClearance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        super.onCreate(savedInstanceState);
        Context context = this;
        setContentView(R.layout.activity_captcha);

        String purl = p.getUrl();

        Intent intent = getIntent();
        String path = intent.getStringExtra("url");
        String url = buildUrl(purl, path == null ? "" : path);

        TextView infoText = this.findViewById(R.id.infoText);
        try {
            URL u = new URL(purl);
            domain = u.getHost();
        } catch (MalformedURLException e) {
            showErrorPopup(context, "Invalid URL.", e, true);
        }

        if (purl.contains("http://")) {
            showErrorPopup(context, "Use an https URL for captcha verification.", null, false);
        }

        webView = this.findViewById(R.id.captchaWebView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        CookieManager cookiem = CookieManager.getInstance();
        cookiem.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookiem.setAcceptThirdPartyCookies(webView, true);
        }
        String cookieHeader = httpClient.getCookieHeader();
        staleClearance = extractCookie(cookieHeader, "cf_clearance");
        String webViewCookie = cookiem.getCookie(purl);
        String webViewClearance = extractCookie(webViewCookie, "cf_clearance");
        if (staleClearance == null)
            staleClearance = webViewClearance;
        if (cookieHeader.length() > 0) {
            for (String cookie : cookieHeader.split("; ")) {
                if (!cookie.startsWith("cf_clearance="))
                    cookiem.setCookie(purl, cookie);
            }
            flushCookies(cookiem);
        }
        clearClearanceCookie(cookiem, purl);

        WebViewClient client = new WebViewClient() {

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && !request.isForMainFrame())
                    return;
                showPopup(context, "Error", "Connection failed. Please check the URL.");
            }

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String agent = request.getRequestHeaders().get("User-Agent");
                if (agent != null && agent.length() > 0) {
                    httpClient.agent = agent;
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                syncCookies(cookiem, purl);
                super.onLoadResource(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                syncCookies(cookiem, purl);
                finishIfVerified(cookiem, purl);
            }
        };

        webView.setWebViewClient(client);
        webView.loadUrl(url);

        new Handler(Looper.getMainLooper()).postDelayed(() -> infoText.setVisibility(View.VISIBLE), 3000);
    }

    private void syncCookies(CookieManager cookiem, String url) {
        try {
            String cookieStr = cookiem.getCookie(url);
            httpClient.setCookies(cookieStr);
            String clearance = extractCookie(cookieStr, "cf_clearance");
            if (clearance != null && clearance.equals(staleClearance))
                httpClient.removeCookie("cf_clearance");
            flushCookies(cookiem);
        } catch (Exception e) {
            Utils.showErrorPopup(this, "Failed to sync captcha cookies.", e, true);
        }
    }

    private void clearClearanceCookie(CookieManager cookiem, String url) {
        httpClient.removeCookie("cf_clearance");
        cookiem.setCookie(url, "cf_clearance=; Max-Age=0; Path=/");
        cookiem.setCookie(url, "cf_clearance=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/");
        flushCookies(cookiem);
    }

    private String buildUrl(String base, String path) {
        if (path.startsWith("http://") || path.startsWith("https://"))
            return path;
        if (base.endsWith("/") && path.startsWith("/"))
            return base + path.substring(1);
        if (!base.endsWith("/") && !path.startsWith("/"))
            return base + "/" + path;
        return base + path;
    }

    private void finishIfVerified(CookieManager cookiem, String cookieUrl) {
        String cookieStr = cookiem.getCookie(cookieUrl);
        String clearance = extractCookie(cookieStr, "cf_clearance");
        if (clearance != null && !clearance.equals(staleClearance)) {
            finishCaptcha();
        }
    }

    private String extractCookie(String cookieStr, String name) {
        if (cookieStr == null)
            return null;
        for (String cookie : cookieStr.split("; ")) {
            int split = cookie.indexOf("=");
            if (split <= 0)
                continue;
            if (cookie.substring(0, split).equals(name))
                return cookie.substring(split + 1);
        }
        return null;
    }

    private void finishCaptcha() {
        Intent resultIntent = new Intent();
        setResult(RESULT_CAPTCHA, resultIntent);
        finish();
    }

    private void flushCookies(CookieManager cookiem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookiem.flush();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ((ConstraintLayout) findViewById(R.id.captchaContainer)).removeAllViews();
        webView.clearHistory();
        webView.clearCache(true);
        webView.destroy();
    }

    @Override
    public void finish() {
        super.finish();
        this.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
