package au.edu.uq.rcc.nimrodg.shell;

import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.session.Session;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

public class FileSystemFactoryWrapper implements FileSystemFactory {
    private final FileSystem fs;

    public FileSystemFactoryWrapper(FileSystem fs) {
        this.fs = fs;
    }

    @Override
    public FileSystem createFileSystem(Session session) {
        return new FileSystem() {
            @Override
            public FileSystemProvider provider() {
                return fs.provider();
            }

            @Override
            public void close() {

            }

            @Override
            public boolean isOpen() {
                return fs.isOpen();
            }

            @Override
            public boolean isReadOnly() {
                return fs.isReadOnly();
            }

            @Override
            public String getSeparator() {
                return fs.getSeparator();
            }

            @Override
            public Iterable<Path> getRootDirectories() {
                return fs.getRootDirectories();
            }

            @Override
            public Iterable<FileStore> getFileStores() {
                return fs.getFileStores();
            }

            @Override
            public Set<String> supportedFileAttributeViews() {
                return fs.supportedFileAttributeViews();
            }

            @Override
            public Path getPath(String arg0, String... arg1) {
                return fs.getPath(arg0, arg1);
            }

            @Override
            public PathMatcher getPathMatcher(String arg0) {
                return fs.getPathMatcher(arg0);
            }

            @Override
            public UserPrincipalLookupService getUserPrincipalLookupService() {
                return fs.getUserPrincipalLookupService();
            }

            @Override
            public WatchService newWatchService() throws IOException {
                return fs.newWatchService();
            }
        };
    }
}
