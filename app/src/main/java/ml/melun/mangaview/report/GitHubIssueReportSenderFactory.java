package ml.melun.mangaview.report;

import android.content.Context;

import org.acra.config.CoreConfiguration;
import org.acra.sender.ReportSender;
import org.acra.sender.ReportSenderFactory;

public class GitHubIssueReportSenderFactory implements ReportSenderFactory {
    @Override
    public ReportSender create(Context context, CoreConfiguration config) {
        return new GitHubIssueReportSender();
    }
}
