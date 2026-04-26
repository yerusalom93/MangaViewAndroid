package ml.melun.mangaview.activity;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.Map;

import ml.melun.mangaview.R;
import ml.melun.mangaview.Utils;
import ml.melun.mangaview.mangaview.Login;

import static ml.melun.mangaview.MainApplication.httpClient;
import static ml.melun.mangaview.MainApplication.p;
import static ml.melun.mangaview.Utils.showErrorPopup;
import static ml.melun.mangaview.Utils.showPopup;

public class CaptchaActivity extends AppCompatActivity {
    WebView webView;
    public static final int RESULT_CAPTCHA = 15;
    public static final int REQUEST_CAPTCHA = 32;
    String domain;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        super.onCreate(savedInstanceState);
        Context context = this;
        setContentView(R.layout.activity_captcha);

        String purl = p.getUrl();

        Intent intent = getIntent();
        String path = intent.getStringExtra("url");
        String url = purl + (path == null ? "" : path);

        TextView infoText = this.findViewById(R.id.infoText);
        try {
            URL u = new URL(purl);
            domain = u.getHost();
        }catch (MalformedURLException e){
            showErrorPopup(context, "URL 형식이 올바르지 않습니다.", e, true);
        }

        if(purl.contains("http://")){
            showErrorPopup(context, "ip 주소 혹은 잘못된 주소를 사용중입니다. 자동 URL 설정을 사용하거나, 주소를 다시 입력해 주세요", null, false);
        }

        webView = this.findViewById(R.id.captchaWebView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        CookieManager cookiem = CookieManager.getInstance();
        cookiem.setAcceptCookie(true);

        // Keep the current app session in WebView so CAPTCHA verification is performed
        // on the same server session.
        if (httpClient.getCookie("PHPSESSID") != null) {
            cookiem.setCookie(purl, "PHPSESSID=" + httpClient.getCookie("PHPSESSID") + "; path=/");
        }

        WebViewClient client = new WebViewClient() {
            private boolean captchaDone = false;
            private boolean captchaPageDetected = false;

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                //super.onReceivedError(view, request, error);
                showPopup(context, "오류", "연결에 실패했습니다. URL을 확인해 주세요");
            }

            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                httpClient.agent = request.getRequestHeaders().get("User-Agent");
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (captchaDone || url == null) return;
                String lowerUrl = url.toLowerCase(Locale.ROOT);
                // CAPTCHA page itself can load bootstrap/jquery resources, so do not
                // close on resource load; close only after navigating away from challenge.
                if (lowerUrl.contains("captcha")) {
                    captchaPageDetected = true;
                    return;
                }
                // Prevent instant close on initial non-captcha loads. Only finish after
                // at least one captcha page was actually shown, then moved away from it.
                if (!captchaPageDetected) return;

                // read cookies and finish
                try {
                    String cookieStr = cookiem.getCookie(purl);
                    if (cookieStr != null && cookieStr.length() > 0) {
                        for (String s : cookieStr.split("; ")) {
                            int idx = s.indexOf("=");
                            if (idx <= 0 || idx >= s.length() - 1) continue;
                            String k = s.substring(0, idx);
                            String v = s.substring(idx + 1);
                            httpClient.setCookie(k, v);
                        }
                    }
                    captchaDone = true;
                    Intent resultIntent = new Intent();
                    setResult(RESULT_CAPTCHA, resultIntent);
                    finish();
                } catch (Exception e) {
                    Utils.showErrorPopup(context, "인증 도중 오류가 발생했습니다. 네트워크 연결 상태를 확인해주세요.", e, true);
                }
            }
        };

        webView.setWebViewClient(client);

//        webView.setOnTouchListener((view, motionEvent) -> true);

//        Login login = p.getLogin();
//        if(login != null && login.getCookie() !=null && login.getCookie().length()>0){
//            //session exists
//            cookiem.setCookie(purl, login.getCookie(true));
//        }

        webView.loadUrl(url);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            //Do something after 100ms
            infoText.setVisibility(View.VISIBLE);
        }, 3000);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //destroy webview
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
