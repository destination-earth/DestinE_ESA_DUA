import argparse
import json
import logging
import os
from typing import Literal, Optional

import requests
import tika.parser

# Constants
DOC_METADATA_JSON = "document-metadata.json"

logger = logging.getLogger(__name__)


# Logger configuration
def configure_logger(output_dir: str):
    log_file = os.path.join(output_dir, "dua-tia-metadata-retrieval-tool.log")
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
        handlers=[logging.FileHandler(log_file), logging.StreamHandler()],
    )


# Utility functions
def load_json(file_path: str) -> dict:
    """Load JSON content from a file."""
    try:
        with open(file_path, "r") as f:
            return json.load(f)
    except (FileNotFoundError, json.JSONDecodeError) as e:
        logger.error(f"Failed to load JSON file {file_path}: {e}")
        raise


def extract_doc_type(metadata: dict) -> str:
    try:
        return str(metadata["configuration"]["documentType"])
    except KeyError:
        logger.error()


def find_pdf_in_directory(source_dir: str) -> str:
    """Finds a single PDF file in the directory."""
    pdf_files = [f for f in os.listdir(source_dir) if f.endswith(".pdf")]
    if not pdf_files:
        raise FileNotFoundError(f"No PDF file found in directory {source_dir}.")
    if len(pdf_files) > 1:
        logger.warning(f"Multiple PDF files found in {source_dir}. Using the first one.")
    return os.path.join(source_dir, pdf_files[0])


# Base Class for Parsers
class BaseParser:
    def process_pdf(self, pdf_file_path: str) -> Optional[bytes]:
        raise NotImplementedError("Subclasses must implement this method.")

    def write_output(self, output_path: str, content: bytes):
        raise NotImplementedError("Subclasses must implement this method.")


# Grobid Parser Implementation
class GrobidParser(BaseParser):
    def __init__(self, grobid_url: str):
        self.grobid_url = grobid_url

    def process_pdf(self, pdf_file_path: str) -> Optional[bytes]:
        """Process a PDF file using GROBID service."""
        try:
            with open(pdf_file_path, "rb") as f:
                pdf_content = f.read()
            response = requests.post(
                self.grobid_url,
                files={"input": pdf_content},
                data={"segmentSentences": 1, "consolidateHeader": 1, "includeRawAffiliations": 1},
            )
            response.raise_for_status()
            return response.content
        except requests.RequestException as e:
            logger.error(f"Error processing PDF with Grobid {pdf_file_path}: {e}")
            return None

    def write_output(self, output_path: str, content: bytes):
        """Write content to a file."""
        with open(f"{output_path}.xml", "wb") as f:
            f.write(content)
        logger.info(f"GROBID output written to {output_path}")


# Tika Parser Stub
class TikaParser(BaseParser):
    def __init__(self, tika_url: str):
        self.tika_url = tika_url

    def process_pdf(self, pdf_file_path: str) -> Optional[bytes]:
        """Process a PDF file using TIKA service."""
        try:
            parsed = tika.parser.from_file(
                pdf_file_path, serverEndpoint=self.tika_url, xmlContent=True
            )
            return parsed
        except requests.RequestException as e:
            logger.error(f"Error processing PDF with Tika {pdf_file_path}: {e}")
            return None

    def write_output(self, output_path, content):
        with open(f"{output_path}-tika.json", "w") as f:
            json.dump(content, f)
        logger.info(f"TIKA output written to {output_path}")


# Manager Class
class PDFProcessor:
    def __init__(self, source_dir: str, output_dir: str, grobid_url: str, tika_url: str):
        self.source_dir = source_dir
        self.output_dir = output_dir
        self.parsers = {
            "academic": [GrobidParser(grobid_url)],
            "policy": [GrobidParser(grobid_url), TikaParser(tika_url)],
            "esa": [GrobidParser(grobid_url), TikaParser(tika_url)],
        }

    def run(self, document_type: Literal["academic", "policy", "esa"]):
        os.makedirs(self.output_dir, exist_ok=True)
        configure_logger(self.output_dir)

        logger.info(f"Start processing {os.path.basename(self.source_dir)}..")
        pdf_file_path = find_pdf_in_directory(self.source_dir)

        parsers = self.parsers.get(document_type)
        if not parsers:
            logger.error(f"Unknown document type specified: {document_type}.")

        for parser in parsers:
            result = parser.process_pdf(pdf_file_path)
            if result:
                output_file = os.path.join(self.output_dir, f"{os.path.basename(self.source_dir)}")
                parser.write_output(output_file, result)


# Argument parsing
def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("input_dir", type=str, help="Directory containing PDF and metadata.")
    parser.add_argument(
        "-o",
        "--output-dir",
        default=None,
        help="Output directory for results. Defaults to input directory.",
    )
    parser.add_argument("--grobid-url", help="GROBID service URL.")
    parser.add_argument("--tika-url", help="TIKA service URL.")
    return parser.parse_args()


# Main Function
def main():
    args = parse_args()
    doc_metadata = load_json(os.path.join(args.input_dir, DOC_METADATA_JSON))
    doc_type = extract_doc_type(doc_metadata)
    output_dir = args.input_dir if args.output_dir is None else args.output_dir
    if doc_type:
        processor = PDFProcessor(args.input_dir, output_dir, args.grobid_url, args.tika_url)
        processor.run(doc_type)
    else:
        raise NotImplementedError(f"Processor not implemented for the documentType: {doc_type}")


if __name__ == "__main__":
    main()
