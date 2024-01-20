package command;

import object.GitObject;
import object.Tree;
import picocli.CommandLine;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;

import static util.Util.parseDirToEntries;

@CommandLine.Command(name = "write-tree")
public class WriteTreeCommand implements Callable {
    @Override
    public Object call() {
        List entries = parseDirToEntries(Paths.get("").toAbsolutePath());
        Tree root = new Tree(entries);
        String hash = GitObject.writeToFile(root, Paths.get("").toAbsolutePath());
        System.out.print(hash);

        return null;
    }
}
