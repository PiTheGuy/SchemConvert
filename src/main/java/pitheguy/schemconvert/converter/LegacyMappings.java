package pitheguy.schemconvert.converter;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class LegacyMappings {
    private static final Map<Integer, String> LEGACY_TO_MODERN = new HashMap<>();
    private static final Map<String, Integer> MODERN_TO_LEGACY = new HashMap<>();

    static {
        loadMappings();
    }

    private static void loadMappings() {
        try (InputStream stream = LegacyMappings.class.getResourceAsStream("/legacy_mappings.json")) {
            if (stream == null)
                throw new RuntimeException("Could not find legacy_mappings.json");
            try (Reader reader = new InputStreamReader(stream)) {
                Gson gson = new Gson();
                JsonObject root = gson.fromJson(reader, JsonObject.class);
                Map<String, String> blocks = gson.fromJson(root.get("blocks"), new TypeToken<Map<String, String>>() {
                }.getType());

                for (Map.Entry<String, String> entry : blocks.entrySet()) {
                    String key = entry.getKey(); // "id:data"
                    String value = entry.getValue(); // "minecraft:block"

                    String[] parts = key.split(":");
                    int id = Integer.parseInt(parts[0]);
                    int data = Integer.parseInt(parts[1]);
                    int packed = (id << 4) | (data & 0xF);

                    LEGACY_TO_MODERN.put(packed, value);
                    // Only map modern to legacy if not already present (prefer default data 0 if
                    // duplicates exist, though usually they are unique variants)
                    MODERN_TO_LEGACY.putIfAbsent(value, packed);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load legacy mappings", e);
        }
    }

    public static String getModernBlock(int id, int data) {
        int packed = (id << 4) | (data & 0xF);
        return LEGACY_TO_MODERN.getOrDefault(packed, "minecraft:air");
    }

    public static int getLegacyId(String modernBlock) {
        Integer packed = MODERN_TO_LEGACY.get(modernBlock);
        if (packed == null) {
            // Fallback or error? For now return 0 (air) but maybe we should warn?
            // Try stripping properties
            if (modernBlock.contains("[")) {
                String base = modernBlock.substring(0, modernBlock.indexOf("["));
                packed = MODERN_TO_LEGACY.get(base);
            }
        }
        return packed != null ? packed : 0;
    }

    public static int unpackId(int packed) {
        return packed >> 4;
    }

    public static int unpackData(int packed) {
        return packed & 0xF;
    }
}
