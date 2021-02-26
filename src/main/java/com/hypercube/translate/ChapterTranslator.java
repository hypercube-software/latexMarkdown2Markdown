package com.hypercube.translate;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import com.hypercube.GeneratorConfig.TocType;

public class ChapterTranslator {
	private Logger logger = Logger.getLogger(ChapterTranslator.class.getName());

	private int currentChapterIndex;
	private List<Chapter> chapters = new ArrayList<>();
	private Pattern chapterSection = Pattern.compile("^(#+)\\s+(.+)$");
	private TocType tocType;
	private boolean inCodeBlock = false;
	private boolean inTOC = false;

	public ChapterTranslator(TocType tocType) {
		super();
		this.tocType = tocType;
	}

	public void collectChapters(int lineNumber, String line) {
		if (!inCodeBlock && line.startsWith("```"))
			inCodeBlock = true;
		else if (inCodeBlock && line.startsWith("```"))
			inCodeBlock = false;

		if (!inTOC && line.startsWith("# Table of content"))
			inTOC = true;
		else if (inTOC && line.startsWith("#"))
			inTOC = false;

		Matcher m = chapterSection.matcher(line);
		if (!inCodeBlock && !inTOC && lineNumber > 1 && m.matches()) {
			String header = m.group(1);
			String chapter = m.group(2);

			chapters.add(new Chapter(lineNumber, header, chapter));
		}

	}

	public String translateLine(int lineNumber, String line) {
		if (!inCodeBlock && line.startsWith("```"))
			inCodeBlock = true;
		else if (inCodeBlock && line.startsWith("```"))
			inCodeBlock = false;

		// handle our own TOC (doc update)
		if (!inTOC && line.startsWith("# Table of content")) {
			inTOC = true;
			return null;
		} else if (inTOC && line.startsWith("#")) {
			inTOC = false;
			return buildTableOfContent() + translateLine(lineNumber, line);
		} else if (inTOC) {
			return null;
		}

		// handle Typora TOC
		if (!inCodeBlock && line.indexOf("[TOC]") != -1)
			return buildTableOfContent();

		// first line is supposed to be the document title
		if (lineNumber == 1)
			return line;

		Matcher m = chapterSection.matcher(line);
		if (!inCodeBlock && lineNumber > 1 && m.matches()) {
			Chapter ch = chapters.get(currentChapterIndex++);

			return ch.getHeader() + " " + ch.getNumberedTitle();
		} else {
			return line;
		}
	}

	public String buildTableOfContent() {
		if (chapters.size() == 0 || tocType == TocType.NO_TOC)
			return "";

		ChapterCounter chapterCounter = new ChapterCounter();
		for (Chapter ch : chapters) {
			chapterCounter.onNewMarkdownChapter(ch);

			if ((chapterCounter.getPreviousDepth() > 2 && chapterCounter.getCurrentDepth() == 2)
					|| (chapterCounter.getPreviousDepth() > 1 && chapterCounter.getCurrentDepth() == 1)) {
				ch.setNewSection(true);
			}

			ch.setNumberedTitle(chapterCounter.getCurrentState() + " " + ch.getTitle());
		}

		StringBuffer toc = new StringBuffer();
		toc.append("# Table of content")
				.append("\n")
				.append("\n");

		IntStream.range(0, chapters.size())
				.forEach(chIdx -> {
					Chapter ch = chapters.get(chIdx);
					logger.info(ch.getNumberedTitle());
					if (tocType == TocType.TAB_TOC) {
						for (int i = 0; i < ch.getHeader()
								.length() - 1; i++)
							toc.append("&nbsp;&nbsp;&nbsp;&nbsp;");
					}
					if (chIdx == 0) {
						toc.append("[" + ch.getNumberedTitle() + "]");
						toc.append("(#" + ch.getTitleAnchor() + ")");
						toc.append("  \n"); // force newline
					} else {
						if (ch.isNewSection() && tocType != TocType.TAB_TOC) {
							toc.append("  \n"); // force newline
							toc.append("  \n"); // force newline
						}
						toc.append("[" + ch.getNumberedTitle() + "]");
						toc.append("(#" + ch.getNumberedTitleAnchor() + ")");
						toc.append("  \n"); // force newline
					}
				});

		return toc.toString();
	}
}