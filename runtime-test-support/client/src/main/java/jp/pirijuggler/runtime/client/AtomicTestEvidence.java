package jp.pirijuggler.runtime.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Test-only consistent JSON snapshots; no production gameplay changes. */
final class AtomicTestEvidence {
    private AtomicTestEvidence(){}
    static void write(Path dest,String data) {
        try {
            Files.createDirectories(dest.toAbsolutePath().getParent());
            Path tmp=dest.resolveSibling(dest.getFileName()+".partial");
            Files.writeString(tmp,data);
            try {
                Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch(AtomicMoveNotSupportedException unsupported) {
                Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING);
            }
        } catch(IOException e) {
            throw new UncheckedIOException("Failed to write test evidence "+dest,e);
        }
    }
}
