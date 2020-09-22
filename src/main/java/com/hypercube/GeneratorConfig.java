package com.hypercube;

public class GeneratorConfig {	
	
	private String hexBackgroundColor; // The format is like HTML: "#RRGGBB"
	
	private boolean generateToc;
	
	private int margin;		
	
	public GeneratorConfig(String hexBackgroundColor, boolean generateToc, int margin) {
		super();
		this.hexBackgroundColor = hexBackgroundColor;
		this.generateToc = generateToc;
		this.margin = margin;
	}

	public boolean isGenerateToc() {
		return generateToc;
	}

	public int getMargin() {
		return margin;
	}

	public String getHexBackgroundColor() {
		return hexBackgroundColor;
	}
}
