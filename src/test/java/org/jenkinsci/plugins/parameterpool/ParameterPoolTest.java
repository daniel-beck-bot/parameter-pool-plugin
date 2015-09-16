package org.jenkinsci.plugins.parameterpool;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.JobProperty;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.tasks.Shell;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests using a test jenkins instance
 */
public class ParameterPoolTest {
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void canSelectPoolValue() throws IOException, ExecutionException, InterruptedException {
        FreeStyleProject project = createProject(0, false);
        FreeStyleBuild completedBuild = project.scheduleBuild2(0).get();

        String logText = FileUtils.readFileToString(completedBuild.getLogFile());
        assertThatVmIsInText(completedBuild.getNumber(), 1, logText);
    }

    @Test
    public void selectFirstNonRunningPoolValue() throws Exception {
        FreeStyleProject project = createProject(5, false);
        project.scheduleBuild2(0).waitForStart();

        FreeStyleBuild completedBuild = project.scheduleBuild2(0).get();

        String logText = FileUtils.readFileToString(completedBuild.getLogFile());
        assertThatVmIsInText(completedBuild.getNumber(), 2, logText);
    }

    @Test
    public void skipPoolValueFromFailedBuild() throws Exception {
        FreeStyleProject project = createProject(0, false);
        project.getBuildersList().add(new Shell("exit 1"));
        project.scheduleBuild2(0).waitForStart().run();

        FreeStyleBuild completedBuild = project.scheduleBuild2(0).get();

        String logText = FileUtils.readFileToString(completedBuild.getLogFile());
        assertThatVmIsInText(completedBuild.getNumber(), 2, logText);
    }

    @Test
    public void pickFirstNonRunningValueIfAllValuesHaveFailed() throws Exception {
        FreeStyleProject project = createProject(0, false);
        project.getBuildersList().add(new Shell("exit 1"));
        project.scheduleBuild2(0).waitForStart().run();
        project.scheduleBuild2(0).waitForStart().run();
        project.scheduleBuild2(0).waitForStart().run();

        FreeStyleBuild completedBuild = project.scheduleBuild2(0).get();


        String logText = FileUtils.readFileToString(completedBuild.getLogFile());
        assertThatVmIsInText(completedBuild.getNumber(), 1, logText);
    }

    @Test
    public void pickNextValueBetweenTwoProjects() throws Exception {
        FreeStyleProject project = createProject("first project", "first project,second project", 0, false);
        FreeStyleProject anotherProject = createProject("second project", "first project,second project", 0, false);

        project.scheduleBuild2(0).waitForStart().run();
        anotherProject.scheduleBuild2(0).waitForStart().run();

        FreeStyleBuild completedBuild = project.scheduleBuild2(0).get();

        String logText = FileUtils.readFileToString(completedBuild.getLogFile());
        assertThatVmIsInText(completedBuild.getNumber(), 3, logText);
    }

    @Test
    public void pickNextValueBetweenTwoProjectsWithSecondProjectHavingRegularParameter() throws Exception {
        FreeStyleProject project = createProject("first project", "first project,second project", 0, false);
        FreeStyleProject anotherProject = createProjectWithRegularParameter("second project", 0);

        project.scheduleBuild2(0).waitForStart().run();
        anotherProject.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("testValue", "vm2")));
        anotherProject.scheduleBuild2(0).waitForStart().run();

        FreeStyleBuild completedBuild = project.scheduleBuild2(0).get();

        String logText = FileUtils.readFileToString(completedBuild.getLogFile());
        assertThatVmIsInText(completedBuild.getNumber(), 3, logText);
    }

    @Test
    public void pickFirstErrorBuildIfPreferringErrors() throws Exception {
        FreeStyleProject project = createProject(0, true);

        Shell exitShell = new Shell("exit 1");
        project.getBuildersList().add(exitShell);
        project.scheduleBuild2(0).waitForStart().run();
        project.scheduleBuild2(0).waitForStart().run();

        project.getBuildersList().remove(exitShell);

        FreeStyleBuild successfulBuildAfterFailures = project.scheduleBuild2(0).get();
        String logText = FileUtils.readFileToString(successfulBuildAfterFailures.getLogFile());
        assertEquals(logText, Result.SUCCESS, successfulBuildAfterFailures.getResult());

        FreeStyleBuild completedBuild = project.scheduleBuild2(0).get();
        logText = FileUtils.readFileToString(completedBuild.getLogFile());
        assertThatVmIsInText(completedBuild.getNumber(), 2, logText);
    }

    private void assertThatVmIsInText(int buildNumber, int vmNumber, String logText) {
        assertTrue("Expected vm" + vmNumber + " in text " + buildNumber + " " + logText,
                logText.contains("Vm vm" + vmNumber + " used for testing"));
    }


    private FreeStyleProject createProject(int sleepDuration, boolean preferError) throws IOException {
        return createProject("test project", "test project", sleepDuration, preferError);
    }

    private FreeStyleProject createProject(String name, String projectNamesForBuilder, int sleepDuration, boolean preferError) throws IOException {
        FreeStyleProject project = jenkins.createFreeStyleProject(name);
        project.setConcurrentBuild(true);
        project.getBuildersList().add(new ParameterPoolBuilder(projectNamesForBuilder, "testValue", "vm[1..3]", preferError));
        project.getBuildersList().add(new Shell("sleep " + sleepDuration + ";\necho Vm ${testValue} used for testing"));
        return project;
    }

    private FreeStyleProject createProjectWithRegularParameter(String name, int sleepDuration) throws IOException {
        FreeStyleProject project = jenkins.createFreeStyleProject(name);
        project.setConcurrentBuild(true);
        project.getBuildersList().add(new Shell("sleep " + sleepDuration + ";\necho Vm ${testValue} used for testing"));
        return project;
    }

}
