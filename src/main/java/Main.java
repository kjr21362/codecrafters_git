import com.google.common.primitives.Bytes;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.InflaterInputStream;

public class Main {
  public static void main(String[] args) {

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
          InflaterInputStream inflaterInputStream = new InflaterInputStream(fileStream)) {

          byte[] data = inflaterInputStream.readAllBytes();
          int first_delimeter_idx = Bytes.indexOf(data, (byte)' ');
          int second_delimeter_idx = Bytes.indexOf(data, (byte) 0x00);
          String type = new String(data, 0, first_delimeter_idx);
          int length = Integer.valueOf(new String(data, first_delimeter_idx + 1, second_delimeter_idx - first_delimeter_idx - 1));
          String content = new String(data, second_delimeter_idx + 1, length);

          switch (type) {
            case "blob" -> {
              System.out.print(content);
            }
            default -> System.out.println("Not supported: " + type);
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
