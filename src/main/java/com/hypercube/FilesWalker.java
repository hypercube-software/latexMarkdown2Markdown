package com.hypercube;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Stack;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * 
 * Files.walk() is mostly unusable. Here is a better version, single threaded and
 * lazy evaluated. In this implementation elements are ordered (files first).
 * 
 * see:
 * https://stackoverflow.com/questions/22867286/files-walk-calculate-total-size/22868706
 * 
 * Note: there is no way to avoid the filter in the constructor if you plan to
 * use lazy evaluation. this is due to the way Stream.iterate() is made.
 * 
 */
public class FilesWalker {
	private static Logger logger = Logger.getLogger(FilesWalker.class.getName());

	private static class HiddenState {

		private Stack<File> stack = new Stack<File>();

		private Predicate<File> filter;

		public HiddenState(Predicate<File> filter) {
			this.filter = filter;
		}

		public boolean hasNext(File current) {

			try {
				if (current.isDirectory()) {
					File[] children = current.listFiles();
					//
					// visit the file first, then the folders
					//
					Arrays.sort(children, (f1, f2) -> {
						Boolean b1 = f1.isDirectory();
						Boolean b2 = f2.isDirectory();
						int cmp = b2.compareTo(b1);
						return cmp == 0 ? f2.getName()
								.compareTo(f1.getName()) : cmp;
					});

					Arrays.stream(children)
							.filter(filter)
							.forEach(f -> stack.push(f));
				}
			} catch (Exception e) {
				logger.log(Level.WARNING, "Unexpected error on folder " + current.getAbsolutePath(), e);
			}

			return !stack.isEmpty();
		}

		public File nextOf(File current) {
			return stack.pop();
		}

	}

	public static Stream<File> walk(Path folder, Predicate<File> filter) {
		// We have an HiddenState for every call, so it is threadsafe.
		HiddenState stat = new HiddenState(filter);
		return Stream.iterate(folder.toFile(), stat::hasNext, stat::nextOf);
	}
}
