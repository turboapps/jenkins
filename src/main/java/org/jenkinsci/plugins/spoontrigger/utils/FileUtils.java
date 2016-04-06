package org.jenkinsci.plugins.spoontrigger.utils;

import hudson.model.BuildListener;
import org.apache.http.util.TextUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

public final class FileUtils {

    public static void deleteDirectoryTree(Path path) throws IOException {
        Files.walkFileTree(path, new DeleteDirectoryTreeVisitor());
    }

    public static void deleteDirectoryTreeRetryOnFailure(Path directory, BuildListener listener) throws IOException, InterruptedException {
        final int MAX_RETRY = 5;
        final int BASE = 3000;
        final int BACK_OFF = 5000;

        int attempt = 0;
        while (true) {
            try {
                Files.walkFileTree(directory, new DeleteDirectoryTreeVisitor());
                break;
            } catch (FileSystemException ex) {
                String processedFile = ex.getFile();
                if (!Files.exists(Paths.get(processedFile))) {
                    throw ex;
                }

                if (attempt < MAX_RETRY) {
                    final long delayMillis = BASE + attempt * BACK_OFF;

                    String logMsg = String.format("Failed to delete %s. Next retry in %d seconds.", processedFile, delayMillis / 1000);
                    listener.getLogger().println(logMsg);

                    Thread.sleep(delayMillis);
                } else {
                    String error = String.format("Failed to delete %s file in %d attempts.", processedFile, MAX_RETRY);
                    throw new IOException(error, ex);
                }
            }

            ++attempt;
        }
    }

    public static void quietDeleteChildren(Path path) throws IOException {
        File[] children = path.toFile().listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                quietDeleteDirectoryTree(child.toPath());
            } else {
                quietDeleteFile(child.toPath());
            }
        }
    }

    public static String getExtension(Path file) {
        final String extension = com.google.common.io.Files.getFileExtension(file.toString());
        if (TextUtils.isEmpty(extension)) {
            return "";
        }
        return extension.toLowerCase(Locale.ROOT);
    }

    public static void quietDeleteFileIfExist(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // no-op
        }
    }

    public static void quietDeleteFile(Path file) {
        try {
            Files.delete(file);
        } catch (IOException e) {
            // no-op
        }
    }

    public static void quietDeleteDirectoryTree(Path directory) {
        try {
            FileUtils.deleteDirectoryTree(directory);
        } catch (IOException ex) {
            // no-op
        }
    }

    public static void quietDeleteDirectoryTreeIfExists(Path directory) {
        if (directory.toFile().exists()) {
            quietDeleteDirectoryTree(directory);
        }
    }

    private static class DeleteDirectoryTreeVisitor extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }
}
