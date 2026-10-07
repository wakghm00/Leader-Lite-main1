package leader.module.modules.render;

import leader.Leader;
import leader.event.EventTarget;
import leader.events.KeyEvent;
import leader.events.Render2DEvent;
import leader.module.Module;
import leader.property.Property;
import leader.property.properties.BooleanProperty;
import leader.property.properties.FloatProperty;
import leader.property.properties.IntProperty;
import leader.property.properties.ModeProperty;
import leader.property.properties.PercentProperty;
import leader.util.KeyBindUtil;
import leader.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TabGui extends Module {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final int ROW_HEIGHT = 12;
    private static final int MAX_ROWS = 20;
    private static final int GAP = 4;
    private static final int[] WIDTHS = {76, 82, 92, 82};
    private static final int BACKGROUND = new Color(19, 19, 19, 220).getRGB();
    private static final int GRAY = new Color(165, 165, 165).getRGB();

    private final LinkedHashMap<String, List<Module>> categories = new LinkedHashMap<>();
    private int level;
    private int categoryIndex;
    private int moduleIndex;
    private int settingIndex;
    private int modeIndex;
    private boolean binding;
    private boolean upDown;
    private boolean downDown;
    private boolean leftDown;
    private boolean rightDown;
    private boolean enterDown;

    public TabGui() {
        super("TabGui", false);
        rebuildCategories();
    }

    private void rebuildCategories() {
        categories.clear();
        for (String name : Arrays.asList("Combat", "Movement", "Render", "Player", "Misc", "Legit")) {
            categories.put(name, new ArrayList<Module>());
        }
        if (Leader.moduleManager == null) {
            return;
        }
        for (Module module : Leader.moduleManager.modules.values()) {
            String packageName = module.getClass().getPackage().getName().toLowerCase(Locale.ROOT);
            for (String category : categories.keySet()) {
                if (packageName.endsWith("." + category.toLowerCase(Locale.ROOT))) {
                    categories.get(category).add(module);
                    break;
                }
            }
        }
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        if (!isEnabled() || !binding) {
            return;
        }
        if (event.getKey() == Keyboard.KEY_ESCAPE) {
            binding = false;
        } else if (event.getKey() != Keyboard.KEY_RETURN && event.getKey() != Keyboard.KEY_NUMPADENTER) {
            Module module = getSelectedModule();
            if (module != null) {
                module.setKey(event.getKey());
            }
            binding = false;
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isEnabled()) {
            return;
        }
        rebuildCategories();
        clampSelections();
        if (MC.currentScreen == null && !binding) {
            handleKeyboard();
        }

        FontRenderer font = MC.fontRendererObj;
        float x = 4.0F;
        float[] selectedRows = {categoryIndex, moduleIndex, settingIndex + 1, modeIndex};
        for (int column = 0; column < 4; column++) {
            List<Row> rows = getRows(column);
            boolean visible = column == 0 || level >= column;
            int selected = (int) selectedRows[column];
            drawColumn(font, x, WIDTHS[column], rows, selected, visible);
            x += WIDTHS[column] + GAP;
        }
    }

    private void handleKeyboard() {
        boolean up = Keyboard.isKeyDown(Keyboard.KEY_UP);
        boolean down = Keyboard.isKeyDown(Keyboard.KEY_DOWN);
        boolean left = Keyboard.isKeyDown(Keyboard.KEY_LEFT);
        boolean right = Keyboard.isKeyDown(Keyboard.KEY_RIGHT);
        boolean enter = Keyboard.isKeyDown(Keyboard.KEY_RETURN) || Keyboard.isKeyDown(Keyboard.KEY_NUMPADENTER);
        if (up && !upDown) moveSelection(-1);
        if (down && !downDown) moveSelection(1);
        if (left && !leftDown) level = Math.max(0, level - 1);
        if (right && !rightDown) openOrAdjust(1);
        if (enter && !enterDown) pressEnter();
        upDown = up;
        downDown = down;
        leftDown = left;
        rightDown = right;
        enterDown = enter;
    }

    private void moveSelection(int direction) {
        if (level == 0) {
            categoryIndex = wrap(categoryIndex + direction, categories.size());
            moduleIndex = settingIndex = modeIndex = 0;
        } else if (level == 1) {
            moduleIndex = wrap(moduleIndex + direction, getModules().size());
            settingIndex = modeIndex = 0;
        } else if (level == 2) {
            settingIndex = wrap(settingIndex + direction, getSettings().size() + 1);
            syncModeIndex();
        } else {
            modeIndex = wrap(modeIndex + direction, getMode() == null ? 0 : getMode().getModes().length);
        }
    }

    private void openOrAdjust(int direction) {
        if (level == 0) {
            level = 1;
        } else if (level == 1 && !getSettings().isEmpty()) {
            level = 2;
        } else if (level == 2) {
            Property<?> property = getSelectedProperty();
            if (property instanceof ModeProperty) {
                level = 3;
                syncModeIndex();
            } else {
                adjust(property, direction);
            }
        }
    }

    private void pressEnter() {
        if (level == 1) {
            Module module = getSelectedModule();
            if (module != null) module.toggle();
        } else if (level == 2) {
            if (settingIndex == 0) {
                binding = true;
            } else {
                Property<?> property = getSelectedProperty();
                if (property instanceof ModeProperty) {
                    level = 3;
                    syncModeIndex();
                } else {
                    adjust(property, 1);
                }
            }
        } else if (level == 3 && getMode() != null) {
            getMode().setValue(modeIndex);
        }
    }

    private void adjust(Property<?> property, int direction) {
        if (property == null || !property.isVisible()) return;
        if (property instanceof BooleanProperty) {
            BooleanProperty value = (BooleanProperty) property;
            value.setValue(!value.getValue());
        } else if (property instanceof IntProperty) {
            IntProperty value = (IntProperty) property;
            value.setValue(clamp(value.getValue() + direction, value.getMinimum(), value.getMaximum()));
        } else if (property instanceof PercentProperty) {
            PercentProperty value = (PercentProperty) property;
            value.setValue(clamp(value.getValue() + direction, value.getMinimum(), value.getMaximum()));
        } else if (property instanceof FloatProperty) {
            FloatProperty value = (FloatProperty) property;
            float next = clamp(value.getValue() + direction * 0.1F, value.getMinimum(), value.getMaximum());
            value.setValue(Math.round(next * 10.0F) / 10.0F);
        }
    }

    private List<Row> getRows(int column) {
        List<Row> rows = new ArrayList<>();
        if (column == 0) {
            for (String category : categories.keySet()) rows.add(new Row(category, GRAY));
        } else if (column == 1) {
            for (Module module : getModules()) rows.add(new Row(module.getName(), module.isEnabled() ? Color.WHITE.getRGB() : GRAY));
        } else if (column == 2) {
            Module module = getSelectedModule();
            if (module != null) {
                rows.add(new Row("Keybind", KeyBindUtil.getKeyName(module.getKey()), GRAY));
                for (Property<?> property : getSettings()) {
                    rows.add(new Row(label(property.getName()), property instanceof BooleanProperty && !((BooleanProperty) property).getValue() ? GRAY : Color.WHITE.getRGB(), property instanceof ModeProperty));
                }
            }
        } else {
            ModeProperty mode = getMode();
            if (mode != null) for (String value : mode.getModes()) rows.add(new Row(label(value), GRAY));
        }
        return rows;
    }

    private void drawColumn(FontRenderer font, float x, int width, List<Row> rows, int selected, boolean visible) {
        if (!visible) return;
        int count = Math.min(MAX_ROWS, Math.max(1, rows.size()));
        int start = scrollStart(selected, rows.size());
        float y = 15.0F;
        GlStateManager.enableBlend();
        RenderUtil.drawRect(x, y, x + width, y + count * ROW_HEIGHT, BACKGROUND);
        int row = selected - start;
        if (row >= 0 && row < count) RenderUtil.drawRect(x, y + row * ROW_HEIGHT, x + width, y + (row + 1) * ROW_HEIGHT, new Color(70, 130, 220, 210).getRGB());
        GlStateManager.disableBlend();
        GlStateManager.disableDepth();
        for (int i = 0; i < count && start + i < rows.size(); i++) {
            Row item = rows.get(start + i);
            int color = start + i == selected ? Color.WHITE.getRGB() : item.color;
            String text = item.name;
            if (font.getStringWidth(text) > width - 6) text = font.trimStringToWidth(text, width - 9) + "...";
            font.drawString(text, x + 3, y + i * ROW_HEIGHT + 2, color, false);
            if (!item.value.isEmpty()) {
                String value = font.trimStringToWidth(item.value, width / 2);
                font.drawString(value, x + width - font.getStringWidth(value) - 3, y + i * ROW_HEIGHT + 2, Color.WHITE.getRGB(), false);
            }
        }
        GlStateManager.enableDepth();
    }

    private List<Module> getModules() {
        if (categories.isEmpty()) return new ArrayList<>();
        List<String> names = new ArrayList<>(categories.keySet());
        return categories.get(names.get(categoryIndex));
    }

    private Module getSelectedModule() {
        List<Module> modules = getModules();
        return modules.isEmpty() ? null : modules.get(clamp(moduleIndex, 0, modules.size() - 1));
    }

    private List<Property<?>> getSettings() {
        Module module = getSelectedModule();
        if (module == null || Leader.propertyManager == null) return new ArrayList<>();
        List<Property<?>> result = new ArrayList<>();
        List<Property<?>> properties = Leader.propertyManager.properties.get(module.getClass());
        if (properties != null) for (Property<?> property : properties) if (property.isVisible()) result.add(property);
        return result;
    }

    private Property<?> getSelectedProperty() {
        List<Property<?>> settings = getSettings();
        return settingIndex <= 0 || settingIndex > settings.size() ? null : settings.get(settingIndex - 1);
    }

    private ModeProperty getMode() {
        Property<?> property = getSelectedProperty();
        return property instanceof ModeProperty ? (ModeProperty) property : null;
    }

    private void syncModeIndex() {
        ModeProperty mode = getMode();
        modeIndex = mode == null ? 0 : mode.getValue();
    }

    private void clampSelections() {
        categoryIndex = clamp(categoryIndex, 0, Math.max(0, categories.size() - 1));
        moduleIndex = clamp(moduleIndex, 0, Math.max(0, getModules().size() - 1));
        settingIndex = clamp(settingIndex, 0, getSettings().size());
        ModeProperty mode = getMode();
        modeIndex = clamp(modeIndex, 0, mode == null ? 0 : Math.max(0, mode.getModes().length - 1));
        if (level > 1 && getSettings().isEmpty()) level = 1;
        if (level > 2 && mode == null) level = 2;
    }

    private int scrollStart(int selected, int size) {
        return size <= MAX_ROWS ? 0 : clamp(selected - MAX_ROWS / 2, 0, size - MAX_ROWS);
    }

    private int wrap(int value, int size) {
        if (size <= 0) return 0;
        return (value % size + size) % size;
    }

    private int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }

    private String label(String value) {
        String normalized = value.replace('_', ' ').replace('-', ' ').toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder();
        for (String part : normalized.split(" ")) {
            if (part.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private static final class Row {
        private final String name;
        private final String value;
        private final int color;

        private Row(String name, int color) { this(name, "", color); }
        private Row(String name, String value, int color) { this.name = name; this.value = value; this.color = color; }
        private Row(String name, int color, boolean ignored) { this(name, "", color); }
    }
}
