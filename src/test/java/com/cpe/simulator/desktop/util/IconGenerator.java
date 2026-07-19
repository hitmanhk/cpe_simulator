package com.cpe.simulator.desktop.util;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * One-shot generator for the application icon set. Run manually when the
 * design changes:
 * <pre>mvn -q compile exec:java ... (or run from IDEA)</pre>
 * Outputs:
 * <ul>
 *   <li>src/main/resources/icons/app-icon-{16,32,48,256}.png — stage/taskbar icons</li>
 *   <li>src/jpackage/app.ico — jpackage installer icon (PNG-compressed entries)</li>
 * </ul>
 * Design: rounded-square blue→violet gradient with a white signal-tower
 * glyph (device ⇄ ACS communication).
 */
public final class IconGenerator {

    private static final int[] SIZES = {16, 32, 48, 256};

    public static void main(String[] args) throws Exception {
        Path resourcesDir = Path.of("src/main/resources/icons");
        Path jpackageDir = Path.of("src/jpackage");
        Files.createDirectories(resourcesDir);
        Files.createDirectories(jpackageDir);

        List<byte[]> pngs = new ArrayList<>();
        for (int size : SIZES) {
            BufferedImage image = render(size);
            Path out = resourcesDir.resolve("app-icon-" + size + ".png");
            ImageIO.write(image, "PNG", out.toFile());
            pngs.add(toPngBytes(image));
            System.out.println("written " + out.toAbsolutePath());
        }
        Path ico = jpackageDir.resolve("app.ico");
        Files.write(ico, buildIco(pngs));
        System.out.println("written " + ico.toAbsolutePath());
    }

    static BufferedImage render(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double radius = size * 0.22;
        RoundRectangle2D.Double tile = new RoundRectangle2D.Double(0, 0, size, size, radius * 2, radius * 2);

        // 底：线性渐变 蓝(#3B82F6) → 紫(#8B5CF6)，左上→右下
        java.awt.GradientPaint gradient = new java.awt.GradientPaint(
                0, 0, new Color(0x3B, 0x82, 0xF6),
                size, size, new Color(0x8B, 0x5C, 0xF6));
        g.setPaint(gradient);
        g.fill(tile);

        // 高光：顶部柔和径向亮色，营造质感
        g.setClip(tile);
        RadialGradientPaint sheen = new RadialGradientPaint(
                new Point2D.Float(size * 0.3f, size * 0.18f), size * 0.9f,
                new float[]{0f, 1f},
                new Color[]{new Color(1f, 1f, 1f, 0.22f), new Color(1f, 1f, 1f, 0f)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE);
        g.setPaint(sheen);
        g.fillRect(0, 0, size, size);
        g.setClip(null);

        // 信号塔：中心圆点 + 三圈对称弧
        g.setColor(Color.WHITE);
        g.setComposite(AlphaComposite.SrcOver);
        float cx = size / 2f;
        float cy = size * 0.56f;
        float dotR = size * 0.075f;
        g.fill(new java.awt.geom.Ellipse2D.Float(cx - dotR, cy - dotR, dotR * 2, dotR * 2));

        float stroke = Math.max(1.5f, size * 0.055f);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 1; i <= 3; i++) {
            float r = dotR + size * 0.13f * i;
            float spread = 52f; // 半张角
            // 左弧
            g.draw(new java.awt.geom.Arc2D.Float(cx - r, cy - r, r * 2, r * 2,
                    180f - spread, spread * 2, java.awt.geom.Arc2D.OPEN));
            // 右弧
            g.draw(new java.awt.geom.Arc2D.Float(cx - r, cy - r, r * 2, r * 2,
                    -spread, spread * 2, java.awt.geom.Arc2D.OPEN));
        }
        g.dispose();
        return image;
    }

    private static byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", out);
        return out.toByteArray();
    }

    /** Builds an ICO file embedding the PNG images (Windows Vista+ format). */
    static byte[] buildIco(List<byte[]> pngs) {
        int count = pngs.size();
        int headerSize = 6 + 16 * count;
        int total = headerSize;
        for (byte[] png : pngs) {
            total += png.length;
        }
        ByteBuffer buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 0);          // reserved
        buffer.putShort((short) 1);          // type: icon
        buffer.putShort((short) count);
        int offset = headerSize;
        for (int i = 0; i < count; i++) {
            int size = SIZES[i];
            byte[] png = pngs.get(i);
            buffer.put((byte) (size >= 256 ? 0 : size)); // width (0 = 256)
            buffer.put((byte) (size >= 256 ? 0 : size)); // height
            buffer.put((byte) 0);                        // palette
            buffer.put((byte) 0);                        // reserved
            buffer.putShort((short) 1);                  // planes
            buffer.putShort((short) 32);                 // bpp
            buffer.putInt(png.length);
            buffer.putInt(offset);
            offset += png.length;
        }
        for (byte[] png : pngs) {
            buffer.put(png);
        }
        return buffer.array();
    }
}
