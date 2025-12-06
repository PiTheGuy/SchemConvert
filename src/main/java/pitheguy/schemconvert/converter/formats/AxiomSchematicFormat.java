package pitheguy.schemconvert.converter.formats;

import pitheguy.schemconvert.converter.Schematic;
import pitheguy.schemconvert.converter.SchematicParseException;
import pitheguy.schemconvert.nbt.NbtUtil;
import pitheguy.schemconvert.nbt.tags.*;
import pitheguy.schemconvert.util.Util;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class AxiomSchematicFormat implements SchematicFormat {
    private static final int MAGIC = 0x0AE5BB36;

    @Override
    public Schematic read(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != MAGIC)
                throw new SchematicParseException("Incorrect header");
            int headerTagSize = in.readInt();
            in.readNBytes(headerTagSize);
            int thumbnailLength = in.readInt();
            byte[] thumbnail = in.readNBytes(thumbnailLength);
            int blockDataLength = in.readInt();
            byte[] blockData = in.readNBytes(blockDataLength);
            DataInputStream blockDataStream = new DataInputStream(
                    new GZIPInputStream(new ByteArrayInputStream(blockData)));
            CompoundTag blockDataTag = NbtUtil.read(blockDataStream);
            blockDataStream.close();

            ListTag blockRegions = blockDataTag.getList("BlockRegion");
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (Tag tag : blockRegions) {
                CompoundTag region = (CompoundTag) tag;
                minX = Math.min(minX, region.getInt("X"));
                minY = Math.min(minY, region.getInt("Y"));
                minZ = Math.min(minZ, region.getInt("Z"));
                maxX = Math.max(maxX, region.getInt("X"));
                maxY = Math.max(maxY, region.getInt("Y"));
                maxZ = Math.max(maxZ, region.getInt("Z"));
            }
            int[] size = { (maxX - minX + 1) * 16, (maxY - minY + 1) * 16, (maxZ - minZ + 1) * 16 };
            int dataVersion = blockDataTag.contains("DataVersion", Tag.TAG_INT) ? blockDataTag.getInt("DataVersion")
                    : -1;
            Schematic.Builder builder = new Schematic.Builder(file, dataVersion, size).setThumbnail(thumbnail);
            for (Tag tag : blockRegions) {
                CompoundTag region = (CompoundTag) tag;
                CompoundTag blockStatesTag = region.getCompound("BlockStates");
                ListTag paletteTag = blockStatesTag.getList("palette");
                String[] palette = new String[paletteTag.size()];
                for (int i = 0; i < palette.length; i++)
                    palette[i] = NbtUtil.convertToBlockString((CompoundTag) paletteTag.get(i));
                long[] data = palette.length == 1 ? new long[256] : blockStatesTag.getLongArray("data");
                int regionX = region.getInt("X") - minX;
                int regionY = region.getInt("Y") - minY;
                int regionZ = region.getInt("Z") - minZ;
                int[] blockStateData = new int[4096];
                int bitsPerValue = Math.max(4, Integer.SIZE - Integer.numberOfLeadingZeros(palette.length - 1));
                int valuesPerLong = Long.SIZE / bitsPerValue;
                int index = 0;
                int mask = (1 << bitsPerValue) - 1;
                for (long num : data) {
                    for (int i = 0; i < valuesPerLong && index < 4096; i++) {
                        blockStateData[index++] = (int) (num & mask);
                        num >>>= bitsPerValue;
                    }
                }

                int i = 0;
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            builder.setBlockAt(regionX * 16 + x, regionY * 16 + y, regionZ * 16 + z,
                                    palette[blockStateData[i++]]);
                        }
                    }
                }
            }
            if (blockDataTag.contains("BlockEntities", Tag.TAG_LIST)) {
                ListTag blockEntities = blockDataTag.getList("BlockEntities");
                for (Tag tag : blockEntities) {
                    CompoundTag blockEntity = (CompoundTag) tag;
                    if (blockEntity.contains("X", Tag.TAG_INT))
                        builder.addBlockEntity(blockEntity.getInt("X"), blockEntity.getInt("Y"),
                                blockEntity.getInt("Z"), blockEntity);
                    else
                        builder.addBlockEntity(blockEntity.getInt("x"), blockEntity.getInt("y"),
                                blockEntity.getInt("z"), blockEntity);

                }
            }
            return builder.trim().build();
        }
    }

    @Override
    public void write(File file, Schematic schematic) throws IOException {
        DataOutputStream out = new DataOutputStream(new FileOutputStream(file));
        out.writeInt(MAGIC);
        writeHeader(out, schematic);
        writeThumbnail(out, schematic);
        writeBlockData(out, schematic);
        out.close();
    }

    private void writeHeader(DataOutputStream out, Schematic schematic) throws IOException {
        CompoundTag header = new CompoundTag();
        header.put("ThumbnailYaw", new FloatTag(0));
        header.put("ThumbnailPitch", new FloatTag(45));
        header.put("ContainsAir", new ByteTag((byte) 0));
        header.put("LockedThumbnail", new ByteTag((byte) 0));
        header.put("Version", new LongTag(1));
        header.put("Author", new StringTag("SchemConvert"));
        header.put("Name", new StringTag(Util.stripExtension(schematic.getSourceFile().getName())));
        header.put("Tags", new ListTag(Tag.TAG_END));
        header.put("BlockCount", new IntTag(schematic.countNonEmptyBlocks()));
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        NbtUtil.write(header, new DataOutputStream(stream));
        out.writeInt(stream.size());
        out.write(stream.toByteArray());
    }

    private void writeThumbnail(DataOutputStream out, Schematic schematic) throws IOException {
        if (schematic.getThumbnail() != null && schematic.getThumbnail().length > 0) {
            out.writeInt(schematic.getThumbnail().length);
            out.write(schematic.getThumbnail());
        } else {
            try (InputStream stream = AxiomSchematicFormat.class.getResourceAsStream("/icon.png")) {
                if (stream == null)
                    throw new IOException("Icon not found");
                out.writeInt(stream.available());
                out.write(stream.readAllBytes());
            }
        }
    }

    private void writeBlockData(DataOutputStream out, Schematic schematic) throws IOException {
        CompoundTag blockData = new CompoundTag();
        ListTag blockRegions = new ListTag(Tag.TAG_COMPOUND);
        int[] size = schematic.getSize();
        int[] regionSize = new int[] {
                (int) Math.ceil(size[0] / 16.0),
                (int) Math.ceil(size[1] / 16.0),
                (int) Math.ceil(size[2] / 16.0),
        };
        for (int regionX = 0; regionX < regionSize[0]; regionX++) {
            for (int regionY = 0; regionY < regionSize[1]; regionY++) {
                for (int regionZ = 0; regionZ < regionSize[2]; regionZ++) {
                    CompoundTag region = new CompoundTag();
                    region.put("X", new IntTag(regionX));
                    region.put("Y", new IntTag(regionY));
                    region.put("Z", new IntTag(regionZ));
                    CompoundTag blockStates = new CompoundTag();
                    List<String> palette = collectPalette(schematic, regionX, regionY, regionZ);

                    int[] blockStateData = new int[4096];
                    int index = 0;
                    for (int dy = 0; dy < 16; dy++) {
                        for (int dz = 0; dz < 16; dz++) {
                            for (int dx = 0; dx < 16; dx++) {
                                String block;
                                try {
                                    block = schematic.getBlock(regionX * 16 + dx, regionY * 16 + dy, regionZ * 16 + dz);
                                    if (block == null)
                                        block = "minecraft:structure_void";
                                    if (block.startsWith("minecraft:oak_log")) {
                                        new Object();
                                    }
                                } catch (IndexOutOfBoundsException e) {
                                    if (!palette.contains("minecraft:structure_void"))
                                        palette.add("minecraft:structure_void");
                                    block = "minecraft:structure_void";
                                }
                                blockStateData[index++] = palette.indexOf(block);
                            }
                        }
                    }
                    ListTag paletteTag = new ListTag(Tag.TAG_COMPOUND);
                    palette.stream().map(NbtUtil::convertFromBlockString).forEach(paletteTag::add);
                    blockStates.put("palette", paletteTag);
                    int bitsPerValue = Math.max(4, Integer.SIZE - Integer.numberOfLeadingZeros(palette.size() - 1));
                    int valuesPerLong = Long.SIZE / bitsPerValue;
                    long[] data = new long[(int) Math.ceil(4096 / (double) valuesPerLong)];
                    int blockIndex = 0;
                    for (int i = 0; i < data.length; i++) {
                        long value = 0;
                        for (int j = 0; j < valuesPerLong && blockIndex < 4096; j++) {
                            // Pack each blockStateData value into the long at the correct bit offset.
                            value |= ((long) blockStateData[blockIndex++]) << (j * bitsPerValue);
                        }
                        data[i] = value;
                    }
                    blockStates.put("data", new LongArrayTag(data));
                    region.put("BlockStates", blockStates);
                    blockRegions.add(region);
                }
            }
        }
        blockData.put("BlockRegion", blockRegions);
        ListTag blockEntities = new ListTag(Tag.TAG_COMPOUND);
        schematic.getBlockEntities().forEach((pos, blockEntity) -> {
            blockEntity.put("x", new IntTag(pos.x()));
            blockEntity.put("y", new IntTag(pos.y()));
            blockEntity.put("z", new IntTag(pos.z()));
            blockEntities.add(blockEntity);
        });
        blockData.put("BlockEntities", blockEntities);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(stream);
        NbtUtil.write(blockData, new DataOutputStream(gzip));
        gzip.finish();
        out.writeInt(stream.size());
        out.write(stream.toByteArray());
    }

    private List<String> collectPalette(Schematic schematic, int regionX, int regionY, int regionZ) {
        Set<String> palette = new HashSet<>();
        for (int dx = 0; dx < 16; dx++) {
            for (int dy = 0; dy < 16; dy++) {
                for (int dz = 0; dz < 16; dz++) {
                    try {
                        String block = schematic.getBlock(regionX * 16 + dx, regionY * 16 + dy, regionZ * 16 + dz);
                        palette.add(block == null ? "minecraft:structure_void" : block);
                    } catch (IndexOutOfBoundsException e) {
                        palette.add("minecraft:structure_void");
                    }
                }
            }
        }
        return new ArrayList<>(palette);
    }

    @Override
    public String getExtension() {
        return ".bp";
    }
}
