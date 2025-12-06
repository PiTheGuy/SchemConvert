package pitheguy.schemconvert.converter;

import pitheguy.schemconvert.converter.formats.*;
import pitheguy.schemconvert.nbt.tags.CompoundTag;
import pitheguy.schemconvert.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Schematic {
    private final String[][][] blocks;
    private final List<String> palette;
    private final Map<Pos, CompoundTag> blockEntities;
    private final List<Entity> entities;
    private final int dataVersion;
    private final File sourceFile;
    private final byte[] thumbnail;

    private Schematic(String[][][] blocks, List<String> palette, Map<Pos, CompoundTag> blockEntities,
            List<Entity> entities, int dataVersion, File sourceFile, byte[] thumbnail) {
        this.blocks = blocks;
        this.palette = palette;
        this.blockEntities = blockEntities;
        this.entities = entities;
        this.dataVersion = dataVersion;
        this.sourceFile = sourceFile;
        this.thumbnail = thumbnail;
    }

    public byte[] getThumbnail() {
        return thumbnail;
    }

    public Schematic withThumbnail(byte[] thumbnail) {
        return new Schematic(blocks, palette, blockEntities, entities, dataVersion, sourceFile, thumbnail);
    }

    public static Schematic read(File file) throws IOException {
        String extension = Util.getExtension(file.getName());
        return switch (extension) {
            case ".nbt" -> SchematicFormats.NBT.read(file);
            case ".schem" -> SchematicFormats.SCHEM.read(file);
            case ".litematic" -> SchematicFormats.LITEMATIC.read(file);
            case ".bp" -> SchematicFormats.AXIOM.read(file);
            case ".schematic" -> SchematicFormats.CLASSIC.read(file);
            default -> throw new IllegalArgumentException("Unsupported format: " + extension);
        };
    }

    public int[] getSize() {
        return new int[] { blocks.length, blocks[0].length, blocks[0][0].length };
    }

    public String getBlock(int x, int y, int z) {
        return blocks[x][y][z];
    }

    public int getPaletteBlock(int x, int y, int z) {
        String block = getBlock(x, y, z);
        if (block == null)
            return -1;
        return palette.indexOf(block);
    }

    public List<String> getPalette() {
        return palette;
    }

    public Map<Pos, CompoundTag> getBlockEntities() {
        return blockEntities;
    }

    public boolean hasBlockEntityAt(int x, int y, int z) {
        return blockEntities.containsKey(new Pos(x, y, z));
    }

    public CompoundTag getBlockEntityAt(int x, int y, int z) {
        return blockEntities.get(new Pos(x, y, z));
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public List<Entity> getEntities() {
        return entities;
    }

    public void write(File file, SchematicFormat format) throws IOException {
        format.write(file, this);
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public int countNonEmptyBlocks() {
        int count = 0;
        for (String[][] layer : blocks)
            for (String[] column : layer)
                for (String block : column)
                    if (!isEmpty(block))
                        count++;
        return count;
    }

    private static boolean isEmpty(String block) {
        return block == null || block.equals("minecraft:air") || block.equals("minecraft:structure_void");
    }

    public static class Builder {
        private String[][][] blocks;
        private final SequencedSet<String> palette;
        private final Map<Pos, CompoundTag> blockEntities;
        private final List<Entity> entities;
        private final File sourceFile;
        private final int dataVersion;

        public Builder(File sourceFile, int dataVersion, int xSize, int ySize, int zSize) {
            this.blocks = new String[xSize][ySize][zSize];
            this.palette = new LinkedHashSet<>();
            this.blockEntities = new HashMap<>();
            this.entities = new ArrayList<>();
            this.sourceFile = sourceFile;
            this.dataVersion = dataVersion;
        }

        public Builder(File sourceFile, int dataVersion, int[] size) {
            this(sourceFile, dataVersion, size[0], size[1], size[2]);
        }

        public void setBlockAt(int x, int y, int z, String block) {
            this.blocks[x][y][z] = block;
            palette.add(block);
        }

        public void addBlockEntity(int x, int y, int z, CompoundTag entity) {
            this.blockEntities.put(new Pos(x, y, z), entity);
        }

        public void addEntity(String id, double x, double y, double z, CompoundTag nbt) {
            entities.add(new Entity(id, x, y, z, nbt));
        }

        public Builder trim() {
            int minY = 0;
            loop: for (int y = 0; y < blocks[0].length; y++) {
                for (int x = 0; x < blocks.length; x++)
                    for (int z = 0; z < blocks[0][0].length; z++)
                        if (!isEmpty(blocks[x][y][z]))
                            break loop;
                minY++;
            }
            String[][][] newBlocks = new String[blocks.length][blocks[0].length - minY][blocks[0][0].length];
            for (int y = minY; y < blocks[0].length; y++)
                for (int x = 0; x < blocks.length; x++)
                    System.arraycopy(blocks[x][y], 0, newBlocks[x][y - minY], 0, blocks[0][0].length);
            blocks = newBlocks;
            return this;
        }

        private byte[] thumbnail;

        public Builder setThumbnail(byte[] thumbnail) {
            this.thumbnail = thumbnail;
            return this;
        }

        public Schematic build() {
            return new Schematic(blocks, palette.stream().toList(), blockEntities, entities, dataVersion, sourceFile,
                    thumbnail);
        }
    }
}
