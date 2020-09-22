package com.hypercube;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

public class CommandLine {
	@Parameter(names = {
			"-background" }, description = "Force the background color in hexa #rrggbb instead of a transparent background", required = false)
	private String background;

	@Parameter(names = { "-dir" }, description = "directory where *.tex.md files are", required = true)
	private String baseDir;
	
	@Parameter(names = { "-toc" }, description = "generate a table of content and add numbers to chapters", required = false)
	private boolean generateToc;

	public boolean isGenerateToc() {
		return generateToc;
	}

	public String getBackground() {
		return background;
	}

	public String getBaseDir() {
		return baseDir;
	}

	boolean parse(String[] args) {
		JCommander parser = new JCommander(this);
		try {
			// parse the arguments.
			parser.parse(args);

		} catch (Exception e) {
			System.err.println(e.getMessage());
			parser.usage();
			return false;
		}
		return true;
	}
}
