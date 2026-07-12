package net.runelite.client.plugins.microbot.netomahoganyhomes;

import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;

import java.awt.image.BufferedImage;

class NetoMahoganyHomesWorldPoint extends WorldMapPoint
{
    private final NetoMahoganyHomesPlugin plugin;
    private final Point point;

    NetoMahoganyHomesWorldPoint(final WorldPoint worldPoint, final NetoMahoganyHomesPlugin plugin)
    {
        super(worldPoint, null);
        this.plugin = plugin;

        final BufferedImage image = plugin.getMapArrow();
        point = new Point(image.getWidth() / 2, image.getHeight());

        this.setSnapToEdge(true);
        this.setJumpOnClick(true);
        this.setImage(image);
        this.setImagePoint(point);
        this.setName("Mahogany Homes Contract");
    }

    @Override
    public void onEdgeSnap()
    {
        this.setImage(plugin.getMapIcon());
        this.setImagePoint(null);
    }

    @Override
    public void onEdgeUnsnap()
    {
        this.setImage(plugin.getMapArrow());
        this.setImagePoint(point);
    }
}
