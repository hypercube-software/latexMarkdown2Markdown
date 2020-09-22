package com.hypercube;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

public class LatexMarkdown2Markdown {

	//
	// Compute the chapter numbering
	//
	private class ChapterCounter {
		private List<Integer> counters = new ArrayList<>();
		private int previousSize = 0;

		//
		// header can be #, ##, ###, or ####
		// if suddently we jump from # to ### this will raise an exception
		//
		public void onNewMarkdownChapter(String header, String chapter) {
			int depth = header.length();
			int index = depth - 1; // from 0 to n-1
			previousSize = counters.size();
			if (counters.size() == index) {
				counters.add(1);
			} else if (counters.size() >= depth) {
				while (counters.size() != depth) {
					counters.remove(counters.size() - 1);
				}
				counters.set(index, counters.get(index) + 1);
			} else {
				throw new RuntimeException("Chapter depth is wrong for: \"" + chapter + "\"");
			}
		}

		// return a string in the form "1", "1.2" or "1.2.4"
		public String getCurrentState() {
			StringBuffer result = new StringBuffer();
			for (int i = 0; i < counters.size(); i++) {
				if (i > 0)
					result.append(".");

				result.append(counters.get(i));
			}
			return result.toString();
		}

		public int getPreviousDepth() {
			return previousSize;
		}

		public int getCurrentDepth() {
			return counters.size();
		}
	}

	//
	// The result of the SVG generator.
	// We don't really use it for now.
	//
	@SuppressWarnings("unused")
	private class SVGFormula {
		private int width;
		private int height;
		private String latexCode;
		private File file;
		
		public SVGFormula(int width, int height,String latexCode, File file) {
			super();
			this.width = width;
			this.height = height;
			this.latexCode = latexCode;
			this.file = file;
		}

		public int getWidth() {
			return width;
		}

		public int getHeight() {
			return height;
		}

		public String getLatexCode() {
			return latexCode;
		}

		public File getFile() {
			return file;
		}
	}

	private Logger logger = Logger.getLogger(LatexMarkdown2Markdown.class.getName());

	private int svgIdCounter = 1;

	private GeneratorConfig config;

	public LatexMarkdown2Markdown(GeneratorConfig config) {
		super();
		this.config = config;
	}

	public static void main(String[] args) {
		initLogs();
		CommandLine cm = new CommandLine();
		if (cm.parse(args)) {
			LatexMarkdown2Markdown lm2m = new LatexMarkdown2Markdown(
					new GeneratorConfig(cm.getBackground(), cm.isGenerateToc(), 0));
			lm2m.start(Path.of(cm.getBaseDir()));
		}
	}

	private Path generateNextSvgFile() {
		return Path.of(String.format("formula-%d.svg", svgIdCounter++));
	}

	private void start(Path startPath) {
		FilesWalker.walk(startPath, f -> !Set.of("node_modules", ".git")
				.contains(f.getName()))
				.filter(f -> f.getName()
						.endsWith(".tex.md"))
				.map(File::toPath)
				.forEach(this::parseTexMarkdow);
	}

	private void parseTexMarkdow(Path srcPath) {
		try {
			Path destPath = buildDestPath(srcPath);

			logger.info("Generate " + destPath.toString());

			String content = Files.readString(srcPath, Charset.forName("UTF-8"));

			content = translateLatexSections(srcPath, content);

			if (config.isGenerateToc()) {
				content = translateChapters(content);
			}
			Files.write(destPath, content.getBytes(Charset.forName("UTF-8")));

		} catch (IOException e) {
			logger.log(Level.SEVERE, "Unexpected error", e);
		}
	}

	//
	// This cleanup matches what Github do
	//
	private String chapterAnchor(String chapter) {
		return chapter.toLowerCase()
				.replaceAll("[^a-zA-Z0-9- ]", "")
				.replaceAll(" ", "-")
				.replace("---", "--")
				.replaceAll("-+$", "");
	}

	private String translateChapters(String content) {
		StringBuffer translated = new StringBuffer();
		Pattern chapterSection = Pattern.compile("^(#+)\\s+(.+)$", Pattern.MULTILINE);
		Matcher m = chapterSection.matcher(content);
		int pos = 0;
		ChapterCounter chapterCounter = new ChapterCounter();
		List<String> chapters = new ArrayList<>();
		String firstChapter = "";
		while (m.find(pos)) {
			int start = m.start();
			int end = m.end();

			String beforeSection = content.substring(pos, start);
			translated.append(beforeSection);

			String header = m.group(1);
			String chapter = m.group(2);
			chapterCounter.onNewMarkdownChapter(header, chapter);

			if ((chapterCounter.getPreviousDepth() > 2 && chapterCounter.getCurrentDepth() == 2)
					|| (chapterCounter.getPreviousDepth() > 1 && chapterCounter.getCurrentDepth() == 1)) {
				chapters.add("");
			}
			if (firstChapter.length() == 0) {
				firstChapter = chapter;
			} else {
				translated.append(header + " " + chapterCounter.getCurrentState() + " " + chapter);
				chapters.add(chapterCounter.getCurrentState() + " " + chapter);
			}
			pos = end;
		}
		String afterSection = content.substring(pos);
		translated.append(afterSection);

		logger.info(firstChapter);

		StringBuffer toc = new StringBuffer();
		toc.append("# " + firstChapter + "\n");
		toc.append("**Table of content**")
				.append("\n")
				.append("\n");

		chapters.forEach(ch -> {
			logger.info(ch);
			if (ch.equals("")) {
				toc.append("  \n"); // force newline
				toc.append("  \n"); // force newline
			} else {
				toc.append("[" + ch + "]");
				toc.append("(#" + chapterAnchor(ch) + ")");
				toc.append("  \n"); // force newline
			}
		});

		return toc.toString() + "\n\n" + translated.toString();
	}

	private String translateLatexSections(Path srcPath, String content) {
		StringBuffer translated = new StringBuffer();
		// LaTex sections are separated by $$
		// The regexp is a little bit complex because we must accept a single $ inside the section
		Pattern latexSection = Pattern.compile("\\$\\$(?<latex>([^$]|(\\$[^$]))*)\\$\\$");
		Matcher m = latexSection.matcher(content);
		int pos = 0;
		while (m.find(pos)) {
			int start = m.start();
			int end = m.end();

			String beforeSection = content.substring(pos, start);
			translated.append(beforeSection);

			String latexCode = m.group("latex");
			Path svgFile = generateNextSvgFile();

			File destSvgFile = srcPath.getParent()
					.resolve("assets")
					.resolve(svgFile)
					.toFile();
			logger.info("Generate " + destSvgFile.toString());
			latexToSVG(latexCode, destSvgFile).ifPresent(f -> {
				translated.append("<img src=\"assets/" + svgFile.toString() + "\"");
				translated.append(" align=\"top\""); // make symbols vaguely ok when they are in the middle of the text
				translated.append("/>");
			});

			pos = end;
		}
		String afterSection = content.substring(pos);
		translated.append(afterSection);
		return translated.toString();
	}

	private Path buildDestPath(Path srcPath) {
		String filename = srcPath.getFileName()
				.toString();
		filename = filename.replace(".tex.", ".");
		Path destPath = srcPath.getParent()
				.resolve(filename);
		return destPath;
	}

	private Optional<SVGFormula> latexToSVG(String latexCode, File destFile) {
		try {
			TeXFormula formula = new TeXFormula(latexCode);
			TeXIcon icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 20);
			icon.setInsets(new Insets(config.getMargin(), config.getMargin(), config.getMargin(), config.getMargin()));
			DOMImplementation domImpl = SVGDOMImplementation.getDOMImplementation();
			Document document = domImpl.createDocument(SVGDOMImplementation.SVG_NAMESPACE_URI, "svg", null);
			SVGGeneratorContext ctx = SVGGeneratorContext.createDefault(document);

			SVGGraphics2D g2 = new SVGGraphics2D(ctx, true);
			g2.setSVGCanvasSize(new Dimension(icon.getIconWidth(), icon.getIconHeight()));

			if (config.getHexBackgroundColor() != null) {
				g2.setColor(Color.decode(config.getHexBackgroundColor()));
				g2.fillRect(0, 0, icon.getIconWidth(), icon.getIconHeight());
			}
			icon.paintIcon(null, g2, 0, 0);
			destFile.getParentFile()
					.mkdirs();
			try (FileOutputStream svgs = new FileOutputStream(destFile)) {
				Writer out = new OutputStreamWriter(svgs, "UTF-8");
				g2.stream(out, false);
				svgs.flush();
			}
			return Optional.of(new SVGFormula(icon.getIconWidth(), icon.getIconHeight(),latexCode,destFile));
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Unexpected error", e);
		}
		return Optional.empty();
	}

	public static void initLogs() {
		try {
			// Load a properties file from class path java.util.logging.config.file
			final LogManager logManager = LogManager.getLogManager();
			URL configURL = LatexMarkdown2Markdown.class.getResource("/logging.properties");
			if (configURL != null) {
				try (InputStream is = configURL.openStream()) {
					logManager.readConfiguration(is);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
