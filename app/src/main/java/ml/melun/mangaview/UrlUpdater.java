package ml.melun.mangaview;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static ml.melun.mangaview.MainApplication.httpClient;
import static ml.melun.mangaview.MainApplication.p;

public class UrlUpdater extends AsyncTask<Void, Void, Boolean> {
    private static final int MAX_INCREMENT_SCAN = 50;
    private static final int MAX_CONSECUTIVE_MISSES_AFTER_NEWER = 2;
    private static final Pattern MANATOKI_NUMBERED_URL = Pattern.compile("^(https?://manatoki)(\\d+)(\\.net)(/.*)?$");

    String result;
    String fetchUrl;
    boolean silent = false;
    Context c;
    UrlUpdaterCallback callback;
    public static volatile boolean running = false;

    public UrlUpdater(Context c){
        this.c = c;
        this.fetchUrl = p.getDefUrl();
    }

    public UrlUpdater(Context c, boolean silent, UrlUpdaterCallback callback, String defUrl){
        this.c = c;
        this.silent = silent;
        this.callback = callback;
        this.fetchUrl = defUrl;
    }

    protected void onPreExecute() {
        running = true;
        if(!silent)
            Toast.makeText(c, "Finding current site URL...", Toast.LENGTH_SHORT).show();
    }

    protected Boolean doInBackground(Void... params) {
        return fetch();
    }

    protected Boolean fetch(){
        try {
            String numberedUrl = findLatestNumberedManatoki();
            if(numberedUrl != null){
                result = numberedUrl;
                return true;
            }

            Response r = requestUrl(fetchUrl, false);
            if (r == null)
                return false;

            if (r.code() == 302) {
                result = normalizeUrl(r.header("Location"));
                r.close();
                return result != null;
            }
            r.close();
            return false;

        }catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    private Response requestUrl(String url, boolean fast) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", httpClient.agent);
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Referer", p.getUrl());
        String cookie = httpClient.getCookieHeader();
        if(cookie.length() > 0)
            headers.put("Cookie", cookie);
        if(!fast)
            return httpClient.get(normalizeInputUrl(url), headers);

        try {
            OkHttpClient fastClient = httpClient.client.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .build();
            Request.Builder builder = new Request.Builder()
                    .url(normalizeInputUrl(url))
                    .get();
            for(String k : headers.keySet()){
                builder.addHeader(k, headers.get(k));
            }
            return fastClient.newCall(builder.build()).execute();
        } catch (Exception e){
            if(!fast)
                e.printStackTrace();
            return null;
        }
    }

    private String findLatestNumberedManatoki() {
        Matcher matcher = MANATOKI_NUMBERED_URL.matcher(normalizeInputUrl(fetchUrl));
        if(!matcher.matches())
            return null;
        int number = Integer.parseInt(matcher.group(2));
        return findLatestNumberedManatoki(matcher.group(1), matcher.group(3), number);
    }

    private String findLatestNumberedManatoki(String prefix, String suffix, int seedNumber) {
        String latest = null;
        int latestNumber = -1;
        String firstReachable = null;
        int misses = 0;
        for(int number = seedNumber; number <= seedNumber + MAX_INCREMENT_SCAN; number++){
            String candidate = prefix + number + suffix;
            Response response = requestUrl(candidate, true);
            if(response == null){
                if(++misses >= MAX_CONSECUTIVE_MISSES_AFTER_NEWER)
                    break;
                continue;
            }

            int code = response.code();
            String location = response.header("Location");

            if(location != null && location.contains("manatoki")){
                response.close();
                latest = normalizeUrl(location);
                latestNumber = extractNumber(latest, number);
                misses = 0;
                continue;
            }

            if(isUsableStatus(code) && firstReachable == null)
                firstReachable = candidate;

            if(isUsableStatus(code) && isManatokiMainPage(response)){
                latest = candidate;
                latestNumber = number;
                misses = 0;
            }else{
                response.close();
                if(++misses >= MAX_CONSECUTIVE_MISSES_AFTER_NEWER)
                    break;
            }
        }
        return latest == null ? firstReachable : latest;
    }

    private boolean isManatokiMainPage(Response response) {
        try {
            String body = response.body().string();
            return body.contains("miso-post-gallery")
                    || body.contains("miso-post-list")
                    || body.contains("/comic/")
                    || body.contains("/webtoon/");
        } catch (Exception e) {
            return false;
        } finally {
            response.close();
        }
    }

    private boolean isUsableStatus(int code) {
        return code >= 200 && code < 400;
    }

    private String normalizeInputUrl(String url) {
        if(url == null || url.trim().length() == 0)
            return "";
        url = url.trim();
        int queryStart = url.indexOf("?");
        if(queryStart > -1)
            url = url.substring(0, queryStart);
        if(!url.startsWith("http://") && !url.startsWith("https://"))
            url = "https://" + url;
        return normalizeUrl(url);
    }

    private String normalizeUrl(String url) {
        if(url == null)
            return null;
        int protocolEnd = url.indexOf("://");
        int searchStart = protocolEnd >= 0 ? protocolEnd + 3 : 0;
        int pathStart = url.indexOf("/", searchStart);
        if(pathStart > 0)
            return url.substring(0, pathStart);
        return url;
    }

    private int extractNumber(String url, int fallback) {
        if(url == null)
            return fallback;
        Matcher matcher = MANATOKI_NUMBERED_URL.matcher(normalizeInputUrl(url));
        if(matcher.matches())
            return Integer.parseInt(matcher.group(2));
        return fallback;
    }

    protected void onPostExecute(Boolean r) {
        running = false;
        if(r && result !=null){
            p.setUrl(result);
            if(!silent)
                Toast.makeText(c, "Site URL set: " + result, Toast.LENGTH_SHORT).show();
            if(callback!=null)
                callback.callback(true);
        }else{
            if(!silent)
                Toast.makeText(c, "Could not find the current site URL. Try again later.", Toast.LENGTH_LONG).show();
            if(callback!=null)
                callback.callback(false);
        }
    }

    public interface UrlUpdaterCallback{
        void callback(boolean success);
    }
}
