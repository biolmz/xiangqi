import javax.imageio.ImageIO;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;

/** 开发辅助：抓取当前屏幕用于界面验收。 */
public class Screenshot {
    public static void main(String[] args) throws Exception {
        Thread.sleep(Long.parseLong(args.length > 2 ? args[2] : "1200"));
        java.awt.Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle rect = new Rectangle(0, 0, d.width, d.height);
        BufferedImage img = new Robot().createScreenCapture(rect);
        ImageIO.write(img, "png", new File(args[0]));
        System.out.println("saved " + args[0] + " " + d.width + "x" + d.height);
    }
}
