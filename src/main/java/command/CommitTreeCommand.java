package command;

import object.AuthorSignature;
import object.Commit;
import object.GitObject;
import picocli.CommandLine;

import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "commit-tree")
public class CommitTreeCommand implements Callable {

    @CommandLine.Option(names = "-p")
    private String parent_commit_hash;
    @CommandLine.Option(names = "-m")
    private String message;
    @CommandLine.Parameters
    private String tree_hash;

    @Override
    public Object call() {
        AuthorSignature author = new AuthorSignature("test author", "testauthor@test.com", ZonedDateTime.now());
        Commit commit = new Commit(author, author, tree_hash, parent_commit_hash, message);

        String hash = GitObject.writeToFile(commit, Path.of("").toAbsolutePath());
        System.out.print(hash);

        return null;
    }
}
