package pitheguy.schemconvert.nbt.tags;

import pitheguy.schemconvert.nbt.NbtException;
import pitheguy.schemconvert.nbt.NbtUtil;

import java.io.*;
import java.util.*;

public class CompoundTag implements Tag {
    Map<String, Tag> tags;

    public CompoundTag() {
        tags = new LinkedHashMap<>();
    }

    public Set<String> keySet() {
        return tags.keySet();
    }

    public void put(String key, Tag tag) {
        tags.put(key, tag);
    }

    public boolean contains(String key, byte type) {
        return tags.containsKey(key) && tags.get(key).getType() == type;
    }

    public Tag get(String key) {
        return tags.get(key);
    }

    public Tag get(String key, byte type) {
        Tag tag = tags.get(key);
        if (tag == null)
            throw new NbtException("No such tag: " + key);
        if (tag.getType() != type)
            throw new NbtException("Type mismatch for tag " + key + ": " + tag.getType() + " != " + type);
        return tag;
    }

    public byte getByte(String key) {
        return ((ByteTag) get(key, Tag.TAG_BYTE)).value();
    }

    public short getShort(String key) {
        return ((ShortTag) get(key, Tag.TAG_SHORT)).value();
    }

    public int getInt(String key) {
        return ((IntTag) get(key, Tag.TAG_INT)).value();
    }

    public long getLong(String key) {
        return ((LongTag) get(key, Tag.TAG_LONG)).value();
    }

    public float getFloat(String key) {
        return ((FloatTag) get(key, Tag.TAG_FLOAT)).value();
    }

    public double getDouble(String key) {
        return ((DoubleTag) get(key, Tag.TAG_DOUBLE)).value();
    }

    public String getString(String key) {
        return ((StringTag) get(key, Tag.TAG_STRING)).value();
    }

    public byte[] getByteArray(String key) {
        return ((ByteArrayTag) get(key, Tag.TAG_BYTE_ARRAY)).values();
    }

    public int[] getIntArray(String key) {
        return ((IntArrayTag) get(key, Tag.TAG_INT_ARRAY)).values();
    }

    public long[] getLongArray(String key) {
        return ((LongArrayTag) get(key, Tag.TAG_LONG_ARRAY)).values();
    }

    public ListTag getList(String key) {
        return ((ListTag) get(key, Tag.TAG_LIST));
    }

    public CompoundTag getCompound(String key) {
        return ((CompoundTag) get(key, Tag.TAG_COMPOUND));
    }

    public void remove(String key) {
        tags.remove(key);
    }

    @Override
    public void writeContents(DataOutputStream out) throws IOException {
        for (Map.Entry<String, Tag> entry : tags.entrySet()) {
            String key = entry.getKey();
            Tag tag = entry.getValue();
            out.writeByte(tag.getType());
            out.writeUTF(key);
            tag.writeContents(out);
        }
        out.writeByte(Tag.TAG_END);
    }

    public static CompoundTag readContents(DataInputStream in) throws IOException {
        CompoundTag result = new CompoundTag();
        byte type;
        while ((type = in.readByte()) != Tag.TAG_END) {
            String key = in.readUTF();
            Tag tag = NbtUtil.readByType(type, in);
            result.put(key, tag);
        }
        return result;
    }

    @Override
    public byte getType() {
        return Tag.TAG_COMPOUND;
    }
}
