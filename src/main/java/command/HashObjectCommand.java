package command;

import object.Blob;
import object.GitObject;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "hash-object")
public class HashObjectCommand implements Callable {

    @CommandLine.Option(names = "-w")
    private boolean enableWrite;
    @CommandLine.Parameters
    private String file;

    @Override
    public Object call() {
        if (enableWrite) {
            String hash = GitObject.writeToFile(Blob.fromFile(Path.of(file)));
            System.out.print(hash);
        }
        return null;
    }
}
