import json
import logging
import os
from datetime import datetime
from typing import Any, List, Literal, Union

from bs4 import BeautifulSoup

from core.exceptions import MetadataError
from core.log import configure_logger

METADATA_FILE_NAMES = {
    "document": "document-metadata.json",
    "unpaywall": "unpaywall-metadata.json",
    "crossref": "cross-ref-metadata.json",
    "opencitations": "open-citations-metadata.json",
}

configure_logger()
logger = logging.getLogger(__name__)


class JSONParser:
    """Base class for loading and delegating JSON parsing based on document type."""

    def __init__(self, input_dir: str):
        self.input_dir = input_dir

    def load_json(self, doc_type: str, file_names: dict = METADATA_FILE_NAMES) -> dict:
        """Load JSON metadata from the specified file."""
        file_names["tika"] = f"{os.path.basename(self.input_dir)}-tika.json"
        json_path = os.path.join(self.input_dir, file_names[doc_type])
        try:
            with open(json_path, "r") as file:
                json_metadata = json.load(file)
                return json_metadata
        except (FileNotFoundError, IOError):
            if doc_type in {"document"}:
                raise MetadataError(
                    f"{os.path.basename(self.input_dir)}.json not created. Reason: {file_names[doc_type]} not found."
                )
            else:
                logger.warning(f"{file_names[doc_type]} not found in {self.input_dir}")
                return {}

    def parse(
        self, doc_type: Literal["document", "unpaywall", "crossref", "opencitations", "tika"]
    ) -> dict:
        """Delegate parsing to the appropriate parser based on file type."""
        json_metadata = self.load_json(doc_type)

        # Mapping file types to parser classes
        parser_classes = {
            "document": DocumentMetadataParser,
            "unpaywall": UnpaywallMetadataParser,
            "crossref": CrossRefMetadataParser,
            "opencitations": OpenCitationsMetadataParser,
            "tika": TikaMetadataParser,
        }

        parser_class = parser_classes.get(doc_type)
        if not parser_class:
            raise ValueError(f"Unsupported document type: {doc_type}")

        # Initialize and use the specific parser
        parser = parser_class(json_metadata)
        metadata = parser.parse()

        return metadata


class BaseJSONMetadataParser:
    """Base class for JSON metadata parsers."""

    def __init__(self, json_metadata: dict):
        self.json_metadata = json_metadata

    def _get_field(self, path: list, default: Any = "") -> Union[str, int, list, float]:
        """Retrieves a nested field from the JSON metadata."""
        try:
            value = self.json_metadata
            for key in path:
                value = value[key]
            return value if value is not None else default
        except (KeyError, TypeError):
            return default

    def parse(self):
        """Override this method in subclasses for specific parsing logic."""
        raise NotImplementedError("Subclasses must implement the parse method.")


class DocumentMetadataParser(BaseJSONMetadataParser):
    """Parser for document metadata."""

    def _get_publication_date(self, path: list, default: str = "") -> str:
        """Retrieves and formats the publication date from a timestamp field."""
        try:
            pub_date_timestamp = self._get_field(path, default)
            if isinstance(pub_date_timestamp, (int, float)):
                pub_date_datetime = datetime.fromtimestamp(pub_date_timestamp / 1000)
                return pub_date_datetime.strftime("%Y-%m-%d")
            return default
        except (TypeError, ValueError):
            return default

    def parse(self) -> dict:
        """Parses and structures document metadata into a dictionary."""
        # Define fields and their extraction paths
        field_extractors = {
            "document_type": (["configuration", "documentType"], ""),
            "initiative": (["configuration", "initiative"], ""),
            "kw_dict_id": (["configuration", "keywordDictionary"], ""),
            "title": (["document", "title"], ""),
            "doi": (["document", "doi"], ""),
            "publication_date": (["document", "publishedOn"], ""),
            "mission": (["document", "keywordDictionarySecondLevel"], ""),
            "abstract": (["document", "docAbstract"], ""),
            "authors": (["document", "authors"], []),
            "num_citations": (["publicationDetails", "citationCount"], 0),
            "jif": (["publicationDetails", "journalImpactFactor"], 0.0),
            "overton_score": (["publicationDetails", "repositoryScore"], 0),
        }

        # Extract metadata using the defined paths
        metadata = {}
        for field, (path, default) in field_extractors.items():
            try:
                if field == "publication_date":  # Special handling for publication date
                    metadata[field] = self._get_publication_date(path, default)
                else:
                    metadata[field] = self._get_field(path, default)

            except Exception as e:
                logger.warning(f"Failed to extract {field}: {e}. Setting to default.")
                metadata[field] = default

        return metadata


class UnpaywallMetadataParser(BaseJSONMetadataParser):
    """Parser for Unpaywall metadata."""

    def _get_affiliation_and_country(self, author: dict[str, List[dict[str, str]]]) -> tuple:
        """Determines the affiliation and country for an author."""
        # Extract affiliation and country from Unpaywall metadata
        affiliation_data = author.get("affiliation", "")
        if affiliation_data:
            affiliation = affiliation_data[0].get("name", "")
            country = affiliation.split(" ")[-1] if " " in affiliation else ""
        else:
            affiliation, country = "", ""

        return affiliation, country

    def _get_authors_info(self, path: list, default: list = []) -> list:
        """Extracts author information from Unpaywall metadata and optional XML data."""
        authors_info = []
        authors = self._get_field(path, default)

        for author in authors:
            assert isinstance(author, dict)
            # Extract author names
            given_name = str(author.get("given", "")).strip()
            family_name = str(author.get("family", "")).strip()
            author_name = f"{given_name} {family_name}".strip()

            # Extract affiliation and country
            affiliation, country = self._get_affiliation_and_country(author)

            # Append author details
            authors_info.append(
                {
                    "author_name": author_name,
                    "affiliation": affiliation,
                    "country": country,
                }
            )

        return authors_info

    def parse(self) -> dict:
        """Parses and structures Unpaywall metadata into a dictionary."""
        # Define fields and their extraction paths
        field_extractors = {
            "genre": (["genre"], ""),
            "journal_name": (["journal_name"], ""),
            "authors_info": (["z_authors"], []),
            "publisher": (["publisher"], ""),
            "is_oa": (["is_oa"], ""),
            "oa_status": (["oa_status"], ""),
            "license": (["best_oa_location", "license"], ""),
        }

        # Extract metadata using the defined paths
        metadata = {}
        for field, (path, default) in field_extractors.items():
            try:
                if field == "authors_info":  # Special handling for authors_info
                    metadata[field] = self._get_authors_info(path, default)
                else:
                    metadata[field] = self._get_field(path, default)
            except Exception as e:
                logger.warning(f"Failed to extract {field}: {e}. Setting to default.")
                metadata[field] = default

        return metadata


class CrossRefMetadataParser(BaseJSONMetadataParser):
    """Parser for CrossRef metadata."""

    def parse(self) -> dict:
        """Parses and structures CrossRef metadata into a dictionary."""
        # Define fields and their extraction paths
        field_extractors = {
            "num_citations": (["message", "is-referenced-by-count"], 0),
            "reference": (["message", "reference"], []),
        }

        # Extract metadata using the defined paths
        metadata = {}
        for field, (path, default) in field_extractors.items():
            try:
                metadata[field] = self._get_field(path, default)
            except Exception as e:
                logger.warning(f"Failed to extract {field}: {e}. Setting to default.")
                metadata[field] = default

        return metadata


class OpenCitationsMetadataParser(BaseJSONMetadataParser):
    """Parser for OpenCitations metadata."""

    def parse(self) -> dict:
        """Parses and structures OpenCitations metadata into a dictionary."""
        try:
            citings = [
                str(citation["citing"]).split(" ")[1]
                for citation in self.json_metadata
                if isinstance(citation, dict)
                and "citing" in citation
                and " " in citation["citing"]
            ]
        except (TypeError, AttributeError):
            citings = []

        metadata = {
            "num_citations": len(citings),
            "citings": citings,
        }

        return metadata


class TikaMetadataParser(BaseJSONMetadataParser):
    """Parser for Tika metadata."""

    def parse_html(self, html_content: str) -> str:
        """"""
        # Parse the HTML
        soup = BeautifulSoup(html_content, "html.parser")

        # Remove unnecessary metadata tags (head section)
        for meta in soup.find_all(["meta", "title", "head"]):
            meta.extract()

        # Remove annotation divs
        for annotation in soup.find_all("div", class_="annotation"):
            annotation.extract()

        # Extract cleaned text
        cleaned_text = soup.get_text(separator="\n", strip=True)

        return cleaned_text

    def parse(self) -> dict:
        """Parses and structures Tika metadata into a dictionary."""
        # Define fields and their extraction paths
        field_extractors = {
            "title": (["metadata", "pdf:docinfo:title"], ""),
            "publication_date": (["metadata", "xmp:CreateDate"], ""),
            "publisher": (["metadata", "pdf:docinfo:producer"], ""),
            "authors": (["metadata", "pdf:docinfo:creator"], ""),
            "content": (["content"], ""),
        }

        # Extract metadata using the defined paths
        metadata = {}
        for field, (path, default) in field_extractors.items():
            try:
                metadata[field] = self._get_field(path, default)
            except Exception:
                metadata[field] = default

        # Clean the content text
        metadata["full_text"] = self.parse_html(metadata["content"])

        return metadata
