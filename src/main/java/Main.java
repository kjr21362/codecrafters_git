import java.io.*;
import java.nio.file.Files;
import java.util.zip.InflaterInputStream;

public class Main {
  public static void main(String[] args) {
    // You can use print statements as follows for debugging, they'll be visible
    // when running tests.
    System.out.println("Logs from your program will appear here!");

    final String command = args[0];

    switch (command) {
      case "init" -> {
        initCommand();
      }
      case "cat-file" -> {
        catFileCommand(args[1], args[2]);
      }
      default -> System.out.println("Unknown command: " + command);
    }
  }

  private static void catFileCommand(String mode, String blob_sha) {
    switch (mode) {
      case "-p" -> {
        String header_sha = blob_sha.substring(0, 2);
        String content_sha = blob_sha.substring(2);

        try(InputStream fileStream = new FileInputStream(".git/objects/" + header_sha + "/" + content_sha);
          InflaterInputStream inflaterInputStream = new InflaterInputStream(fileStream);
          Reader reader = new InputStreamReader(inflaterInputStream);
          BufferedReader bufferedReader = new BufferedReader(reader)) {

          int b;
          while ((b = bufferedReader.read()) != -1) {
            System.out.print((char)b);
          }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
      }
      default -> System.out.println("Not supported: " + mode);
    }
  }

  private static void initCommand() {
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
  }
}
