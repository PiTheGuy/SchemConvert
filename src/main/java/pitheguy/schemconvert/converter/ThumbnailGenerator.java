package pitheguy.schemconvert.converter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ThumbnailGenerator {
    private static final Map<String, Color> COLOR_MAP = new HashMap<>();
    private static final Map<String, BufferedImage> TEXTURE_CACHE = new HashMap<>(); // Cache for loaded textures
    private static final int THUMBNAIL_SIZE = 256;
    private static final int BLOCK_SIZE = 16; // 16 matches standard texture size, best for quality

    static {
        loadColors();
    }

    private static void loadColors() {
        try (InputStream stream = ThumbnailGenerator.class.getResourceAsStream("/block_colors.json")) {
            if (stream == null) {
                System.err.println("block_colors.json not found!");
                return;
            }
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, String>>() {
            }.getType();
            Map<String, String> colors = gson.fromJson(new InputStreamReader(stream), type);
            for (Map.Entry<String, String> entry : colors.entrySet()) {
                COLOR_MAP.put(entry.getKey(), decodeColor(entry.getValue()));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Color decodeColor(String hex) {
        if (hex.startsWith("#"))
            hex = hex.substring(1);
        if (hex.length() == 8) {
            long val = Long.parseLong(hex, 16);
            int a = (int) ((val >> 24) & 0xFF);
            int r = (int) ((val >> 16) & 0xFF);
            int g = (int) ((val >> 8) & 0xFF);
            int b = (int) (val & 0xFF);
            return new Color(r, g, b, a);
        } else {
            return new Color(Integer.parseInt(hex, 16));
        }
    }

    // Attempt to load texture from ./textures/block/name.png
    private static BufferedImage getTexture(String name) {
        if (TEXTURE_CACHE.containsKey(name))
            return TEXTURE_CACHE.get(name);

        File textureFile = new File("textures/block/" + name + ".png");
        if (!textureFile.exists()) {
            // Try stripping 'minecraft:' prefix if present and file absent
            if (name.contains(":")) {
                textureFile = new File("textures/block/" + name.substring(name.indexOf(":") + 1) + ".png");
            }
        }

        BufferedImage img = null;
        if (textureFile.exists()) {
            try {
                img = ImageIO.read(textureFile);
            } catch (IOException e) {
                // Ignore, leave null
            }
        }
        // Don't cache null here if we want to allow synthetic generation to retry or be
        // checked later.
        // But our logic handles it.
        return img;
    }

    public static byte[] generate(Schematic schematic) {
        int[] size = schematic.getSize();
        int width = size[0];
        int height = size[1];
        int length = size[2];

        // Iso Projection bounds
        int minIsoX = Integer.MAX_VALUE, maxIsoX = Integer.MIN_VALUE;
        int minIsoY = Integer.MAX_VALUE, maxIsoY = Integer.MIN_VALUE;

        int[][] corners = {
                { 0, 0, 0 }, { width, 0, 0 }, { 0, height, 0 }, { width, height, 0 },
                { 0, 0, length }, { width, 0, length }, { 0, height, length }, { width, height, length }
        };

        for (int[] corner : corners) {
            Point p = project(corner[0], corner[1], corner[2]);
            if (p.x < minIsoX)
                minIsoX = p.x;
            if (p.x > maxIsoX)
                maxIsoX = p.x;
            if (p.y < minIsoY)
                minIsoY = p.y;
            if (p.y > maxIsoY)
                maxIsoY = p.y;
        }

        int imgWidth = maxIsoX - minIsoX + BLOCK_SIZE * 4;
        int imgHeight = maxIsoY - minIsoY + BLOCK_SIZE * 4;

        imgWidth = Math.min(imgWidth, 4096);
        imgHeight = Math.min(imgHeight, 4096);

        BufferedImage image = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        // Use Nearest Neighbor for crisp pixel art
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int offsetX = -minIsoX + BLOCK_SIZE * 2;
        int offsetY = -minIsoY + BLOCK_SIZE * 2;

        // Render Order: Back-to-Front
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    String block = schematic.getBlock(x, y, z);
                    if (isTransparent(block))
                        continue;

                    boolean topVisible = (y == height - 1) || isTransparent(schematic.getBlock(x, y + 1, z));
                    boolean rightVisible = (x == width - 1) || isTransparent(schematic.getBlock(x + 1, y, z));
                    boolean leftVisible = (z == length - 1) || isTransparent(schematic.getBlock(x, y, z + 1));

                    if (!topVisible && !rightVisible && !leftVisible)
                        continue;

                    String baseBlock = block.contains("[") ? block.substring(0, block.indexOf("[")) : block;
                    String blockName = baseBlock.contains(":") ? baseBlock.substring(baseBlock.indexOf(":") + 1)
                            : baseBlock;

                    Color color = COLOR_MAP.get(baseBlock);
                    if (color == null) {
                        if (baseBlock.endsWith("planks"))
                            color = new Color(162, 132, 79);
                        else if (baseBlock.endsWith("log") || baseBlock.endsWith("wood") || baseBlock.endsWith("s"))
                            color = new Color(106, 80, 48);
                        else if (baseBlock.endsWith("leaves"))
                            color = new Color(52, 121, 30);
                        else
                            color = Color.GRAY;
                    }

                    drawBlock(g2d, x, y, z, offsetX, offsetY, color, baseBlock, blockName, topVisible, rightVisible,
                            leftVisible);
                }
            }
        }
        g2d.dispose();

        // Vertical flip to correct orientation
        image = flip(image);

        return scaleImage(image, THUMBNAIL_SIZE);
    }

    private static void drawBlock(Graphics2D g, int x, int y, int z, int offX, int offY, Color c, String type,
            String name, boolean topVis, boolean rightVis, boolean leftVis) {
        Point p = project(x, y, z);
        int px = p.x + offX;
        int py = p.y + offY;

        int w = BLOCK_SIZE;
        int h = BLOCK_SIZE / 2;
        int v = BLOCK_SIZE;

        // Polygons needed for background fill and fallback
        Polygon top = new Polygon(new int[] { px, px + w, px, px - w },
                new int[] { py - v, py - v + h, py - v + 2 * h, py - v + h }, 4);
        Polygon right = new Polygon(new int[] { px + w, px + w, px, px },
                new int[] { py - v + h, py + h, py + 2 * h, py - v + 2 * h }, 4);
        Polygon left = new Polygon(new int[] { px - w, px, px, px - w },
                new int[] { py - v + h, py + 2 * h, py + 2 * h, py + h }, 4);

        if (topVis) {
            // Always fill background first for transparent textures
            g.setColor(shade(c, 1.0f));
            g.fill(top);

            BufferedImage topTex = loadOrGenerateTexture(name + "_top", c);
            if (topTex == null)
                topTex = loadOrGenerateTexture(name, c);

            if (topTex != null) {
                // Map (0,0)->Left(px-w, py-v+h), (w,0)->Top(px, py-v), (0,h)->Bottom(px,
                // py-v+2h)
                drawAffineImage(g, topTex, px - w, py - v + h, px, py - v, px, py - v + 2 * h, 1.0f, shouldTint(name),
                        c);
            } else {
                // Fallback
                drawTexture(g, top, type, "top", shade(c, 1.0f));
            }
        }

        if (rightVis) {
            g.setColor(shade(c, 0.6f));
            g.fill(right);

            BufferedImage sideTex = loadOrGenerateTexture(name, c);
            if (sideTex != null) {
                // Map (0,0)->Top-Near(px, py-v+2h), (w,0)->Top-Far(px+w, py-v+h),
                // (0,h)->Bottom-Near(px, py+2h)
                drawAffineImage(g, sideTex, px, py - v + 2 * h, px + w, py - v + h, px, py + 2 * h, 0.6f,
                        shouldTint(name), c);
            } else {
                drawTexture(g, right, type, "side", shade(c, 0.6f));
            }
        }

        if (leftVis) {
            g.setColor(shade(c, 0.8f));
            g.fill(left);

            BufferedImage sideTex = loadOrGenerateTexture(name, c);
            if (sideTex != null) {
                // Map (0,0)->Top-Far(px-w, py-v+h), (w,0)->Top-Near(px, py-v+2h),
                // (0,h)->Bottom-Far(px-w, py+h)
                drawAffineImage(g, sideTex, px - w, py - v + h, px, py - v + 2 * h, px - w, py + h, 0.8f,
                        shouldTint(name), c);
            } else {
                drawTexture(g, left, type, "side", shade(c, 0.8f));
            }
        }
    }

    private static BufferedImage loadOrGenerateTexture(String name, Color baseColor) {
        if (TEXTURE_CACHE.containsKey(name))
            return TEXTURE_CACHE.get(name);

        // 1. Try Loading External File
        BufferedImage img = getTexture(name);

        // 2. If missing, Generate Synthetic
        if (img == null) {
            img = generateSyntheticTexture(name, baseColor);
        }

        TEXTURE_CACHE.put(name, img);
        return img;
    }

    // Generates a 16x16 pixel art texture procedurally
    private static BufferedImage generateSyntheticTexture(String name, Color c) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Fill base
        g.setColor(c);
        g.fillRect(0, 0, 16, 16);

        // Noise
        Random rand = new Random(name.hashCode()); // Consistent noise per block type

        if (name.contains("planks")) {
            // Streaks
            for (int i = 0; i < 16; i++) {
                if (rand.nextBoolean()) {
                    g.setColor(shade(c, 0.9f));
                    g.drawLine(i, 0, i, 16);
                }
                if (i % 4 == 0) {
                    g.setColor(shade(c, 0.85f));
                    g.drawLine(i, 0, i, 16);
                }
            }
            // Horizontal cuts
            g.setColor(shade(c, 0.8f));
            for (int y = 0; y < 16; y += 4) {
                int offset = rand.nextInt(16);
                g.drawLine(0, y + offset % 4, 16, y + offset % 4);
            }
        } else if (name.equals("bricks") || name.contains("brick")) {
            // Grid
            g.setColor(shade(c, 0.8f));
            for (int y = 0; y < 16; y += 8)
                g.drawLine(0, y, 16, y);
            for (int x = 0; x < 16; x += 8) {
                g.drawLine(x, 0, x, 7);
                g.drawLine(x + 4, 8, x + 4, 16);
            }
        } else if (name.contains("log") || name.contains("wood")) {
            if (name.endsWith("_top")) {
                // Rings
                g.setColor(shade(c, 0.85f));
                g.drawOval(2, 2, 12, 12);
                g.drawOval(5, 5, 6, 6);
                g.setColor(shade(c, 0.9f));
                g.fillRect(7, 7, 2, 2);
            } else {
                // Bark
                for (int i = 0; i < 16; i++) {
                    if (rand.nextFloat() > 0.6) {
                        g.setColor(shade(c, 0.85f + rand.nextFloat() * 0.1f));
                        g.drawLine(i, 0, i, 16);
                    }
                }
            }
        } else if (name.contains("leaves") || name.contains("grass") || name.contains("vine")) {
            // Transparency Holes - Completely rebuild image with transparency
            img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            g = img.createGraphics();
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    if (rand.nextFloat() > 0.3) { // 70% chance of block
                        g.setColor(c);
                        g.fillRect(x, y, 1, 1);
                    }
                }
            }
        } else {
            // Default Noise
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    float noise = 0.9f + rand.nextFloat() * 0.2f;
                    g.setColor(shade(c, noise));
                    g.fillRect(x, y, 1, 1);
                }
            }
        }

        g.dispose();
        return img;
    }

    private static Point project(int x, int y, int z) {
        int px = (x - z) * BLOCK_SIZE;
        int py = (x + z) * (BLOCK_SIZE / 2) - (y * BLOCK_SIZE);
        return new Point(px, py);
    }

    private static boolean isTransparent(String block) {
        if (block == null)
            return true;
        if (block.equals("minecraft:air") || block.equals("minecraft:structure_void")
                || block.equals("minecraft:void_air") || block.equals("minecraft:cave_air")
                || block.equals("minecraft:barrier") || block.equals("minecraft:light"))
            return true;

        if (block.contains("glass") || block.contains("pane"))
            return true;
        if (block.contains("vine") || block.contains("lichen"))
            return true;
        if (block.contains("grass") || block.contains("fern") || block.contains("flower") || block.contains("rose")
                || block.contains("fungus") || block.contains("roots") || block.contains("sprouts"))
            return true;
        if (block.contains("crop") || block.contains("stem") || block.contains("shroomlight")
                || block.contains("sapling") || block.contains("bamboo"))
            return true;
        if (block.contains("torch") || block.contains("lantern") || block.contains("candle") || block.contains("fire")
                || block.contains("camp"))
            return true;
        if (block.contains("door") || block.contains("trapdoor") || block.contains("fence") || block.contains("wall")
                || block.contains("gate") || block.contains("bar"))
            return true;
        if (block.contains("rail") || block.contains("dust") || block.contains("wire") || block.contains("button")
                || block.contains("lever") || block.contains("plate"))
            return true;
        return false;
    }

    private static boolean shouldTint(String name) {
        return name.contains("leaves") || name.contains("grass") || name.contains("fern") || name.contains("vine")
                || name.contains("lily");
    }

    // Legacy fallback drawing method (now mostly unused if synthetic generation
    // works)
    private static void drawTexture(Graphics2D g, Polygon poly, String type, String face, Color baseColor) {
        g.setColor(shade(baseColor, 0.9f));
        Rectangle bounds = poly.getBounds();
        int step = Math.max(1, BLOCK_SIZE / 4);

        if (type.contains("planks")) {
            for (int i = 0; i < bounds.width; i += step) {
                if (i % 2 == 0)
                    drawLinesInPoly(g, poly, i, 0, i + step, bounds.height);
            }
        } else if (type.contains("log") || type.contains("wood")) {
            if (face.equals("top")) {
                int w = bounds.width;
                int h = bounds.height;
                if (w > 4 && h > 4)
                    g.drawOval(bounds.x + 2, bounds.y + 2, w - 4, h - 4);
                if (w > 8 && h > 8)
                    g.drawOval(bounds.x + 4, bounds.y + 4, w - 8, h - 8);
            } else {
                drawLinesInPoly(g, poly, bounds.width / 3, 0, bounds.width / 3, bounds.height);
                drawLinesInPoly(g, poly, 2 * bounds.width / 3, 0, 2 * bounds.width / 3, bounds.height);
            }
        } else if (type.contains("brick")) {
            for (int i = 0; i < bounds.height; i += step)
                drawLinesInPoly(g, poly, 0, i, bounds.width, i);
            for (int i = 0; i < bounds.width; i += step * 2)
                drawLinesInPoly(g, poly, i, 0, i, bounds.height);
        } else {
            addNoiseToPoly(g, poly, baseColor);
        }
    }

    private static void drawLinesInPoly(Graphics2D g, Polygon poly, int x1, int y1, int x2, int y2) {
        Shape oldClip = g.getClip();
        g.setClip(poly);
        Rectangle b = poly.getBounds();
        g.drawLine(b.x + x1, b.y + y1, b.x + x2, b.y + y2);
        g.setClip(oldClip);
    }

    private static void addNoiseToPoly(Graphics2D g, Polygon poly, Color c) {
        Random rand = new Random(c.getRGB());
        Shape oldClip = g.getClip();
        g.setClip(poly);
        Rectangle b = poly.getBounds();
        for (int i = 0; i < 10; i++) {
            int x = rand.nextInt(b.width);
            int y = rand.nextInt(b.height);
            g.setColor(rand.nextBoolean() ? shade(c, 0.9f) : shade(c, 1.1f));
            g.fillRect(b.x + x, b.y + y, 2, 2);
        }
        g.setClip(oldClip);
    }

    private static void drawAffineImage(Graphics2D g, BufferedImage img, int x0, int y0, int x1, int y1, int x2, int y2,
            float brightness, boolean tint, Color tintColor) {
        // Calculate AffineTransform that maps (0,0)->(x0,y0), (w,0)->(x1,y1),
        // (0,h)->(x2,y2)
        double w = img.getWidth();
        double h = img.getHeight();

        // Matrix:
        // x' = m00*x + m01*y + m02
        // y' = m10*x + m11*y + m12

        // 0,0 -> x0, y0 => m02 = x0, m12 = y0
        // w,0 -> x1, y1 => m00*w + x0 = x1 => m00 = (x1-x0)/w
        // m10*w + y0 = y1 => m10 = (y1-y0)/w
        // 0,h -> x2, y2 => m01*h + x0 = x2 => m01 = (x2-x0)/h
        // m11*h + y0 = y2 => m11 = (y2-y0)/h

        double m00 = (x1 - x0) / w;
        double m10 = (y1 - y0) / w;
        double m01 = (x2 - x0) / h;
        double m11 = (y2 - y0) / h;
        double m02 = x0;
        double m12 = y0;

        AffineTransform tx = new AffineTransform(m00, m10, m01, m11, m02, m12);

        Composite oldComp = g.getComposite();

        g.drawImage(img, tx, null);

        if (tint) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.6f));
            g.setColor(tintColor);
            g.fill(new Polygon(new int[] { x0, x1, x1 + x2 - x0, x2 }, new int[] { y0, y1, y1 + y2 - y0, y2 }, 4));
            g.setComposite(oldComp);
        }

        if (brightness < 1.0f) {
            // Draw shading overlay
            int x3 = x1 + x2 - x0;
            int y3 = y1 + y2 - y0;
            Polygon poly = new Polygon(new int[] { x0, x1, x3, x2 }, new int[] { y0, y1, y3, y2 }, 4);

            int alpha = (int) (255 * (1.0f - brightness));
            g.setColor(new Color(0, 0, 0, alpha));
            g.fill(poly);
        }
    }

    private static Color shade(Color c, float factor) {
        return new Color(
                clamp((int) (c.getRed() * factor)),
                clamp((int) (c.getGreen() * factor)),
                clamp((int) (c.getBlue() * factor)),
                c.getAlpha());
    }

    private static int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }

    private static BufferedImage flip(BufferedImage image) {
        AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
        tx.translate(0, -image.getHeight(null));
        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(image, null);
    }

    private static byte[] scaleImage(BufferedImage original, int size) {
        double scale = Math.min((double) size / original.getWidth(), (double) size / original.getHeight());
        int scaledWidth = (int) (original.getWidth() * scale);
        int scaledHeight = (int) (original.getHeight() * scale);

        if (scaledWidth <= 0)
            scaledWidth = 1;
        if (scaledHeight <= 0)
            scaledHeight = 1;

        BufferedImage thumbnail = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = thumbnail.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int xOffset = (size - scaledWidth) / 2;
        int yOffset = (size - scaledHeight) / 2;

        g.drawImage(original, xOffset, yOffset, scaledWidth, scaledHeight, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(thumbnail, "png", baos);
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
        return baos.toByteArray();
    }
}
