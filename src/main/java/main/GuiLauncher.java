package main;

/**
 * JavaFX起動用ラッパー。
 * Application継承クラスを直接起動したときの
 * "JavaFX runtime components are missing" 回避のために使う。
 */
public class GuiLauncher {
    public static void main(String[] args) {
        ui.MainApp.main(args);
    }
}
