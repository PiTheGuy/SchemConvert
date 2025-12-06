package pitheguy.schemconvert;

import joptsimple.*;
import pitheguy.schemconvert.converter.ConversionException;
import pitheguy.schemconvert.converter.Converter;
import pitheguy.schemconvert.converter.formats.SchematicFormat;
import pitheguy.schemconvert.converter.formats.SchematicFormats;
import pitheguy.schemconvert.ui.Gui;
import pitheguy.schemconvert.util.Util;

import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length == 0)
            new Gui();
        else
            processCommandLine(args);
    }

    private static void processCommandLine(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.accepts("help", "Show this help message").forHelp();
        parser.accepts("input", "Input file").withRequiredArg().ofType(File.class).required();
        parser.accepts("format",
                "Output format (One of: nbt, schem, litematic). If not specified, format will be inferred from output file")
                .withRequiredArg().ofType(String.class);
        parser.accepts("output", "Output file. If not specified, will output to the same folder as the input file.")
                .requiredUnless("format").withRequiredArg().ofType(File.class);
        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }
        if (options.has("help")) {
            parser.printHelpOn(System.out);
            return;
        }
        File inputFile = (File) options.valueOf("input");
        Output output = getOutput(options);
        if (output == null)
            return; // Error printed in getOutput

        File outputFile = output.file();
        SchematicFormat format = output.format();
        if (!inputFile.exists()) {
            printError("Input file not found: " + inputFile);
            return;
        }
        if (Converter.SCHEMATIC_EXTENSIONS.stream().noneMatch(ext -> inputFile.getName().endsWith(ext)))
            System.err.println("Unrecognized input file: " + inputFile);
        try {
            new Converter().convert(inputFile, outputFile, format);
            System.out.println("Successfully converted " + inputFile + " to " + outputFile);
        } catch (IOException e) {
            printError("An error occurred reading or writing to disk: " + e.getMessage());
        } catch (ConversionException | pitheguy.schemconvert.nbt.NbtException e) {
            printError(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            printError("An error occurred while converting " + inputFile + " to " + outputFile);
        }
    }

    private static Output getOutput(OptionSet options) {
        if (options.has("output") && options.has("format")) {
            File outputFile = (File) options.valueOf("output");
            String formatStr = (String) options.valueOf("format");
            SchematicFormat format;
            try {
                format = SchematicFormats.formatFromExtension("." + formatStr);
            } catch (IllegalArgumentException e) {
                printError("Unrecognized output format: " + formatStr);
                return null;
            }
            if (!Util.getExtension(outputFile.getName()).equalsIgnoreCase(format.getExtension()))
                System.out.println(
                        "Warning: output file " + outputFile + " does not match the format " + format.getExtension());
            return new Output((File) options.valueOf("output"), format);
        } else if (options.has("output")) {
            File outputFile = (File) options.valueOf("output");
            SchematicFormat format;
            try {
                format = SchematicFormats.formatFromExtension(Util.getExtension(outputFile.getName()));
            } catch (IllegalArgumentException e) {
                printError("Output file doesn't match any known format. Specify the format with -format");
                return null;
            }
            return new Output(outputFile, format);
        } else if (options.has("format")) {
            SchematicFormat format;
            try {
                format = SchematicFormats.formatFromExtension("." + options.valueOf("format"));
            } catch (IllegalArgumentException e) {
                printError("Unrecognized output format: " + options.valueOf("format"));
                return null;
            }
            File inputFile = (File) options.valueOf("input");
            File outputFile = new File(Util.stripExtension(inputFile.getName()) + format.getExtension());
            return new Output(outputFile, format);
        } else {
            throw new IllegalStateException("Missing both output and format"); // Should be caught by option parser
                                                                               // constraints theoretically
        }
    }

    private static void printError(String message) {
        System.err.println(message);
        System.exit(1);
    }

    private record Output(File file, SchematicFormat format) {
    }
}