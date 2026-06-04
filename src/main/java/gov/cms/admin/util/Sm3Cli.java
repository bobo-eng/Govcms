package gov.cms.admin.util;

import gov.cms.admin.security.GmCryptoService;
import gov.cms.admin.security.BouncyCastleGmCryptoService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

public class Sm3Cli {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java gov.cms.admin.util.Sm3Cli <file>");
            System.exit(1);
        }
        Path path = Path.of(args[0]);
        if (!Files.exists(path)) {
            System.err.println("File not found: " + path);
            System.exit(2);
        }
        GmCryptoService crypto = new BouncyCastleGmCryptoService();
        byte[] data = Files.readAllBytes(path);
        byte[] digest = crypto.sm3Digest(data);
        System.out.println(HexFormat.of().formatHex(digest));
    }
}
