package leader.config;

import com.google.gson.*;
import leader.Leader;
import leader.mixin.IAccessorMinecraft;
import leader.module.Module;
import leader.util.ChatUtil;
import leader.property.Property;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.util.ArrayList;

public class Config {
    public static Minecraft mc = Minecraft.getMinecraft();
    public static Gson gson = new GsonBuilder().setPrettyPrinting().create();
    public String name;
    public File file;

    public static String lastConfig;

    public Config(String name, boolean newConfig) {
        this.name = name;
        lastConfig = name;
        if (name.equals("!") || name.equals("default")) {
            this.name = "default";
        }
        this.file = new File("./config/Leader/", String.format("%s.json", this.name));
        try {
            file.getParentFile().mkdirs();
            if (newConfig) {
                ((IAccessorMinecraft) mc).getLogger().info(String.format("Created: %s", this.file.getName()));
            }
        } catch (Exception e) {
            ((IAccessorMinecraft) mc).getLogger().error(e.getMessage());
        }
    }

    public void load() {
        try {

            if (!file.exists()) {
                ChatUtil.sendFormatted(String.format("%sConfig file not found (&c&o%s&r). Creating default config...&r", Leader.clientName, file.getName()));
                save();
                return;
            }

            JsonElement parsed = new JsonParser().parse(new BufferedReader(new FileReader(file)));
            if (parsed == null || !parsed.isJsonObject()) {
                ChatUtil.sendFormatted(String.format("%sInvalid config format (&c&o%s&r)&r", Leader.clientName, file.getName()));
                return;
            }

            JsonObject jsonObject = parsed.getAsJsonObject();
            migrateLegacyProperties(jsonObject);
            for (Module module : Leader.moduleManager.modules.values()) {
                JsonElement moduleObj = jsonObject.get(module.getName());
                if (moduleObj != null && moduleObj.isJsonObject()) {
                    JsonObject object = moduleObj.getAsJsonObject();

                    ArrayList<Property<?>> list = Leader.propertyManager.properties.get(module.getClass());
                    if (list != null) {
                        for (Property<?> property : list) {
                            if (object.has(property.getName())) {
                                try {
                                    property.read(object);
                                } catch (Exception e) {
                                    ((IAccessorMinecraft) mc).getLogger().warn(String.format("Failed to load property %s for module %s", property.getName(), module.getName()));
                                }
                            }
                        }
                    }

                    if (object.has("toggled")) {
                        JsonElement toggled = object.get("toggled");
                        if (toggled != null && toggled.isJsonPrimitive()) {
                            module.setEnabled(toggled.getAsBoolean());
                        }
                    }

                    if (object.has("key")) {
                        JsonElement key = object.get("key");
                        if (key != null && key.isJsonPrimitive()) {
                            module.setKey(key.getAsInt());
                        }
                    }

                    if (object.has("hidden")) {
                        JsonElement hidden = object.get("hidden");
                        if (hidden != null && hidden.isJsonPrimitive()) {
                            module.setHidden(hidden.getAsBoolean());
                        }
                    }
                }
            }
            ChatUtil.sendFormatted(String.format("%sConfig has been loaded (&a&o%s&r)&r", Leader.clientName, file.getName()));
        } catch (FileNotFoundException e) {
            ChatUtil.sendFormatted(String.format("%sConfig file not found (&c&o%s&r)&r", Leader.clientName, file.getName()));
        } catch (JsonSyntaxException e) {
            ChatUtil.sendFormatted(String.format("%sConfig has invalid JSON syntax (&c&o%s&r)&r", Leader.clientName, file.getName()));
            ((IAccessorMinecraft) mc).getLogger().error("JSON Syntax Error: " + e.getMessage());
        } catch (Exception e) {
            ((IAccessorMinecraft) mc).getLogger().error("Error loading config: " + e.getMessage());
            ChatUtil.sendFormatted(String.format("%sConfig couldn't be loaded (&c&o%s&r)&r", Leader.clientName, file.getName()));
        }
    }

    static void migrateLegacyProperties(JsonObject root) {
        JsonObject backTrack = moduleObject(root, "BackTrack");
        if (backTrack != null && !backTrack.has("Mode")
                && (backTrack.has("TrackMS") || backTrack.has("Max Track Range")
                || backTrack.has("Ray Trance") || backTrack.has("Smart"))) {
            backTrack.addProperty("Mode", "Legacy");
        }

        JsonObject scaffold = moduleObject(root, "Scaffold");
        if (scaffold != null && scaffold.has("Legit Place Time") && !scaffold.has("Legacy Legit")) {
            scaffold.addProperty("Legacy Legit", true);
        }

        JsonObject aura = moduleObject(root, "KillAura");
        if (aura != null && aura.has("LagMode") && !aura.has("LagClass")) {
            String oldMode = aura.get("LagMode").getAsString();
            switch (oldMode) {
                case "2Tick", "3Tick", "4Tick", "5Tick" -> {
                    aura.addProperty("LagClass", "Tick");
                    if (!aura.has("TickMode")) aura.addProperty("TickMode", oldMode);
                }
                case "3Tick + 2Tick" -> aura.addProperty("LagClass", "Combo");
                case "Swap" -> {
                    aura.addProperty("LagClass", "Swap");
                    if (!aura.has("SwapMode")) aura.addProperty("SwapMode", "Legacy Swap");
                }
            }
        }

        JsonObject speed = moduleObject(root, "Speed");
        if (speed != null && !speed.has("mode")) speed.addProperty("mode", "Normal");

        JsonObject velocity = moduleObject(root, "Velocity");
        if (velocity != null && !velocity.has("Legacy Behavior") && !velocity.has("AirPush")
                && (velocity.has("Ground Delay") || velocity.has("Delay Till On Ground")
                || velocity.has("Force Delay Rising To Falling"))) {
            velocity.addProperty("Legacy Behavior", true);
        }

        JsonObject watermark = moduleObject(root, "Watermark");
        if (watermark != null && !watermark.has("Legacy Style")) {
            watermark.addProperty("Legacy Style", true);
        }

        JsonObject targetHud = moduleObject(root, "TargetHUD");
        if (targetHud != null && !targetHud.has("scoreboard-health")) {
            targetHud.addProperty("scoreboard-health", false);
        }

        for (String name : new String[]{"HUD", "Notification", "Potion", "TargetHUD"}) {
            JsonObject object = moduleObject(root, name);
            if (object != null && object.has("blur") && !object.has("Legacy Blur")) {
                object.addProperty("Legacy Blur", true);
            }
        }
    }

    private static JsonObject moduleObject(JsonObject root, String name) {
        JsonElement object = root.get(name);
        return object != null && object.isJsonObject() ? object.getAsJsonObject() : null;
    }

    public void save() {
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            JsonObject object = new JsonObject();
            for (Module module : Leader.moduleManager.modules.values()) {
                JsonObject moduleObject = new JsonObject();
                moduleObject.addProperty("toggled", module.isEnabled());
                moduleObject.addProperty("key", module.getKey());
                moduleObject.addProperty("hidden", module.isHidden());

                ArrayList<Property<?>> list = Leader.propertyManager.properties.get(module.getClass());
                if (list != null) {
                    for (Property<?> property : list) {
                        try {
                            property.write(moduleObject);
                        } catch (Exception e) {
                            ((IAccessorMinecraft) mc).getLogger().warn(String.format("Failed to save property %s for module %s", property.getName(), module.getName()));
                        }
                    }
                }
                object.add(module.getName(), moduleObject);
            }

            PrintWriter printWriter = new PrintWriter(new FileWriter(file));
            printWriter.println(gson.toJson(object));
            printWriter.close();
            ChatUtil.sendFormatted(String.format("%sConfig has been saved (&a&o%s&r)&r", Leader.clientName, file.getName()));
        } catch (IOException e) {
            ((IAccessorMinecraft) mc).getLogger().error("Error saving config: " + e.getMessage());
            ChatUtil.sendFormatted(String.format("%sConfig couldn't be saved (&c&o%s&r)&r", Leader.clientName, file.getName()));
        }
    }
}
