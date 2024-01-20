package command;

import object.Blob;
import object.GitObject;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "cat-file")
public class CatFileCommand implements Callable {

    @CommandLine.Option(names = "-p")
    private boolean enablePrettyPrint;
    @CommandLine.Parameters
    private String hash;

    @Override
    public Object call() {
        if (enablePrettyPrint) {
            Blob blob = (Blob) GitObject.fromHash(hash);
            System.out.print(blob.getContent());
        }

        return null;
    }
}