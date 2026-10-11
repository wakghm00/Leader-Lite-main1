package leader.module.modules.render;

import leader.module.Module;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.ModeProperty;
import leader.util.FontRender;

public class FontManager extends Module {

    public static final BooleanProperty customFont = new BooleanProperty("CustomFont", false);
    public static final ModeProperty font = new ModeProperty("Font", 0, new String[]{
            "Xylitol", "Xylitol Bold", "HarmonyOS", "HarmonyOS Med",
            "Inter", "NotoSans", "NotoSansSC", "Nursultan",
            "ProductSans", "SF Display", "SF Rounded B", "SF Rounded M", "SF Rounded R"
    });
    public static final FloatProperty fontSize = new FloatProperty("FontSize", 18.0F, 10.0F, 30.0F);
    private static int lastFontMode = -1;

    public FontManager() {
        super("FontManager", false);
    }

    @Override
    public void onEnabled() {
        syncFontMode();
    }

    @Override
    public void onDisabled() {
    }

    private static void syncFontMode() {
        int currentMode = font.getValue();
        if (currentMode != lastFontMode) {
            lastFontMode = currentMode;
            FontRender.setFontMode(currentMode);
        }
    }

    // Custom font sizes retain their proportions relative to the global 18-point default.
    private static float scaleSize(float size) {
        return customFont.getValue() ? size * fontSize.getValue() / 18.0F : size;
    }

    public static void drawString(String text, float x, float y, int color, boolean shadow) {
        drawString(text, x, y, color, shadow, 18.0F);
    }

    public static void drawString(String text, float x, float y, int color, boolean shadow, float size) {
        syncFontMode();
        FontRender.drawString(text, x, y, color, shadow, scaleSize(size), customFont.getValue());
    }

    public static void drawStringWithShadow(String text, float x, float y, int color) {
        drawStringWithShadow(text, x, y, color, 18.0F);
    }

    public static void drawStringWithShadow(String text, float x, float y, int color, float size) {
        syncFontMode();
        FontRender.drawStringWithShadow(text, x, y, color, scaleSize(size), customFont.getValue());
    }

    public static int getStringWidth(String text) {
        return getStringWidth(text, 18.0F);
    }

    public static int getStringWidth(String text, float size) {
        syncFontMode();
        return FontRender.getStringWidth(text, scaleSize(size), customFont.getValue());
    }

    public static int getFontHeight() {
        return getFontHeight(18.0F);
    }

    public static int getFontHeight(float size) {
        syncFontMode();
        return FontRender.getFontHeight(scaleSize(size), customFont.getValue());
    }

    public static float getBaseline(float size) {
        syncFontMode();
        return FontRender.getBaseline(scaleSize(size), customFont.getValue());
    }

    public static float getCapHeight(float size) {
        syncFontMode();
        return FontRender.getCapHeight(scaleSize(size), customFont.getValue());
    }
}
