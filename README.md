 # Aggregate file tree to text
 
Sometimes it is needed to put a file tree (e.g. project tree) into a single, usually
human-readable text file. This kotlin-native CLI tool does it.

Binary (and all unknown files are treated as binary) are represented either as
classic human-readable dump (by default) or as a more space-savvy base64 encoding.

Kotlin native allows it to be redistributed as a binary CLI tool.