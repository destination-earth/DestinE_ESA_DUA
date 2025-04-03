# PDF Metadata Extraction

## Overview

The `dua-tia-metadata-retrieval-tool.py` extracts metadata and full-text content from PDF documents using either **[GROBID](https://grobid.readthedocs.io/en/latest/)** or **[Apache Tika](https://tika.apache.org/)**. It supports three document types:

* **Academic** (processed with GROBID)
* **Policy** (processed with both GROBID and Tika)
* **ESA** (processed with both GROBID and Tika)

The script identifies the document type from a `document-metadata.json` file and applies the appropriate parsing tools.

## Features

* Parses PDFs using GROBID and/or Tika
* Supports multiple document types
* Logs processing details to a file
* Outputs extracted content in **XML** (GROBID) or **JSON** (Tika)

## Installation

Ensure the following dependencies are installed:

* Python 3.8+
* requests==2.32.3
* tika==2.6.0

Install dependencies using:

```
pip install requests==2.32.3 tika==2.6.0
```

You need instances of GROBID and Tika running.

## Usage

Run the script with

```
python dua-tia-metadata-retrieval-tool.py <input_directory> [-o <output_directory>] [--grobid-url <URL>] [--tika-url <URL>]
```

### Example

```
python dua-tia-metadata-retrieval-tool.py /path/to/pdfs -o path/to/output --grobid-url http://localhost:8070/api/processFulltextDocument --tika-url http://localhost:9998/tika 
```

## Output files

* GROBID: Extracted content saved as `.xml`
* Tika: Extracted content saved as `.json`
* Log file: `dua-tia-metadata-retrieval-tool.log`

## Code structure

* `BaseParser`: Abstract class for parsers
* `GrobidParser`: Implement GROBID-based parsing
* `TikaParser`: Implements Tika-based parsing
* `PDFProcessor`: Manages processing logic based on document type.

## Logging

Logs are saved in `<output_directory>/dua-tia-metadata-retrieval-tool.log`.

## License

This script is released under [LICENSE]
