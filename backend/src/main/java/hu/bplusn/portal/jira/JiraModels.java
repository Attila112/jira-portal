package hu.bplusn.portal.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * A ScriptRunner végpontok (jira-scripts/portalEndpoints.groovy) JSON válaszainak Java megfelelői.
 * Az ismeretlen mezőket figyelmen kívül hagyjuk, így a Groovy oldal bővíthető a Java módosítása nélkül.
 */
public final class JiraModels {

    private JiraModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JiraUser(String key, String username, String displayName, String email, Boolean active)
            implements Serializable {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServerInfo(String baseUrl, String version, String zoneId, String serverTime) {
    }

    /** portalMe válasza. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MeResponse(JiraUser user, boolean admin, ServerInfo server) {
    }

    /** Egy jegy a dashboard listájában (portalMyIssues). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Issue(
            String key,
            String summary,
            String project,
            String projectName,
            String issueType,
            boolean subtask,
            String parentKey,
            String parentSummary,
            String status,
            String statusCategory,
            String priority,
            String resolution,
            String assignee,
            List<String> responsible,
            List<String> tester,
            String created,
            String updated,
            String resolved,
            String dueDate,
            List<String> reasons) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FieldNames(String responsible, String tester) {
    }

    /** portalMyIssues válasza. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IssuesResponse(
            ServerInfo server,
            FieldNames fieldNames,
            Integer total,
            Integer limit,
            List<Issue> issues,
            String jql,
            Long executionMs) {
    }

    /** Egy worklog sor (portalWorklogs). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorklogRow(
            String date,
            String issue,
            String issueSummary,
            String project,
            long seconds,
            String comment,
            String worklogId) {

        public double hours() {
            return seconds / 3600.0;
        }
    }

    /** portalWorklogs válasza. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorklogsResponse(
            ServerInfo server,
            JiraUser targetUser,
            int days,
            String startDate,
            String endDate,
            List<String> projects,
            String scope,
            String jql,
            Integer searchedIssueCount,
            Long scannedWorklogCount,
            Integer worklogCount,
            List<WorklogRow> rows,
            Map<String, Long> timings,
            Long totalExecutionMs) {
    }

    /** ScriptRunner hibaválasz (success=false). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorBody(String error, String stage) {
    }

    /** /rest/auth/1/session válasza. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthSession(SessionCookie session) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionCookie(String name, String value) {
    }
}
