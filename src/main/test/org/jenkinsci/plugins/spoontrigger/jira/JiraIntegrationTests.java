package org.jenkinsci.plugins.spoontrigger.jira;

import com.google.common.base.Optional;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import static org.jenkinsci.plugins.spoontrigger.TurboTool.BugTrackerSettings;

public class JiraIntegrationTests {

    private static final String ProjectName = "apache";

    private final BugTrackerSettings bugTrackerSettings;
    private JiraApi jiraApi;

    public JiraIntegrationTests() throws MalformedURLException {
        this.bugTrackerSettings = new BugTrackerSettings(
                new URL("https://team.spoon.net/bugs"),
                "CI",
                "Jira Integration Test",
                "Reopen",
                "JenkinsBuildFailure",
                "Task",
                null);
    }

    @Before
    public void setUp() throws Exception {
        this.jiraApi = new JiraApi(this.bugTrackerSettings);
    }

    @After
    public void tearDown() throws Exception {
        this.jiraApi.close();
    }

    @Test
    @Ignore
    public void createIssueTest() throws Exception {
        this.jiraApi.createOrReopenIssue(ProjectName);
    }

    @Test
    public void searchIssueTest() throws Exception {
        Optional<String> issueKey = this.jiraApi.getIssueKey(Pattern.compile("Eclipse"));

        Assert.assertTrue(issueKey.isPresent());
    }
}
