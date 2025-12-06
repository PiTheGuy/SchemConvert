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
        if (type != Tag.TAG_COMPOUND)
            throw new NbtException("File isn't in NBT format");
        in.readUTF();
        return (CompoundTag) readByType(type, in);
    }

    public static CompoundTag read(DataInputStream in) throws IOException {
        byte type = in.readByte();
        if (type != Tag.TAG_COMPOUND)
            throw new NbtException("Not in NBT format");
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
            tag.put("Name", new StringTag(name));
            String propertiesStr = block.substring(block.indexOf("[") + 1, block.indexOf("]"));
            CompoundTag properties = new CompoundTag();
            for (String property : propertiesStr.split(",")) {
                String[] parts = property.split("=");
                String key = parts[0];
                String value = parts[1];
                properties.put(key, new StringTag(value));
            }
            tag.put("Properties", properties);
        } else
            tag.put("Name", new StringTag(block));
        return tag;
    }

    public static String convertToBlockString(CompoundTag entry) {
        StringBuilder sb = new StringBuilder();
        sb.append(entry.getString("Name"));
        if (entry.contains("Properties", Tag.TAG_COMPOUND)) {
            CompoundTag properties = entry.getCompound("Properties");
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

    public static int getInt(Tag tag) {
        if (tag instanceof IntTag intTag)
            return intTag.value();
        if (tag instanceof ShortTag shortTag)
            return shortTag.value();
        if (tag instanceof ByteTag byteTag)
            return byteTag.value();
        if (tag instanceof DoubleTag doubleTag)
            return (int) doubleTag.value();
        if (tag instanceof FloatTag floatTag)
            return (int) floatTag.value();
        if (tag instanceof LongTag longTag)
            return (int) longTag.value();
        throw new NbtException("Tag " + tag.getClass().getSimpleName() + " is not a number");
    }

    public static double getDouble(Tag tag) {
        if (tag instanceof DoubleTag doubleTag)
            return doubleTag.value();
        if (tag instanceof FloatTag floatTag)
            return floatTag.value();
        if (tag instanceof IntTag intTag)
            return intTag.value();
        if (tag instanceof LongTag longTag)
            return longTag.value();
        if (tag instanceof ShortTag shortTag)
            return shortTag.value();
        if (tag instanceof ByteTag byteTag)
            return byteTag.value();
        throw new NbtException("Tag " + tag.getClass().getSimpleName() + " is not a number");
    }

    public static CompoundTag getCompound(Tag tag) {
        if (tag instanceof CompoundTag compoundTag)
            return compoundTag;
        throw new NbtException("Tag " + tag.getClass().getSimpleName() + " is not a compound tag");
    }
}
