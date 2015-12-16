package org.jenkinsci.plugins.spoontrigger.utils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class FileUtils {

    public static void deleteDirectoryTree(Path path) throws IOException {
        Files.walkFileTree(path, new DeleteDirectoryTreeVisitor());
    }

    public static void quietDeleteFileIfExist(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // no-op
        }
    }

    public static void quietDeleteDirectoryTreeIfExists(Path directory) {
        if (directory.toFile().exists()) {
            try {
                FileUtils.deleteDirectoryTree(directory);
            } catch (IOException ex) {
                // no-op
            }
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
