package com.hypercube;

public class GeneratorConfig {	
	public enum TocType {
		NO_TOC,
		TOC,
		TAB_TOC
	};
	
	private String hexBackgroundColor; // The format is like HTML: "#RRGGBB"
	
	private TocType tocType;
	
	private int margin;		
	
	public GeneratorConfig(String hexBackgroundColor, TocType tocType, int margin) {
		super();
		this.hexBackgroundColor = hexBackgroundColor;
		this.tocType = tocType;
		this.margin = margin;
	}

	public TocType getTocType() {
		return tocType;
	}
	
	int getMargin() {
		return margin;
	}

	public String getHexBackgroundColor() {
		return hexBackgroundColor;
	}
}
