package pitheguy.schemconvert.converter.formats;

public class SchematicFormats {
    public static final SchematicFormat NBT = new NbtSchematicFormat();
    public static final SchemSchematicFormat SCHEM = new SchemSchematicFormat();
    public static final LitematicSchematicFormat LITEMATIC = new LitematicSchematicFormat();
    public static final AxiomSchematicFormat AXIOM = new AxiomSchematicFormat();
    public static final ClassicSchematicFormat CLASSIC = new ClassicSchematicFormat();

    public static SchematicFormat formatFromExtension(String extension) {
        return switch (extension) {
            case ".nbt" -> NBT;
            case ".schem" -> SCHEM;
            case ".litematic" -> LITEMATIC;
            case ".bp" -> AXIOM;
            case ".schematic" -> CLASSIC;
            default -> throw new IllegalArgumentException("Unknown extension: " + extension);
        };
    }
}
