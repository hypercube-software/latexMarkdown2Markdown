package com.hypercube;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.batik.dom.svg.SVGDOMImplementation;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.scilab.forge.jlatexmath.TeXConstants;
import org.scilab.forge.jlatexmath.TeXFormula;
import org.scilab.forge.jlatexmath.TeXIcon;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

import com.hypercube.GeneratorConfig.TocType;
import com.hypercube.translate.ChapterTranslator;

public class LatexMarkdown2Markdown {
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

		public SVGFormula(int width, int height, String latexCode, File file) {
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
			TocType tocType = TocType.NO_TOC;
			if (cm.isGenerateTabulatedToc())
				tocType = TocType.TAB_TOC;
			else if (cm.isGenerateToc())
				tocType = TocType.TOC;

			LatexMarkdown2Markdown lm2m = new LatexMarkdown2Markdown(
					new GeneratorConfig(cm.getBackground(), tocType, 0));
			if (cm.getInputFile() != null) {
				lm2m.parseTexMarkdow(Path.of(cm.getInputFile()));
			} else if (cm.getBaseDir() != null) {
				lm2m.start(Path.of(cm.getBaseDir()));
			} else {
				cm.showUsage();
			}
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
			Path tmpPath = destPath;
			if (destPath.equals(srcPath)) {
				tmpPath = buildTmpDestPath(srcPath);
				logger.info("Update " + destPath.toString());
			} else {
				logger.info("Generate " + destPath.toString());
			}
			try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(tmpPath))) {
				ChapterTranslator ct = new ChapterTranslator(config.getTocType());
				//
				// first pass, collect chapters and compute their numbers
				//
				AtomicInteger lineNumber = new AtomicInteger();
				try (Stream<String> stream = Files.lines(srcPath)) {
					stream.forEach(l -> ct.collectChapters(lineNumber.incrementAndGet(), l));
				}

				//
				// second pass, translate chapters and latex
				//
				lineNumber.set(0);
				try (Stream<String> stream = Files.lines(srcPath)) {
					stream.map(l -> ct.translateLine(lineNumber.incrementAndGet(), l))
							.filter(l -> l != null)
							.map(line -> translateLatexSections(srcPath, line))
							.forEach(l -> {
								pw.println(l);
								pw.flush();
							});
				}
			}
			if (destPath.equals(srcPath)) {
				renameTmpDestPath(tmpPath, srcPath);
			}
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Unexpected error", e);
		}
	}

	private void renameTmpDestPath(Path tmpPath, Path destPath) throws IOException {
		if (!destPath.toFile()
				.delete()) {
			throw new IOException("Unable to delete " + destPath);
		}
		if (!tmpPath.toFile()
				.renameTo(destPath.toFile())) {
			throw new IOException("Unable to rename " + tmpPath + " to " + destPath);
		}
	}

	private Path buildTmpDestPath(Path srcPath) {
		Path tmpPath;
		tmpPath = Path.of(srcPath + ".tmp.md");
		return tmpPath;
	}

	private String translateLatexSections(Path srcPath, String line) {
		StringBuffer translated = new StringBuffer();
		// LaTex sections are separated by $$
		// The regexp is a little bit complex because we must accept a single $ inside
		// the section
		Pattern latexSection = Pattern.compile("\\$\\$(?<latex>([^$]|(\\$[^$]))*)\\$\\$");
		Matcher m = latexSection.matcher(line);
		int pos = 0;
		while (m.find(pos)) {
			int start = m.start();
			int end = m.end();

			String beforeSection = line.substring(pos, start);
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
		String afterSection = line.substring(pos);
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
			return Optional.of(new SVGFormula(icon.getIconWidth(), icon.getIconHeight(), latexCode, destFile));
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
