package chess;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 程序图标：纯代码绘制，并导出标准多尺寸 .ico 供安装包使用。 */
public final class IconFactory {

    private IconFactory() {}

    public static BufferedImage render(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            float s = size / 256f;
            // 深棕底盘
            g.setColor(new Color(48, 34, 26));
            g.fillRoundRect(0, 0, size, size, (int) (52 * s), (int) (52 * s));
            g.setColor(new Color(196, 152, 60));
            g.setStroke(new java.awt.BasicStroke(Math.max(1f, 7 * s)));
            g.drawRoundRect((int) (7 * s), (int) (7 * s), (int) (242 * s), (int) (242 * s),
                    (int) (44 * s), (int) (44 * s));
            // 红色棋牌
            g.setPaint(new java.awt.GradientPaint(0, 0, new Color(196, 52, 42),
                    size, size, new Color(136, 24, 22)));
            g.fillOval((int) (30 * s), (int) (30 * s), (int) (196 * s), (int) (196 * s));
            g.setColor(new Color(255, 226, 170));
            g.setStroke(new java.awt.BasicStroke(Math.max(1f, 6 * s)));
            g.drawOval((int) (43 * s), (int) (43 * s), (int) (170 * s), (int) (170 * s));
            // 「帥」字
            String[] families = {"KaiTi", "楷体", "STKaiti", "SimSun", "宋体", "Microsoft YaHei",
                    "微软雅黑", "Noto Serif CJK SC", "Serif", "Dialog"};
            java.util.Set<String> avail = new java.util.HashSet<>();
            for (String f : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
                avail.add(f.toLowerCase(java.util.Locale.ROOT));
            String fam = "Dialog";
            for (String f : families) if (avail.contains(f.toLowerCase(java.util.Locale.ROOT))) { fam = f; break; }
            g.setFont(new Font(fam, Font.BOLD, (int) (122 * s)));
            g.setColor(new Color(255, 240, 210));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("帥", (size - fm.stringWidth("帥")) / 2f,
                    size / 2f + (fm.getAscent() - fm.getDescent()) / 2f);
        } finally {
            g.dispose();
        }
        return img;
    }

    public static java.awt.Image frameIcon() { return render(64); }

    public static void writePng(File f, int size) throws IOException {
        ImageIO.write(render(size), "png", f);
    }

    /** 写出 Vista+ 兼容的 32bpp BMP 帧 ICO（不是 PNG 帧，jpackage / WiX 兼容性更好） */
    public static void writeIco(File out, int... sizes) throws IOException {
        ByteArrayOutputStream[] frames = new ByteArrayOutputStream[sizes.length];
        for (int i = 0; i < sizes.length; i++) frames[i] = bmpFrame(render(sizes[i]));

        int offset = 6 + 16 * sizes.length;
        ByteBuffer head = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
        head.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        for (int i = 0; i < sizes.length; i++) {
            int sz = sizes[i];
            head.put((byte) (sz >= 256 ? 0 : sz));
            head.put((byte) (sz >= 256 ? 0 : sz));
            head.put((byte) 0).put((byte) 0);
            head.putShort((short) 1).putShort((short) 32);
            head.putInt(frames[i].size());
            head.putInt(offset);
            offset += frames[i].size();
        }
        try (FileOutputStream fos = new FileOutputStream(out)) {
            fos.write(head.array());
            for (ByteArrayOutputStream f : frames) fos.write(f.toByteArray());
        }
    }

    private static ByteArrayOutputStream bmpFrame(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int maskStride = ((w + 31) / 32) * 4;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        // 像素数据：自下而上 BGRA
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                body.write((argb) & 0xFF);          // B
                body.write((argb >>> 8) & 0xFF);    // G
                body.write((argb >>> 16) & 0xFF);   // R
                body.write((argb >>> 24) & 0xFF);   // A
            }
        }
        for (int i = 0; i < maskStride * h; i++) body.write(0); // AND mask 全 0
        byte[] pixels = body.toByteArray();

        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        ByteBuffer bih = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
        bih.putInt(40).putInt(w).putInt(h * 2).putShort((short) 1).putShort((short) 32);
        bih.putInt(0).putInt(pixels.length).putInt(0).putInt(0).putInt(0).putInt(0);
        frame.write(bih.array(), 0, 40);
        frame.write(pixels, 0, pixels.length);
        return frame;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) return;
        writeIco(new File(args[0]), 16, 24, 32, 48, 64, 128, 256);
        System.out.println("ICO written: " + args[0]);
    }
}
