package ml.melun.mangaview.report;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.acra.ReportField;
import org.acra.data.CrashReportData;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderException;

import ml.melun.mangaview.BuildConfig;

public class GitHubIssueReportSender implements ReportSender {
    private static final String ISSUE_URL = "https://github.com/yerusalom93/MangaViewAndroid/issues/new";
    private static final int MAX_STACK_LENGTH = 7000;

    @Override
    public void send(Context context, CrashReportData report) throws ReportSenderException {
        String stackTrace = value(report, ReportField.STACK_TRACE);
        String title = "[Crash] " + firstMeaningfulLine(stackTrace);
        String body = buildIssueBody(report, stackTrace);

        Uri uri = Uri.parse(ISSUE_URL).buildUpon()
                .appendQueryParameter("title", title)
                .appendQueryParameter("body", body)
                .build();
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            throw new ReportSenderException("Could not open GitHub issue report", e);
        }
    }

    @Override
    public boolean requiresForeground() {
        return true;
    }

    private String buildIssueBody(CrashReportData report, String stackTrace) {
        StringBuilder body = new StringBuilder();
        body.append("## Crash report\n\n");
        body.append("- App version: ").append(valueOrDefault(report, ReportField.APP_VERSION_NAME, BuildConfig.VERSION_NAME)).append('\n');
        body.append("- Version code: ").append(valueOrDefault(report, ReportField.APP_VERSION_CODE, String.valueOf(BuildConfig.VERSION_CODE))).append('\n');
        body.append("- Android: ").append(value(report, ReportField.ANDROID_VERSION)).append('\n');
        body.append("- Device: ").append(value(report, ReportField.PHONE_MODEL)).append('\n');
        body.append("- Report ID: ").append(value(report, ReportField.REPORT_ID)).append("\n\n");
        body.append("## Stack trace\n\n```text\n");
        body.append(limit(stackTrace, MAX_STACK_LENGTH));
        body.append("\n```\n\n");
        body.append("## Notes\n\n");
        body.append("앱에서 자동 생성된 오류 리포트입니다. 오류가 발생하기 직전에 한 동작을 여기에 추가로 적어주세요.\n");
        return body.toString();
    }

    private String firstMeaningfulLine(String stackTrace) {
        if(stackTrace == null || stackTrace.trim().length() == 0)
            return "Unknown crash";
        String[] lines = stackTrace.split("\\r?\\n");
        for(String line : lines) {
            String trimmed = line.trim();
            if(trimmed.length() > 0 && !trimmed.startsWith("at "))
                return limit(trimmed, 120);
        }
        return limit(lines[0].trim(), 120);
    }

    private String valueOrDefault(CrashReportData report, ReportField field, String defaultValue) {
        String value = value(report, field);
        return value.length() > 0 ? value : defaultValue;
    }

    private String value(CrashReportData report, ReportField field) {
        String value = report.getString(field);
        return value == null ? "" : value;
    }

    private String limit(String value, int maxLength) {
        if(value == null)
            return "";
        if(value.length() <= maxLength)
            return value;
        return value.substring(0, maxLength) + "\n... truncated ...";
    }
}
