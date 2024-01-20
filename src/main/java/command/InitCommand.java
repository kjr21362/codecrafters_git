package command;

import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "init")
public class InitCommand implements Callable<Void> {
    @Override
    public Void call() {
        final File root = new File(".git");
        new File(root, "objects").mkdirs();
        new File(root, "refs").mkdirs();
        final File head = new File(root, "HEAD");

        try {
            head.createNewFile();
            Files.write(head.toPath(), "ref: refs/heads/master\n".getBytes());
            System.out.println("Initialized git directory");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
