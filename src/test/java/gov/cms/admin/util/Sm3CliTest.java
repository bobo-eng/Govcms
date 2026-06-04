package gov.cms.admin.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class Sm3CliTest {
    @Test
    @DisplayName("Sm3Cli produces 64-char hex digest for file")
    void sm3cli_file_producesHex() throws Exception {
        Path temp = Files.createTempFile("test", ".txt");
        Files.writeString(temp, "hello");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(out));
        Sm3Cli.main(new String[]{temp.toString()});
        String result = out.toString().trim();
        assertThat(result).hasSize(64);
        assertThat(result).matches("[0-9a-f]+");
        Files.deleteIfExists(temp);
    }
}
