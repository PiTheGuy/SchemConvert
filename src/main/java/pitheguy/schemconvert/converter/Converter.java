package pitheguy.schemconvert.converter;

import pitheguy.schemconvert.converter.formats.SchematicFormat;
import pitheguy.schemconvert.util.Util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Converter {
    public static final List<String> SCHEMATIC_EXTENSIONS = List.of(".nbt", ".schem", ".litematic", ".bp",
            ".schematic");

    public void convert(File input, File output, SchematicFormat outputFormat) throws IOException, ConversionException {
        Schematic schematic = Schematic.read(input);
        if (outputFormat.getExtension().equals(".bp") && schematic.getThumbnail() == null) {
            schematic = schematic.withThumbnail(ThumbnailGenerator.generate(schematic));
        }
        schematic.write(output, outputFormat);
    }

    public List<File> convert(File[] inputs, File outputDir, SchematicFormat outputFormat)
            throws IOException, ConversionException {
        if (!outputDir.isDirectory())
            throw new IOException("Output directory is not a directory!");
        List<File> failedFiles = new ArrayList<>();
        for (File file : inputs) {
            try {
                Schematic schematic = Schematic.read(file);
                if (outputFormat.getExtension().equals(".bp") && schematic.getThumbnail() == null) {
                    schematic = schematic.withThumbnail(ThumbnailGenerator.generate(schematic));
                }
                File outputFile = new File(outputDir,
                        Util.stripExtension(file.getName()) + outputFormat.getExtension());
                schematic.write(outputFile, outputFormat);
            } catch (Exception e) {
                failedFiles.add(file);
                e.printStackTrace();
            }
        }
        return failedFiles;
    }
}
