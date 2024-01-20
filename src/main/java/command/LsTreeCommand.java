package command;

import object.GitObject;
import object.Tree;
import object.TreeEntry;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "ls-tree")
public class LsTreeCommand implements Callable {

    @CommandLine.Option(names = "--name-only")
    private boolean printNameOnly;
    @CommandLine.Parameters
    private String hash;

    @Override
    public Object call() {
        if (printNameOnly) {
            Tree tree = (Tree) GitObject.fromHash(hash);
            for (TreeEntry entry : tree.getEntries()) {
                System.out.println(entry.getPath());
            }
        }

        return null;
    }
}
