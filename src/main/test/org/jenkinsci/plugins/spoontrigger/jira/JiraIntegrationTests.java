package org.jenkinsci.plugins.spoontrigger.jira;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.jenkinsci.plugins.spoontrigger.TurboTool.*;

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
    public void createIssueTest() throws Exception {
        this.jiraApi.createOrReopenIssue(ProjectName);
    }
}
