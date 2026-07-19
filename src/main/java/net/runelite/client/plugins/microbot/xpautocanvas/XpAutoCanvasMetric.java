package net.runelite.client.plugins.microbot.xpautocanvas;

public enum XpAutoCanvasMetric
{
	TIME_TO_LEVEL("Time To Level", "TTL"),
	XP_GAINED("Xp Gained", "XP Gained"),
	XP_HOUR("Xp Hour", "XP/hr"),
	XP_LEFT("Xp Left", "XP Left"),
	ACTIONS_LEFT("Actions Left", "Actions"),
	ACTIONS_HOUR("Actions Hour", "Actions/hr"),
	ACTIONS_DONE("Actions Done", "Actions Done");

	private final String displayName;
	private final String overlayLabel;

	XpAutoCanvasMetric(String displayName, String overlayLabel)
	{
		this.displayName = displayName;
		this.overlayLabel = overlayLabel;
	}

	String getOverlayLabel()
	{
		return overlayLabel;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
