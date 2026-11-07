package chess;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Font;

/** 程序入口。 */
public final class Main {

    private Main() {}

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 保留跨平台默认外观
        }
        Font plain = BoardPanel.uiFont(Font.PLAIN, 13);
        Font bold = BoardPanel.uiFont(Font.BOLD, 13);
        UIManager.put("Menu.font", plain);
        UIManager.put("MenuItem.font", plain);
        UIManager.put("CheckBoxMenuItem.font", plain);
        UIManager.put("RadioButtonMenuItem.font", plain);
        UIManager.put("MenuBar.font", plain);
        UIManager.put("Button.font", bold);
        UIManager.put("Label.font", plain);
        UIManager.put("OptionPane.messageFont", plain);
        UIManager.put("OptionPane.buttonFont", bold);
        UIManager.put("TextArea.font", plain);
        UIManager.put("FileChooser.listFont", plain);
        java.awt.Color dark = new java.awt.Color(48, 40, 33);
        java.awt.Color ink = new java.awt.Color(238, 223, 197);
        UIManager.put("MenuBar.background", dark);
        UIManager.put("Menu.background", dark);
        UIManager.put("Menu.foreground", ink);
        UIManager.put("MenuItem.background", dark);
        UIManager.put("MenuItem.foreground", ink);
        UIManager.put("MenuItem.selectionBackground", new java.awt.Color(102, 82, 58));
        UIManager.put("MenuItem.selectionForeground", java.awt.Color.WHITE);
        UIManager.put("CheckBoxMenuItem.background", dark);
        UIManager.put("CheckBoxMenuItem.foreground", ink);
        UIManager.put("RadioButtonMenuItem.background", dark);
        UIManager.put("RadioButtonMenuItem.foreground", ink);
        UIManager.put("PopupMenu.background", dark);
        SwingUtilities.invokeLater(() -> new GameFrame().setVisible(true));
    }
}
