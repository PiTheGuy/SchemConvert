package pitheguy.schemconvert.converter.formats;

import pitheguy.schemconvert.converter.*;
import pitheguy.schemconvert.nbt.NbtUtil;
import pitheguy.schemconvert.nbt.tags.*;

import java.io.File;
import java.io.IOException;

public class NbtSchematicFormat implements SchematicFormat {

    @Override
    public Schematic read(File file) throws IOException {
        CompoundTag tag = NbtUtil.read(file);
        if (!tag.contains("size", Tag.TAG_LIST)) {
            throw new ConversionException(
                    "Invalid NBT Schematic format. Missing 'size' tag. Found keys: " + tag.keySet());
        }
        ListTag sizeTag = tag.getList("size");
        int[] size = new int[3];
        for (int i = 0; i < 3; i++)
            size[i] = NbtUtil.getInt(sizeTag.get(i));
        ListTag paletteTag = tag.getList("palette");
        String[] palette = new String[paletteTag.size()];
        for (int i = 0; i < paletteTag.size(); i++)
            palette[i] = NbtUtil.convertToBlockString(NbtUtil.getCompound(paletteTag.get(i)));
        Schematic.Builder builder = new Schematic.Builder(file, tag.getInt("DataVersion"), size[0], size[1], size[2]);
        ListTag blocksTag = tag.getList("blocks");
        for (Tag value : blocksTag) {
            CompoundTag entry = (CompoundTag) value;
            ListTag posTag = entry.getList("pos");
            int[] pos = new int[3];
            for (int i = 0; i < 3; i++)
                pos[i] = NbtUtil.getInt(posTag.get(i));
            int state = entry.getInt("state");
            builder.setBlockAt(pos[0], pos[1], pos[2], palette[state]);
            if (entry.contains("nbt", Tag.TAG_COMPOUND))
                builder.addBlockEntity(pos[0], pos[1], pos[2], entry.getCompound("nbt"));
        }
        ListTag entitiesTag = tag.getList("entities");
        for (Tag value : entitiesTag) {
            CompoundTag entityTag = (CompoundTag) value;
            ListTag posTag = entityTag.getList("pos");
            double[] pos = new double[3];
            for (int i = 0; i < 3; i++)
                pos[i] = NbtUtil.getDouble(posTag.get(i));
            CompoundTag nbt = entityTag.getCompound("nbt");
            builder.addEntity(nbt.getString("id"), pos[0], pos[1], pos[2], nbt);
        }
        return builder.build();
    }

    @Override
    public void write(File file, Schematic schematic) throws IOException {
        int[] size = schematic.getSize();
        if (size[0] > 48 || size[1] > 48 || size[2] > 48)
            throw new ConversionException(
                    "The NBT schematic format only supports schematics of up to 48x48x48 blocks.");
        CompoundTag tag = new CompoundTag();
        ListTag sizeTag = new ListTag(Tag.TAG_INT);
        for (int i : size)
            sizeTag.add(new IntTag(i));
        ListTag paletteTag = new ListTag(Tag.TAG_COMPOUND);
        for (String block : schematic.getPalette())
            paletteTag.add(NbtUtil.convertFromBlockString(block));
        ListTag blocksTag = new ListTag(Tag.TAG_COMPOUND);
        for (int x = 0; x < size[0]; x++) {
            for (int y = 0; y < size[1]; y++) {
                for (int z = 0; z < size[2]; z++) {
                    int state = schematic.getPaletteBlock(x, y, z);
                    if (state == -1)
                        continue;
                    ListTag posTag = new ListTag(Tag.TAG_INT);
                    for (int i : new int[] { x, y, z })
                        posTag.add(new IntTag(i));
                    CompoundTag entry = new CompoundTag();
                    entry.put("pos", posTag);
                    entry.put("state", new IntTag(state));
                    if (schematic.hasBlockEntityAt(x, y, z))
                        entry.put("nbt", schematic.getBlockEntityAt(x, y, z));
                    blocksTag.add(entry);
                }
            }
        }
        ListTag entitiesTag = new ListTag(Tag.TAG_COMPOUND);
        for (Entity entity : schematic.getEntities()) {
            CompoundTag entityTag = new CompoundTag();
            ListTag posTag = new ListTag(Tag.TAG_DOUBLE);
            posTag.add(new DoubleTag(entity.x()));
            posTag.add(new DoubleTag(entity.y()));
            posTag.add(new DoubleTag(entity.z()));
            entityTag.put("pos", posTag);
            ListTag blockPosTag = new ListTag(Tag.TAG_INT);
            blockPosTag.add(new IntTag((int) entity.x()));
            blockPosTag.add(new IntTag((int) entity.y()));
            blockPosTag.add(new IntTag((int) entity.z()));
            entityTag.put("blockPos", blockPosTag);
            CompoundTag nbt = entity.nbt();
            nbt.put("id", new StringTag(entity.id()));
            entityTag.put("nbt", nbt);
            entitiesTag.add(entityTag);
        }
        tag.put("entities", entitiesTag);
        tag.put("size", sizeTag);
        tag.put("blocks", blocksTag);
        tag.put("palette", paletteTag);
        tag.put("DataVersion", new IntTag(schematic.getDataVersion()));
        NbtUtil.write(tag, file);
    }

    @Override
    public String getExtension() {
        return ".nbt";
    }
}
