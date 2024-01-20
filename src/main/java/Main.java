import command.CatFileCommand;
import command.CloneCommand;
import command.CommitTreeCommand;
import command.HashObjectCommand;
import command.InitCommand;
import command.LsTreeCommand;
import command.WriteTreeCommand;
import picocli.CommandLine;

@CommandLine.Command(name = "git", mixinStandardHelpOptions = true,
subcommands = {
        InitCommand.class, CatFileCommand.class, HashObjectCommand.class, LsTreeCommand.class,
        WriteTreeCommand.class, CommitTreeCommand.class, CloneCommand.class
})
public class Main {

    public static void main(String[] args) {
        new CommandLine(new Main()).execute(args);
    }
}