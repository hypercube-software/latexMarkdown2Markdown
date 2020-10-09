package com.hypercube.translate;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class ChapterTranslator {
	private Logger logger = Logger.getLogger(ChapterTranslator.class.getName());

	private int currentChapterIndex;
	private List<Chapter> chapters = new ArrayList<>();
	private Pattern chapterSection = Pattern.compile("^(#+)\\s+(.+)$");

	public void collectChapters(String line) {
		Matcher m = chapterSection.matcher(line);
		if (m.matches()) {
			String header = m.group(1);
			String chapter = m.group(2);

			chapters.add(new Chapter(header, chapter));
		}
	}

	public String translateLine(String line) {
		// remove Typora TOC
		line = line.replace("[TOC]", "");

		Matcher m = chapterSection.matcher(line);
		if (m.matches()) {
			Chapter ch = chapters.get(currentChapterIndex++);

			if (currentChapterIndex == 1)
				return null;

			return ch.getHeader() + " " + ch.getNumberedTitle();
		} else {
			return line;
		}
	}

	public String buildTableOfContent() {
		if (chapters.size() == 0)
			return "";

		Chapter firstChapter = chapters.get(0);
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
		toc.append("# " + firstChapter.getTitle() + "\n");
		toc.append("**Table of content**")
				.append("\n")
				.append("\n");

		IntStream.range(0, chapters.size())
				.forEach(chIdx -> {
					Chapter ch = chapters.get(chIdx);
					logger.info(ch.getNumberedTitle());
					if (chIdx == 0) {
						toc.append("[" + ch.getNumberedTitle() + "]");
						toc.append("(#" + ch.getTitleAnchor() + ")");
						toc.append("  \n"); // force newline
					} else {
						if (ch.isNewSection()) {
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