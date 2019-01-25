package au.edu.uq.rcc.nimrodg.parsing.visitors;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GlobMatchWalker extends SimpleFileVisitor<Path> {

	private final PathMatcher matcher;
	private final Path root;
	private final List<Path> paths;

	public GlobMatchWalker(PathMatcher matcher, Path root) {
		this.matcher = matcher;
		this.root = root;
		this.paths = new ArrayList<>();
	}

	public List<Path> getPathList() {
		return Collections.unmodifiableList(paths);
	}

	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
		Path rel = root.relativize(file);
		if(matcher.matches(rel)) {
			paths.add(rel);
		}
		return FileVisitResult.CONTINUE;
	}

	@Override
	public FileVisitResult visitFileFailed(Path file, IOException exc) {
		return FileVisitResult.CONTINUE;
	}

}
