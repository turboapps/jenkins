package org.jenkinsci.plugins.spoontrigger.scheduledtasks;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.io.BaseEncoding;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScheduledTasksApi {
    private static final String SCHEDULED_TASKS_RUN_RESOURCE_ID = "run-task.ps1";
    private static final Pattern QUOTES_PATTERN = Pattern.compile("^\"(?<value>.*)\"$");

    private final Charset charset;
    private final EnvVars env;
    private final FilePath pwd;
    private final Launcher launcher;
    private final TaskListener listener;
    private final boolean quiet;

    public ScheduledTasksApi(EnvVars env, FilePath pwd, Charset charset, Launcher launcher, TaskListener listener, boolean quiet) {
        this.env = env;
        this.pwd = pwd;
        this.charset = charset;
        this.launcher = launcher;
        this.listener = listener;
        this.quiet = quiet;
    }

    public void create(String taskName, String command) throws IOException, InterruptedException {
        ArgumentListBuilder args = getCreateCommand(taskName, command);
        executeCommandAssertExitCode(args, new NullOutputStream());
    }

    /**
     * Executes PowerShellCommand command using a scheduled task. If a scheduled task with the specified name already exists it will be deleted.
     */
    public void run(String taskName, String command, OutputStreamCollector outputStreamCollector) throws IOException, InterruptedException {
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

            ArgumentListBuilder runCommand = getRunCommand(launchScriptPath, taskName, command);
            outputStreamCollector.bind(listener.getLogger(), this.charset);
            executeCommandAssertExitCode(runCommand, outputStreamCollector);
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
        Map<String, String> taskInfo = getTask(taskName);
        return Optional.fromNullable(taskInfo.get("State"));
    }

    private Map<String, String> getTask(String taskName) throws IOException, InterruptedException {
        OutputStreamCollector outputStream = new OutputStreamCollector(new PrintStream(new NullOutputStream()), charset);
        try {
            ArgumentListBuilder command = getTaskCommand(taskName);

            int exitCode = executeCommand(command, outputStream);
            if (exitCode == 0) {
                ArrayList<String> headers = null;
                for (String line : outputStream.getLines()) {
                    if (line.startsWith("#")) {
                        continue;
                    }

                    if (headers == null) {
                        headers = extractValues(line);
                        continue;
                    }

                    ArrayList<String> values = extractValues(line);
                    Map<String, String> bag = new HashMap<String, String>();
                    int maxPos = Math.min(values.size(), headers.size());
                    for (int pos = 0; pos < maxPos; ++pos) {
                        String property = headers.get(pos);
                        String value = values.get(pos);
                        bag.put(property, value);
                    }
                    return bag;
                }
            }
            return Collections.emptyMap();
        } finally {
            final boolean swallowException = true;
            Closeables.close(outputStream, swallowException);
        }
    }

    private ArrayList<String> extractValues(String line) {
        String[] rawValues = line.split(",");
        ArrayList<String> values = new ArrayList<String>(rawValues.length);
        for (String rawValue : rawValues) {
            Matcher match = QUOTES_PATTERN.matcher(rawValue);
            if (match.matches()) {
                values.add(match.group("value"));
            } else {
                values.add(rawValue);
            }
        }
        return values;
    }

    private void executeCommandAssertExitCode(ArgumentListBuilder argumentList, OutputStream out) throws IOException, InterruptedException {
        int errorCode = executeCommand(argumentList, out);
        if (errorCode != 0) {
            String errMsg = String.format("Process returned error code %d", errorCode);
            throw new IllegalStateException(errMsg);
        }
    }

    private int executeCommand(ArgumentListBuilder argumentList, OutputStream out) throws IOException, InterruptedException {
        OutputStreamCollector outputCollector = new OutputStreamCollector(new PrintStream(out), charset);
        return this.launcher.launch().pwd(this.pwd).envs(this.env).cmds(argumentList).stdout(outputCollector).quiet(quiet).join();
    }

    private ArgumentListBuilder getRunCommand(Path launchScriptPath, String taskName, String command) {
        return new ArgumentListBuilder("powershell.exe")
                .add("-WindowStyle")
                .add("Hidden")
                .add("-File")
                .addQuoted(launchScriptPath.toAbsolutePath().toString())
                .add("-TaskName")
                .add(taskName)
                .add("-Command")
                .add(command)
                .add("-WorkingDir")
                .add(pwd.getRemote());
    }

    private ArgumentListBuilder getRunCommand(String taskName) {
        String command = String.format("Start-ScheduledTask -TaskName \"%s\"", taskName);
        return getPowerShellCommand(command);
    }

    private ArgumentListBuilder getDeleteCommand(String taskName) {
        String command = String.format("Unregister-ScheduledTask -TaskName \"%s\" -Confirm:$False", taskName);
        return getPowerShellCommand(command);
    }

    private ArgumentListBuilder getTaskCommand(String taskName) {
        String command = String.format("Get-ScheduledTask | Where-Object {$_.TaskName -like \"%s\"} | ConvertTo-CSV", taskName);
        return getPowerShellCommand(command);
    }

    private ArgumentListBuilder getCreateCommand(String taskName, String command) {
        String commandToUse = String.format(
                "Register-ScheduledTask -Action (New-ScheduledTaskAction -Execute powershell.exe -Argument \"-WindowStyle Hidden -EncodedCommand %s\") -TaskName \"%s\"",
                encodeBase64(command),
                taskName);
        return getPowerShellCommand(commandToUse);
    }

    private ArgumentListBuilder getPowerShellCommand(String command) {
        return new ArgumentListBuilder("powershell.exe")
                .add("-encodedCommand")
                .add(encodeBase64(command));
    }

    private String encodeBase64(String value) {
        return BaseEncoding.base64().encode(value.getBytes(Charsets.UTF_16LE));
    }
}
