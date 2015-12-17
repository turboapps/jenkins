package org.jenkinsci.plugins.spoontrigger.scheduledtasks;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.io.Closeables;
import com.google.common.io.Resources;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import org.apache.commons.io.output.NullOutputStream;
import org.jenkinsci.plugins.spoontrigger.commands.OutputStreamCollector;
import org.jenkinsci.plugins.spoontrigger.utils.FileUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.regex.Pattern;

public class ScheduledTasksApi {
    private static final String SCHEDULED_TASKS_RUN_RESOURCE_ID = "run-task.ps1";
    private static final String SCHEDULED_TASKS_TOOL = "schtasks";

    private static final Pattern TASK_STATUS_PATTERN = Pattern.compile(".*\"(?<status>.*?)\"$", Pattern.CASE_INSENSITIVE);

    private final Charset charset;
    private final EnvVars env;
    private final FilePath pwd;
    private final Launcher launcher;
    private final TaskListener listener;

    public ScheduledTasksApi(EnvVars env, FilePath pwd, Charset charset, Launcher launcher, TaskListener listener) {
        this.env = env;
        this.pwd = pwd;
        this.charset = charset;
        this.launcher = launcher;
        this.listener = listener;
    }

    public void create(String taskName, String command) throws IOException, InterruptedException {
        ArgumentListBuilder args = getCreateCommand(taskName, command);
        executeCommandAssertExitCode(args, new NullOutputStream());
    }

    public void run(String taskName, String program, String arguments) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("scheduledtask-");
        try {
            Path launchScriptPath = Paths.get(tempDir.toString(), SCHEDULED_TASKS_RUN_RESOURCE_ID);
            URL resourceId = Resources.getResource(getClass(), SCHEDULED_TASKS_RUN_RESOURCE_ID);
            FileOutputStream outputStream = new FileOutputStream(launchScriptPath.toFile());
            try {
                Resources.copy(resourceId, outputStream);
            } finally {
                final boolean swallowIoException = true;
                Closeables.close(outputStream, swallowIoException);
            }

            ArgumentListBuilder runCommand = getRunCommand(launchScriptPath, taskName, program, arguments);
            executeCommandAssertExitCode(runCommand, listener.getLogger());
        } finally {
            FileUtils.deleteDirectoryTree(tempDir);
        }
    }

    public void run(String taskName) throws IOException, InterruptedException {
        ArgumentListBuilder runCommand = getRunCommand(taskName);
        executeCommandAssertExitCode(runCommand, new NullOutputStream());
    }

    public boolean isRunning(String taskName) throws IOException, InterruptedException {
        Optional<String> status = getStatus(taskName);
        return status.isPresent() && "Running".equals(status.get());
    }

    public boolean isDefined(String taskName) throws IOException, InterruptedException {
        return getStatus(taskName).isPresent();
    }

    public void delete(String taskName) throws IOException, InterruptedException {
        ArgumentListBuilder command = getDeleteCommand(taskName);
        executeCommandAssertExitCode(command, new NullOutputStream());
    }

    public Optional<String> getStatus(String taskName) throws IOException, InterruptedException {
        OutputStreamCollector outputStream = new OutputStreamCollector(new PrintStream(new NullOutputStream()), charset);
        try {
            ArgumentListBuilder command = getStatusCommand(taskName);

            int exitCode = executeCommand(command, outputStream);
            if (exitCode != 0) {
                return Optional.absent();
            }

            Collection<String> statuses = outputStream.findAll(TASK_STATUS_PATTERN);
            if (statuses.isEmpty()) {
                return Optional.absent();
            }
            return Optional.of(Iterables.getLast(statuses));
        } finally {
            final boolean swallowException = true;
            Closeables.close(outputStream, swallowException);
        }
    }

    private void executeCommandAssertExitCode(ArgumentListBuilder argumentList, OutputStream out) throws IOException, InterruptedException {
        int errorCode = executeCommand(argumentList, out);
        if (errorCode != 0) {
            String errMsg = String.format("Process returned error code %d", errorCode);
            throw new IllegalStateException(errMsg);
        }
    }

    private int executeCommand(ArgumentListBuilder argumentList, OutputStream out) throws IOException, InterruptedException {
        return this.launcher.launch().pwd(this.pwd).envs(this.env).cmds(argumentList).stdout(out).join();
    }

    private ArgumentListBuilder getRunCommand(Path launchScriptPath, String taskName, String program, String arguments) {
        return new ArgumentListBuilder("powershell")
                .add("-File")
                .addQuoted(launchScriptPath.toAbsolutePath().toString())
                .add("-TaskName")
                .add(taskName)
                .add("-Path")
                .addQuoted(program)
                .add("-Arguments")
                .add(arguments)
                .add("-WorkingDir")
                .add(pwd.getRemote());
    }

    private ArgumentListBuilder getRunCommand(String taskName) {
        return new ArgumentListBuilder(SCHEDULED_TASKS_TOOL)
                .add("/run")
                .add("/tn").addQuoted(taskName);
    }

    private ArgumentListBuilder getCreateCommand(String taskName, String command) {
        return new ArgumentListBuilder(SCHEDULED_TASKS_TOOL)
                .add("/create")
                .add("/tn").addQuoted(taskName)
                .add("/tr").add(command)
                .add("/sc").add("ONCE")
                .add("/sd").add("01/01/1910")
                .add("/st").add("00:00");
    }

    private ArgumentListBuilder getDeleteCommand(String taskName) {
        return new ArgumentListBuilder(SCHEDULED_TASKS_TOOL)
                .add("/delete")
                .add("/tn").addQuoted(taskName)
                .add("/f");
    }

    private ArgumentListBuilder getStatusCommand(String taskName) {
        return new ArgumentListBuilder(SCHEDULED_TASKS_TOOL)
                .add("/query")
                .add("/tn").addQuoted(taskName)
                .add("/fo").add("csv")
                .add("/nh");
    }
}
