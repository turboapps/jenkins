package org.jenkinsci.plugins.spoontrigger.utils;

import org.apache.http.util.TextUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

public final class FileUtils {

    public static void deleteDirectoryTree(Path path) throws IOException {
        Files.walkFileTree(path, new DeleteDirectoryTreeVisitor());
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
