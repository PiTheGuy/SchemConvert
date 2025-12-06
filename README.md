# SchemConvert

A lightweight and powerful tool to convert between different Minecraft schematic formats.

**This tool requires Java 21 or later.**

## Supported Formats

- `.nbt`: Vanilla Minecraft Structure blocks.
- `.schem`: Modern Sponge schematic format (used by WorldEdit, MCEdit Unified).
- `.litematic`: Litematica mod format.
- `.bp`: Axiom mod blueprints.
- `.schematic`: Legacy MCEdit/WorldEdit format (supports reading & writing).

## Features

- **Cross-Format Conversion**: Convert freely between any of the supported formats.
- **Legacy Support**: Full support for reading and writing the legacy `.schematic` format.
- **Batch Conversion**: Recursively convert entire folders of schematics using the Python helper script.
- **Procedural Texture Generation**: Generates high‑quality 16×16 pixel‑art textures (logs, planks, bricks, leaves, etc.) internally, so previews work without an external `textures/` folder.
- **External Textures (Optional)**: Place a `textures/block/` directory next to the JAR to override generated textures with custom assets.
- **Thumbnail Preservation**: Preserves embedded preview images when converting Axiom `.bp` files.
- **CLI & GUI**: Run from the command line for automation, or launch without arguments for a graphical interface.

## Usage

### Command Line Interface (CLI)

```bash
java -jar build/libs/SchemConvert-1.3.1-all.jar -input <input_file> -format <output_format> -output <output_file>
```

**Arguments:**

- `-input`: Path to the source file to convert.
- `-format` (optional): Desired output format/extension (e.g., `schem`, `litematic`, `bp`, `nbt`, `schematic`). If omitted, inferred from the output filename.
- `-output` (optional): Path for the converted file. If omitted, saves to the same directory with the new extension.

### External Textures (Optional)

You can optionally place a `textures/block/` folder next to the executable to use your own resource‑pack textures for the previews. If omitted, the tool will automatically generate high‑quality procedural textures.

## Building from Source

To build the project yourself, clone the repository and run the automated release script:

```bash
./scripts/build_release.bat
```

Alternatively, you can run the Gradle build command directly:

```bash
./scripts/gradlew build
```

The resulting JAR is located at `build/libs/SchemConvert-1.3.1-all.jar`.

## Batch Converter Tool

The project includes a Python helper script to mass‑convert files, which can be built into a standalone executable.

### Building the Executable

**Requirements:**

- Python installed
- `pyinstaller` installed (`pip install pyinstaller`)

To build the executable, run the provided batch script:

```batch
./scripts/build_executable.bat
```

This will create `convert_all.exe` inside `build_artifacts/dist/`.

### Batch Tool Usage

```bash
convert_all.exe -j <path_to_jar> -d <root_directory> -o <output_directory> -f <target_format>
```

- `-j`, `--jar`: Path to the `SchemConvert` JAR file (required).
- `-d`, `--directory`: Root directory to scan for files (default: current directory).
- `-o`, `--output`: Directory to save converted files (default: same as input).
- `-e`, `--extensions`: Comma‑separated list of extensions to convert (default: `.schem,.schematic,.nbt,.dp`).
- `-f`, `--format`: Target output format/extension (e.g., `bp`, `schem`, `schematic`).

**Example:**

```bash
convert_all.exe -j SchemConvert-1.3.1-all.jar -d ./my_schematics -o ./converted_blueprints -f bp
```

---
