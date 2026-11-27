/*
 * ÖSSZEFOGLALÓ:
 * ScriptRunner REST Endpointok a "Jira Portal" webalkalmazáshoz (Jira Data Center 10.x, ScriptRunner 9.x).
 *
 * Végpontok (mind GET, JSON választ adnak):
 *
 *   1. portalMe
 *      /rest/scriptrunner/latest/custom/portalMe
 *      A bejelentkezett felhasználó adatai + admin jogosultság (jira-administrators tagság).
 *      A webalkalmazás ezzel ellenőrzi a belépést és ebből tudja, ki admin.
 *
 *   2. portalMyIssues
 *      /rest/scriptrunner/latest/custom/portalMyIssues
 *      A dashboard "saját jegyek" listája. A JQL a hívó felhasználóra (currentUser()) fut.
 *
 *   3. portalWorklogs
 *      /rest/scriptrunner/latest/custom/portalWorklogs?days=30[&user=x][&projects=AJI,BNSD][&scope=authored|assigned]
 *      Worklog sorok egy felhasználóra.
 *        - user nélkül: a hívó saját worklogjai (bárki használhatja);
 *        - másik user megadása: csak jira-administrators tagoknak.
 *        - scope=authored (alapértelmezett): minden issue, amire a felhasználó logolt (worklogAuthor JQL);
 *          scope=assigned: a régi worklogReport logikája ("Munka felelőse" vagy assignee szerinti issue-k).
 *      Az összesítéseket (átlag, havi keret, stb.) a webalkalmazás backendje számolja.
 *
 * Jogosultság: a végpontok nem használnak "groups" korlátozást, mert minden Jira-felhasználónak
 * a saját adatait kell látnia. Anonim hívást mindhárom 401-gyel elutasít, a más felhasználóra
 * vonatkozó worklog-lekérést pedig a kód maga ellenőrzi (403).
 *
 * A régi worklogReport endpointot ez a fájl NEM módosítja, az továbbra is működik a Python alkalmazásnak.
 */

import com.atlassian.jira.bc.issue.search.SearchService
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.config.properties.APKeys
import com.atlassian.jira.issue.Issue
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.user.ApplicationUser
import com.atlassian.jira.util.BuildUtilsInfo
import com.atlassian.jira.web.bean.PagerFilter
import com.onresolve.scriptrunner.runner.rest.common.CustomEndpointDelegate

import groovy.json.JsonOutput
import groovy.transform.BaseScript

import java.time.LocalDate
import java.time.ZoneId

import javax.ws.rs.core.MediaType
import javax.ws.rs.core.MultivaluedMap
import javax.ws.rs.core.Response

@BaseScript CustomEndpointDelegate delegate

/* ------------------------------------------------------------------ */
/* Beállítások                                                         */
/* ------------------------------------------------------------------ */

/** Admin csoport: ennek tagjai mások worklogjait is lekérhetik. */
final String ADMIN_GROUP = "jira-administrators"

/** "Munka felelőse" egyedi mező neve és a második (tesztelő) mező azonosítója. */
final String RESPONSIBLE_FIELD_NAME = "Munka felelőse"
final long SECOND_FIELD_ID = 28014L

/** A dashboard JQL alapja. A currentUser() mindig a hívó felhasználó. */
final String MY_ISSUES_BASE_JQL = '''(("Munka felelőse" = currentUser() AND resolution = "Unresolved" AND status NOT IN ("In Review", "Closed", "Done")) OR (cf[28014] = currentUser() AND status NOT IN ("Sikeres teszt", "Sikertelen teszt", "Done") OR (resolution CHANGED AFTER -7d AND ("Munka felelőse" = currentUser() OR cf[28014] = currentUser()))))'''

final int MY_ISSUES_LIMIT = 300

/* ------------------------------------------------------------------ */
/* Közös segédfüggvények                                               */
/* ------------------------------------------------------------------ */

static String isoNow() {
    return new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX")
}

static Response json(int status, Map<String, Object> body) {
    return Response.status(status)
            .type(MediaType.APPLICATION_JSON + ";charset=UTF-8")
            .entity(JsonOutput.toJson(body))
            .build()
}

static Response error(int status, String stage, String message, Map<String, Object> extra = [:]) {
    Map<String, Object> body = [success: false, httpStatus: status, stage: stage, error: message, generatedAt: isoNow()]
    body.putAll(extra)
    return json(status, body)
}

static Map<String, Object> serverInfo() {
    BuildUtilsInfo build = ComponentAccessor.getComponent(BuildUtilsInfo)
    return [
            baseUrl   : ComponentAccessor.applicationProperties.getString(APKeys.JIRA_BASEURL),
            version   : build?.version,
            zoneId    : ZoneId.systemDefault().id,
            serverTime: isoNow()
    ]
}

static Map<String, Object> userInfo(ApplicationUser user) {
    if (user == null) {
        return null
    }
    return [
            key        : user.key,
            username   : user.username,
            displayName: user.displayName,
            email      : user.emailAddress,
            active     : user.active
    ]
}

static String isoDateTime(Date value) {
    return value == null ? null : value.format("yyyy-MM-dd'T'HH:mm:ssXXX")
}

/** Egyedi mező értékét felhasználó-listává alakítja (single és multi user picker is). */
static List<ApplicationUser> usersOf(Issue issue, CustomField field) {
    if (issue == null || field == null) {
        return []
    }
    def value = issue.getCustomFieldValue(field)
    if (value instanceof ApplicationUser) {
        return [(ApplicationUser) value]
    }
    if (value instanceof Collection) {
        return ((Collection) value).findAll { it instanceof ApplicationUser } as List<ApplicationUser>
    }
    return []
}

static String escapeJqlString(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
}

/* ------------------------------------------------------------------ */
/* 1. portalMe                                                          */
/* ------------------------------------------------------------------ */

portalMe(httpMethod: "GET") { MultivaluedMap queryParams ->
    ApplicationUser currentUser = ComponentAccessor.jiraAuthenticationContext.loggedInUser
    if (!currentUser) {
        return error(401, "authentication", "Jira-hitelesítés szükséges.")
    }
    if (!currentUser.active) {
        return error(403, "authorization", "A Jira-felhasználó inaktív.")
    }
    boolean admin = ComponentAccessor.groupManager.isUserInGroup(currentUser, ADMIN_GROUP)
    return json(200, [
            success    : true,
            endpoint   : "portalMe",
            user       : userInfo(currentUser),
            admin      : admin,
            adminGroup : ADMIN_GROUP,
            server     : serverInfo(),
            generatedAt: isoNow()
    ])
}

/* ------------------------------------------------------------------ */
/* 2. portalMyIssues                                                    */
/* ------------------------------------------------------------------ */

portalMyIssues(httpMethod: "GET") { MultivaluedMap queryParams ->
    long startedAt = System.nanoTime()
    ApplicationUser currentUser = ComponentAccessor.jiraAuthenticationContext.loggedInUser
    if (!currentUser) {
        return error(401, "authentication", "Jira-hitelesítés szükséges.")
    }

    // A belső subtasksOf() lekérdezésben az idézőjeleket escape-elni kell.
    String innerJql = MY_ISSUES_BASE_JQL.replace("\\", "\\\\").replace("\"", "\\\"")
    String jql = "${MY_ISSUES_BASE_JQL} OR issueFunction in subtasksOf(\"${innerJql}\") ORDER BY updated DESC"

    SearchService searchService = ComponentAccessor.getComponent(SearchService)
    def parseResult = searchService.parseQuery(currentUser, jql)
    if (!parseResult.valid) {
        return error(400, "jql-validation", "A Jira nem fogadta el a dashboard JQL-t.",
                [details: parseResult.errors?.toString(), jql: jql])
    }

    def searchResult
    try {
        searchResult = searchService.search(currentUser, parseResult.query, new PagerFilter(MY_ISSUES_LIMIT))
    } catch (Exception exception) {
        return error(500, "issue-search", "Hiba történt a jegyek keresése közben.",
                [errorType: exception.class.name, details: exception.message, jql: jql])
    }

    def customFieldManager = ComponentAccessor.customFieldManager
    CustomField responsibleField = customFieldManager.getCustomFieldObjectsByName(RESPONSIBLE_FIELD_NAME)?.find { true }
    CustomField secondField = customFieldManager.getCustomFieldObject(SECOND_FIELD_ID)

    Date recentLimit = new Date(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)

    List<Map<String, Object>> issues = searchResult.results.collect { Issue issue ->
        List<ApplicationUser> responsible = usersOf(issue, responsibleField)
        List<ApplicationUser> second = usersOf(issue, secondField)

        List<String> reasons = []
        boolean isResponsible = responsible.any { it.key == currentUser.key }
        boolean isSecond = second.any { it.key == currentUser.key }
        if (isResponsible) reasons << "responsible"
        if (isSecond) reasons << "tester"
        if (issue.resolutionDate != null && issue.resolutionDate.after(recentLimit)) reasons << "resolvedRecently"
        if (issue.subTask && !isResponsible && !isSecond) reasons << "subtask"

        def status = issue.status
        [
                key           : issue.key,
                summary       : issue.summary,
                project       : issue.projectObject?.key,
                projectName   : issue.projectObject?.name,
                issueType     : issue.issueType?.name,
                subtask       : issue.subTask,
                parentKey     : issue.parentObject?.key,
                parentSummary : issue.parentObject?.summary,
                status        : status?.name,
                statusCategory: status?.statusCategory?.key,
                priority      : issue.priority?.name,
                resolution    : issue.resolution?.name,
                assignee      : issue.assignee?.displayName,
                responsible   : responsible.collect { it.displayName },
                tester        : second.collect { it.displayName },
                created       : isoDateTime(issue.created),
                updated       : isoDateTime(issue.updated),
                resolved      : isoDateTime(issue.resolutionDate),
                dueDate       : issue.dueDate == null ? null : issue.dueDate.format("yyyy-MM-dd"),
                reasons       : reasons
        ]
    }

    return json(200, [
            success    : true,
            endpoint   : "portalMyIssues",
            user       : userInfo(currentUser),
            server     : serverInfo(),
            fieldNames : [responsible: responsibleField?.name ?: RESPONSIBLE_FIELD_NAME,
                          tester     : secondField?.name ?: "cf[${SECOND_FIELD_ID}]".toString()],
            total      : searchResult.total,
            limit      : MY_ISSUES_LIMIT,
            issues     : issues,
            jql        : jql,
            executionMs: Math.round((System.nanoTime() - startedAt) / 1_000_000.0D),
            generatedAt: isoNow()
    ])
}

/* ------------------------------------------------------------------ */
/* 3. portalWorklogs                                                    */
/* ------------------------------------------------------------------ */

portalWorklogs(httpMethod: "GET") { MultivaluedMap queryParams ->
    long startedAt = System.nanoTime()
    Map<String, Long> timings = [:]
    ApplicationUser currentUser = ComponentAccessor.jiraAuthenticationContext.loggedInUser
    if (!currentUser) {
        return error(401, "authentication", "Jira-hitelesítés szükséges.")
    }

    /* --- Paraméterek --- */
    String userParam = queryParams.getFirst("user")?.toString()?.trim()
    String daysParam = queryParams.getFirst("days")?.toString()?.trim()
    String projectsParam = queryParams.getFirst("projects")?.toString()?.trim()
    String scope = (queryParams.getFirst("scope")?.toString()?.trim()?.toLowerCase()) ?: "authored"

    if (!(scope in ["authored", "assigned"])) {
        return error(400, "parameter-validation", "A 'scope' értéke 'authored' vagy 'assigned' lehet.")
    }

    int days = 14
    if (daysParam) {
        try {
            days = Integer.parseInt(daysParam)
        } catch (NumberFormatException ignored) {
            return error(400, "parameter-validation", "A 'days' értéke csak egész szám lehet.")
        }
    }
    if (days < 1 || days > 366) {
        return error(400, "parameter-validation", "A 'days' értékének 1 és 366 között kell lennie.")
    }

    List<String> projectKeys = (projectsParam ?: "")
            .split(",")
            .collect { it.trim().toUpperCase() }
            .findAll { !it.isEmpty() }
            .unique()
    if (projectKeys.any { !(it ==~ /[A-Z][A-Z0-9_]*/) }) {
        return error(400, "parameter-validation", "Érvénytelen projektkulcs.", [receivedProjects: projectKeys])
    }

    /* --- Cél felhasználó és jogosultság --- */
    ApplicationUser targetUser = currentUser
    if (userParam && !userParam.equalsIgnoreCase(currentUser.username)) {
        boolean admin = ComponentAccessor.groupManager.isUserInGroup(currentUser, ADMIN_GROUP)
        if (!admin) {
            return error(403, "authorization", "Más felhasználó worklogjait csak a(z) ${ADMIN_GROUP} csoport tagjai kérhetik le.".toString())
        }
        targetUser = ComponentAccessor.userManager.getUserByName(userParam)
        if (targetUser == null) {
            return error(404, "user-lookup", "Nincs ilyen Jira-felhasználó: ${userParam}".toString())
        }
    }

    /* --- JQL --- */
    ZoneId zoneId = ZoneId.systemDefault()
    LocalDate today = LocalDate.now(zoneId)
    LocalDate cutoffDate = today.minusDays(days - 1L)
    String escapedUser = escapeJqlString(targetUser.username)
    String projectClause = projectKeys ? "project in (${projectKeys.join(', ')}) AND " : ""

    String jql
    if (scope == "authored") {
        jql = "${projectClause}worklogAuthor = \"${escapedUser}\" AND worklogDate >= \"${cutoffDate}\""
    } else {
        jql = "${projectClause}(\"Munka felelőse\" = \"${escapedUser}\" OR assignee = \"${escapedUser}\")"
    }

    SearchService searchService = ComponentAccessor.getComponent(SearchService)
    long phase = System.nanoTime()
    def parseResult = searchService.parseQuery(currentUser, jql)
    if (!parseResult.valid) {
        return error(400, "jql-validation", "A Jira nem fogadta el a JQL-t.", [details: parseResult.errors?.toString(), jql: jql])
    }

    def searchResult
    try {
        searchResult = searchService.search(currentUser, parseResult.query, PagerFilter.unlimitedFilter)
    } catch (Exception exception) {
        return error(500, "issue-search", "Hiba történt a jegyek keresése közben.",
                [errorType: exception.class.name, details: exception.message, jql: jql])
    }
    timings["issueSearchMs"] = Math.round((System.nanoTime() - phase) / 1_000_000.0D)

    /* --- Worklogok --- */
    phase = System.nanoTime()
    def worklogManager = ComponentAccessor.worklogManager
    List<Map<String, Object>> rows = []
    long scanned = 0L
    try {
        searchResult.results.each { Issue issue ->
            worklogManager.getByIssue(issue).each { worklog ->
                scanned++
                if (worklog.authorKey != targetUser.key) {
                    return
                }
                LocalDate worklogDate = worklog.startDate.toInstant().atZone(zoneId).toLocalDate()
                if (worklogDate.isBefore(cutoffDate) || worklogDate.isAfter(today)) {
                    return
                }
                long seconds = worklog.timeSpent as long
                rows << [
                        date        : worklogDate.toString(),
                        issue       : issue.key,
                        issueSummary: issue.summary,
                        project     : issue.projectObject?.key,
                        seconds     : seconds,
                        comment     : worklog.comment ?: "",
                        worklogId   : worklog.id?.toString(),
                        started     : isoDateTime(worklog.startDate)
                ]
            }
        }
    } catch (Exception exception) {
        return error(500, "worklog-processing", "Hiba történt a worklogok feldolgozása közben.",
                [errorType: exception.class.name, details: exception.message, jql: jql])
    }
    timings["worklogProcessingMs"] = Math.round((System.nanoTime() - phase) / 1_000_000.0D)

    rows.sort { a, b -> (b.date as String) <=> (a.date as String) ?: (a.issue as String) <=> (b.issue as String) }

    return json(200, [
            success            : true,
            endpoint           : "portalWorklogs",
            server             : serverInfo(),
            authenticatedUser  : userInfo(currentUser),
            targetUser         : userInfo(targetUser),
            days               : days,
            startDate          : cutoffDate.toString(),
            endDate            : today.toString(),
            projects           : projectKeys,
            scope              : scope,
            jql                : jql,
            searchedIssueCount : searchResult.results.size(),
            scannedWorklogCount: scanned,
            worklogCount       : rows.size(),
            rows               : rows,
            timings            : timings,
            totalExecutionMs   : Math.round((System.nanoTime() - startedAt) / 1_000_000.0D),
            generatedAt        : isoNow()
    ])
}
