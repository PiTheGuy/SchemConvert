package pitheguy.schemconvert.nbt;

import pitheguy.schemconvert.nbt.tags.*;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class NbtUtil {
    public static Tag readByType(byte type, DataInputStream in) throws IOException {
        return switch (type) {
            case Tag.TAG_END -> EndTag.INSTANCE;
            case Tag.TAG_BYTE -> ByteTag.readContents(in);
            case Tag.TAG_SHORT -> ShortTag.readContents(in);
            case Tag.TAG_INT -> IntTag.readContents(in);
            case Tag.TAG_LONG -> LongTag.readContents(in);
            case Tag.TAG_FLOAT -> FloatTag.readContents(in);
            case Tag.TAG_DOUBLE -> DoubleTag.readContents(in);
            case Tag.TAG_BYTE_ARRAY -> ByteArrayTag.readContents(in);
            case Tag.TAG_STRING -> StringTag.readContents(in);
            case Tag.TAG_LIST -> ListTag.readContents(in);
            case Tag.TAG_COMPOUND -> CompoundTag.readContents(in);
            case Tag.TAG_INT_ARRAY -> IntArrayTag.readContents(in);
            case Tag.TAG_LONG_ARRAY -> LongArrayTag.readContents(in);
            default -> throw new NbtException("Unknown type: " + type);
        };
    }

    public static CompoundTag read(File file) throws IOException {
        DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(file)));
        byte type = in.readByte();
        if (type != Tag.TAG_COMPOUND) throw new NbtException("File isn't in NBT format");
        in.readUTF();
        return (CompoundTag) readByType(type, in);
    }

    public static CompoundTag read(DataInputStream in) throws IOException {
        byte type = in.readByte();
        if (type != Tag.TAG_COMPOUND) throw new NbtException("Not in NBT format");
        in.readUTF();
        return (CompoundTag) readByType(type, in);
    }

    public static void write(Tag tag, File file) throws IOException {
        try (GZIPOutputStream gzip = new GZIPOutputStream(new FileOutputStream(file));
             DataOutputStream out = new DataOutputStream(gzip)) {
            out.write(tag.getType());
            out.writeUTF("");
            tag.writeContents(out);
            out.flush();
            gzip.finish();
        }
    }

    public static void write(Tag tag, DataOutputStream out) throws IOException {
        out.write(tag.getType());
        out.writeUTF("");
        tag.writeContents(out);
    }

    public static CompoundTag convertFromBlockString(String block) {
        CompoundTag tag = new CompoundTag();
        if (block.contains("[")) {
            String name = block.substring(0, block.indexOf("["));

            tag.put("id", new StringTag(name));   // For >=26.3
            tag.put("Name", new StringTag(name)); // For <=26.2

            String propertiesStr = block.substring(block.indexOf("[") + 1, block.indexOf("]"));
            CompoundTag properties = new CompoundTag();
            for (String property : propertiesStr.split(",")) {
                String[] parts = property.split("=");
                String key = parts[0];
                String value = parts[1];
                properties.put(key, new StringTag(value));
            }
            tag.put("properties", properties);   // For >=26.3
            tag.put("Properties", properties);   // For <=26.2

        } else {
            tag.put("id", new StringTag(block));   // For >=26.3
            tag.put("Name", new StringTag(block)); // For <=26.2
        }
        return tag;
    }

    public static String convertToBlockString(CompoundTag entry) {
        StringBuilder sb = new StringBuilder();

        String name = entry.contains("id", Tag.TAG_STRING) ? entry.getString("id") : entry.getString("Name");
        sb.append(name);

        CompoundTag properties = null;
        if (entry.contains("properties", Tag.TAG_COMPOUND)) {
            properties = entry.getCompound("properties");
        } else if (entry.contains("Properties", Tag.TAG_COMPOUND)) {
            properties = entry.getCompound("Properties");
        }

        if (properties != null) {
            sb.append("[");
            for (String key : properties.keySet()) {
                sb.append(key);
                sb.append("=");
                sb.append(properties.getString(key));
                sb.append(",");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.append("]");
        }
        return sb.toString();
    }
}
