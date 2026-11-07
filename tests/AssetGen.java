import chess.IconFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/** 生成安卓启动图标 PNG 与 macOS .icns 图标。 */
public class AssetGen {

    public static void main(String[] args) throws Exception {
        File png = new File(args[0]);
        File icns = new File(args[1]);
        png.getParentFile().mkdirs();
        icns.getParentFile().mkdirs();
        IconFactory.writePng(png, 192);
        writeIcns(icns);
        System.out.println("PNG : " + png.getAbsolutePath());
        System.out.println("ICNS: " + icns.getAbsolutePath());
    }

    private static void writeIcns(File f) throws Exception {
        int[] sizes = {16, 32, 128, 256, 512};
        String[] types = {"icp4", "icp5", "ic07", "ic08", "ic09"};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(body);
        for (int i = 0; i < sizes.length; i++) {
            BufferedImage img = IconFactory.render(sizes[i]);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(img, "png", png);
            byte[] data = png.toByteArray();
            d.writeBytes(types[i]);
            d.writeInt(8 + data.length);
            d.write(data);
        }
        d.flush();
        byte[] b = body.toByteArray();
        DataOutputStream out = new DataOutputStream(new FileOutputStream(f));
        out.writeBytes("icns");
        out.writeInt(8 + b.length);
        out.write(b);
        out.flush();
        out.close();
    }
}
