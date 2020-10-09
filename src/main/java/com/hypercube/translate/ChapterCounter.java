package com.hypercube.translate;

import java.util.ArrayList;
import java.util.List;

//
// Compute the chapter numbering
//
public class ChapterCounter {
	private List<Integer> counters = new ArrayList<>();
	private int previousSize = 0;

	//
	// header can be #, ##, ###, or ####
	// if suddently we jump from # to ### this will raise an exception
	//
	public void onNewMarkdownChapter(Chapter chapter) {
		int depth = chapter.getHeader()
				.length();
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
