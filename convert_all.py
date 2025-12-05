"""
usage: convert_all.py [-h] -j JAR [-d DIRECTORY] [-o OUTPUT] [-e EXTENSIONS]

Mass convert schematic files using SchemConvert JAR.

options:
  -h, --help            show this help message and exit
  -j JAR, --jar JAR     The path/name of the converter JAR file (e.g., SchemConvert-1.2.5-all.jar)
  -d DIRECTORY, --directory DIRECTORY
                        The root directory to scan for input files (default: current directory)
  -o OUTPUT, --output OUTPUT
                        The directory to save the output .bp files (default: same as input file directory)
  -e EXTENSIONS, --extensions EXTENSIONS
                        Comma-separated list of accepted input file extensions (e.g., .schem,.nbt)
"""

import os
import subprocess
import argparse
from pathlib import Path

def convert_file(jar_name, input_file_path, output_directory):
    """
    Runs the java command for a single file conversion using the specified JAR.

    Args:
        jar_name (str): The name/path of the JAR executable.
        input_file_path (Path): The full path to the input file to convert.
        output_directory (str): The specified output directory path.
    """
    # Create the output filename by taking the input filename's base name (stem) and adding the new extension (.bp)
    output_filename = Path(input_file_path).stem + ".bp"
    
    # Determine the full output path
    if output_directory == ".":
        # If output directory is '.', save the output file in the same directory as the input file
        output_file_path = Path(input_file_path).parent / output_filename
    else:
        # Otherwise, combine the absolute output directory path with the new filename
        # Ensure the output directory exists before trying to write a file there
        Path(output_directory).mkdir(parents=True, exist_ok=True)
        output_file_path = Path(output_directory) / output_filename

    # Build the complete command as a list of strings, which is safer than a single string
    # when using subprocess (avoids shell injection issues and handles spaces in paths better)
    command = [
        "java",          # The java executable
        "-jar",          # Flag to run an executable JAR file
        jar_name,        # The path to the converter JAR
        "-input",        # The input flag expected by the JAR tool
        str(input_file_path), # The full path of the input file
        "-output",       # The output flag expected by the JAR tool
        str(output_file_path) # The full path of the desired output file
    ]
    
    print(f"Converting: {input_file_path}")
    print(f"Output to: {output_file_path}")

    try:
        # Execute the command using subprocess.run
        # check=True: Raises an exception if the command returns a non-zero exit code (failure)
        # capture_output=True: Captures stdout and stderr
        # text=True: Decodes stdout/stderr as strings
        result = subprocess.run(command, check=True, capture_output=True, text=True)
        print("Conversion successful!")
        # print(result.stdout) # Uncomment to see the raw output from the Java tool
    except subprocess.CalledProcessError as e:
        # Handle errors where the Java tool failed its conversion
        print(f"Conversion failed for {input_file_path}:")
        print(e.stderr)
    except FileNotFoundError as e:
        # Handle fundamental errors like 'java' command not found or the JAR file not existing
        print(f"Error executing command: {e}")
        print("Ensure 'java' is in your system's PATH and the JAR file path is correct.")
    print("-" * 40) # Print a separator for readability


def scan_and_convert(directory, jar_name, extensions, output_dir):
    """
    Scans a directory and subdirectories for accepted file types and initiates conversion for each.

    Args:
        directory (str): The root directory to start scanning from.
        jar_name (str): The name/path of the JAR executable.
        extensions (list): A list of accepted file extensions (e.g., ['.schem', '.nbt']).
        output_dir (str): The specified output directory path.
    """
    found_files = []
    # Ensure extensions are correctly formatted with leading dots for comparison
    formatted_extensions = [f".{ext.strip('.')}" for ext in extensions]

    # os.walk generates file names in a directory tree by walking the tree top-down
    for root, _, files in os.walk(directory):
        for file in files:
            file_path = Path(root) / file
            # Check if the file's extension (converted to lowercase) is in our accepted list
            if file_path.suffix.lower() in formatted_extensions:
                found_files.append(file_path)
    
    if not found_files:
        # Provide feedback if no relevant files are found
        print(f"No files found in '{directory}' with extensions {formatted_extensions}.")
        return

    print(f"Found {len(found_files)} files to convert using {jar_name}.")
    # Iterate through all found files and call the conversion function
    for file_path in found_files:
        convert_file(jar_name, file_path, output_dir)


if __name__ == "__main__":
    # This block runs only when the script is executed directly, not when imported as a module
    
    # Initialize the argument parser with a description
    parser = argparse.ArgumentParser(description="Mass convert schematic files using SchemConvert JAR.")

    # Define the command line arguments
    parser.add_argument(
        "-j", "--jar", 
        type=str, 
        required=True, # This argument is mandatory
        help="The path/name of the converter JAR file (e.g., SchemConvert-1.2.5-all.jar)"
    )
    parser.add_argument(
        "-d", "--directory", 
        type=str, 
        default=".", # Default value is the current directory
        help="The root directory to scan for input files (default: current directory)"
    )
    parser.add_argument(
        "-o", "--output", 
        type=str, 
        default=".",
        help="The directory to save the output .bp files (default: same as input file directory)"
    )
    parser.add_argument(
        "-e", "--extensions", 
        type=str, 
        # Added .dp to the default list of accepted extensions
        default=".schem,.schematic,.nbt,.dp", 
        help="Comma-separated list of accepted input file extensions (e.g., .schem,.nbt)"
    )

    # Parse the arguments provided by the user in the command line
    args = parser.parse_args()
    
    # Split the comma-separated extensions string from the arguments into a Python list
    extensions_list = [ext.strip() for ext in args.extensions.split(',')]

    # Call the main scanning and conversion function with the parsed arguments
    scan_and_convert(
        directory=args.directory,
        jar_name=args.jar,
        extensions=extensions_list,
        output_dir=args.output
    )
