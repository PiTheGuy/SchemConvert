package pitheguy.schemconvert.converter.formats;

import pitheguy.schemconvert.converter.ConversionException;
import pitheguy.schemconvert.converter.LegacyMappings;
import pitheguy.schemconvert.converter.Schematic;
import pitheguy.schemconvert.nbt.NbtUtil;
import pitheguy.schemconvert.nbt.tags.*;

import java.io.File;
import java.io.IOException;

public class ClassicSchematicFormat implements SchematicFormat {

    @Override
    public Schematic read(File file) throws IOException {
        CompoundTag tag = NbtUtil.read(file);

        if (!tag.contains("Blocks", Tag.TAG_BYTE_ARRAY))
            throw new ConversionException("Invalid schematic file: missing Blocks");

        short width = tag.getShort("Width");
        short height = tag.getShort("Height");
        short length = tag.getShort("Length");

        byte[] blocks = tag.getByteArray("Blocks");
        byte[] data = tag.getByteArray("Data");

        if (blocks.length != width * height * length)
            throw new ConversionException("Block data size mismatch");

        Schematic.Builder builder = new Schematic.Builder(file, -1, width, height, length);

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int index = (y * length + z) * width + x;
                    int blockId = blocks[index] & 0xFF;
                    int blockData = data[index] & 0xFF;

                    String blockString = LegacyMappings.getModernBlock(blockId, blockData);
                    builder.setBlockAt(x, y, z, blockString);
                }
            }
        }

        if (tag.contains("TileEntities", Tag.TAG_LIST)) {
            ListTag tileEntities = tag.getList("TileEntities");
            for (Tag t : tileEntities) {
                CompoundTag te = (CompoundTag) t;
                builder.addBlockEntity(te.getInt("x"), te.getInt("y"), te.getInt("z"), te);
            }
        }

        if (tag.contains("Entities", Tag.TAG_LIST)) {
            ListTag entities = tag.getList("Entities");
            for (Tag t : entities) {
                CompoundTag entity = (CompoundTag) t;
                ListTag pos = entity.getList("Pos");
                double x = ((DoubleTag) pos.get(0)).value();
                double y = ((DoubleTag) pos.get(1)).value();
                double z = ((DoubleTag) pos.get(2)).value();
                builder.addEntity(entity.getString("id"), x, y, z, entity);
            }
        }

        return builder.build();
    }

    @Override
    public void write(File file, Schematic schematic) throws IOException {
        int[] size = schematic.getSize();
        short width = (short) size[0];
        short height = (short) size[1];
        short length = (short) size[2];

        byte[] blocks = new byte[width * height * length];
        byte[] data = new byte[width * height * length];

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    String block = schematic.getBlock(x, y, z);
                    int packed = LegacyMappings.getLegacyId(block);
                    int index = (y * length + z) * width + x;
                    blocks[index] = (byte) LegacyMappings.unpackId(packed);
                    data[index] = (byte) LegacyMappings.unpackData(packed);
                }
            }
        }

        CompoundTag tag = new CompoundTag();
        tag.put("Width", new ShortTag(width));
        tag.put("Height", new ShortTag(height));
        tag.put("Length", new ShortTag(length));
        tag.put("Materials", new StringTag("Alpha"));
        tag.put("Blocks", new ByteArrayTag(blocks));
        tag.put("Data", new ByteArrayTag(data));

        ListTag tileEntities = new ListTag(Tag.TAG_COMPOUND);
        schematic.getBlockEntities().forEach((pos, te) -> {
            CompoundTag copy = te;
            copy.put("x", new IntTag(pos.x()));
            copy.put("y", new IntTag(pos.y()));
            copy.put("z", new IntTag(pos.z()));
            tileEntities.add(copy);
        });
        tag.put("TileEntities", tileEntities);

        ListTag entities = new ListTag(Tag.TAG_COMPOUND);
        for (var entity : schematic.getEntities()) {
            CompoundTag eTag = entity.nbt();
            ListTag pos = new ListTag(Tag.TAG_DOUBLE);
            pos.add(new DoubleTag(entity.x()));
            pos.add(new DoubleTag(entity.y()));
            pos.add(new DoubleTag(entity.z()));
            eTag.put("Pos", pos);
            entities.add(eTag);
        }
        tag.put("Entities", entities);

        NbtUtil.write(tag, file);
    }

    @Override
    public String getExtension() {
        return ".schematic";
    }
}
