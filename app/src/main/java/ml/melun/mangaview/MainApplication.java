package ml.melun.mangaview;

import android.content.Context;
import android.webkit.CookieManager;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.multidex.MultiDexApplication;

import org.acra.ACRA;
import org.acra.config.CoreConfigurationBuilder;
import org.acra.config.DialogConfigurationBuilder;
import org.acra.config.MailSenderConfigurationBuilder;
import org.acra.data.StringFormat;

import ml.melun.mangaview.mangaview.CustomHttpClient;



//@AcraCore(reportContent = { APP_VERSION_NAME, ANDROID_VERSION, PHONE_MODEL, STACK_TRACE, REPORT_ID})


public class MainApplication extends MultiDexApplication {
    public static CustomHttpClient httpClient;
    public static Preference p;
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        System.out.println("main app start");
        ACRA.init(this, new CoreConfigurationBuilder()
                .withBuildConfigClass(BuildConfig.class)
                .withReportFormat(StringFormat.JSON)
                .withPluginConfigurations(
                        new MailSenderConfigurationBuilder().withMailTo("mangaview@protonmail.com").build(),
                        new DialogConfigurationBuilder()
                                .withTitle("MangaView")
                                .withText(getResources().getText(R.string.acra_dialog_text).toString())
                                .withPositiveButtonText("확인")
                                .withNegativeButtonText("취소")
                                .build()
                ));
    }

    @Override
    public void onCreate() {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        p = new Preference(this);
        httpClient = new CustomHttpClient();
        restoreWebViewCookies();
        super.onCreate();
    }

    private void restoreWebViewCookies() {
        try {
            String url = p.getUrl();
            if(url == null || url.length() == 0)
                url = p.getDefUrl();
            if(url == null || !url.startsWith("http"))
                return;
            httpClient.setCookies(p.getSavedCookies());
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            String cookies = cookieManager.getCookie(url);
            httpClient.setCookies(cookies);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
