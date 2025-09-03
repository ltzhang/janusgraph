#!/usr/bin/env python3
"""
Script to process the 'out' file and replace non-ASCII characters with readable ASCII representations.
This makes the file grepable and readable while preserving newlines and carriage returns.
"""

import sys
import os

def char_to_readable(char):
    """Convert a character to a readable ASCII representation."""
    code = ord(char)
    
    # Preserve newlines and carriage returns as actual characters
    if code == 10:  # newline
        return char  # Keep as actual newline
    elif code == 13:  # carriage return
        return char  # Keep as actual carriage return
    elif code == 9:  # tab
        return char  # Keep as actual tab
    elif code == 0:
        return "\\0"  # null character
    elif code == 27:
        return "\\e"  # escape
    elif code < 32:
        return f"\\x{code:02x}"  # other control characters
    elif code < 127:
        return char  # printable ASCII
    else:
        return f"\\x{code:02x}"  # non-ASCII characters

def process_file(input_file, output_file):
    """Process the input file and write readable output."""
    try:
        with open(input_file, 'rb') as f:
            content = f.read()
        
        # Convert bytes to string, replacing non-ASCII with readable representations
        readable_content = ""
        for byte in content:
            char = chr(byte)
            readable_content += char_to_readable(char)
        
        # Write the processed content
        with open(output_file, 'w', encoding='utf-8') as f:
            f.write(readable_content)
        
        print(f"Successfully processed {input_file} -> {output_file}")
        print(f"Original file size: {len(content)} bytes")
        print(f"Processed file size: {len(readable_content)} characters")
        
    except FileNotFoundError:
        print(f"Error: File '{input_file}' not found")
        sys.exit(1)
    except Exception as e:
        print(f"Error processing file: {e}")
        sys.exit(1)

def main():
    input_file = "out"
    output_file = "out_readable.txt"
    
    if not os.path.exists(input_file):
        print(f"Error: Input file '{input_file}' does not exist")
        sys.exit(1)
    
    print(f"Processing {input_file}...")
    process_file(input_file, output_file)
    print(f"Output written to {output_file}")
    print("You can now grep this file for patterns.")

if __name__ == "__main__":
    main()
