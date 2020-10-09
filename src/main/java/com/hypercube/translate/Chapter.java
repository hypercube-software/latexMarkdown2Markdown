package com.hypercube.translate;

/**
 * Chapter collected in the markdown
 * 
 * header can be #, ##, ###, or ####
 * 
 */
public class Chapter {
	private String header;
	private String title;
	private String numberedTitle;
	private boolean newSection;

	public Chapter(String header, String title) {
		super();
		this.header = header;
		this.title = title;
		this.newSection = false;
		this.numberedTitle = null;
	}

	public boolean isNewSection() {
		return newSection;
	}

	public void setNewSection(boolean newSection) {
		this.newSection = newSection;
	}

	public String getHeader() {
		return header;
	}

	public String getTitle() {
		return title;
	}

	public String getNumberedTitle() {
		return numberedTitle;
	}

	public void setNumberedTitle(String numberedTitle) {
		this.numberedTitle = numberedTitle;
	}

	public String getNumberedTitleAnchor() {
		return getGitHubAnchor(numberedTitle);
	}
	public String getTitleAnchor() {
		return getGitHubAnchor(title);
	}
	//
	// This cleanup matches what Github do
	//
	private String getGitHubAnchor(String title)
	{
		return title.toLowerCase()
				.replaceAll("[^a-zA-Z0-9- ]", "")
				.replaceAll(" ", "-")
				.replace("---", "--")
				.replaceAll("-+$", "");
	}
	@Override
	public String toString() {
		return title;
	}
}
