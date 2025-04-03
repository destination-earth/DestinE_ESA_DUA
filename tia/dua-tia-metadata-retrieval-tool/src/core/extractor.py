import logging
import os
import re
from typing import Any, List, Optional, Union

import pandas as pd
import yaml

import core.utils as utils
from core.exceptions import MetadataError
from core.log import configure_logger
from core.writer import CSVWriter, JSONWriter
from parsers.json_parser import JSONParser
from parsers.xml_parser import XMLTEIParser

LAUNCH_YEARS = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "../config/launch_years.yml")
)

configure_logger()
logger = logging.getLogger(__name__)


def extract_abstract(document_metadata: dict, xml_data: dict, full_text: str) -> str:
    """Extract abstract or generate a fallback."""
    abstract = extract_field("abstract", document_metadata, xml_data)
    if isinstance(abstract, list):
        abstract = " ".join(abstract)
    if not abstract:
        abstract = (
            full_text[:400].replace("\t", " ").replace("Unknown", "").replace("\n", " ").strip()
        )
    return abstract


def extract_authors(document_metadata: dict, unpaywall_metadata: dict, xml_data: dict) -> list:
    """Extracts and enriches author information from multiple metadata sources."""
    # Extract authors from Unpaywall and XML
    unpaywall_authors = unpaywall_metadata.get("authors_info", [{}])
    xml_authors = xml_data.get("author_info", [{}])
    authors = []

    for author in unpaywall_authors:
        author_name = str(author.get("author_name", ""))
        family_name = author_name.split(" ")[-1] if author_name else ""
        affiliation = author.get("affiliation", "")
        country = author.get("country", "")

        # Supplement with XML data if necessary
        for xml_author in xml_authors:
            xml_author_name = xml_author.get("name", "")
            if family_name and family_name in xml_author_name:
                affiliation = affiliation or xml_author.get("affiliation", "")
                country = country or xml_author.get("country", "")
                break

        if author_name:
            authors.append(
                {
                    "author_name": author_name,
                    "affiliation": affiliation,
                    "country": country,
                }
            )

    # If not authors found, fall back to document metadata
    if not authors:
        document_authors = document_metadata.get("authors", [])
        authors = [{"author_name": author} for author in document_authors if author]

    return authors


def extract_doi(xml_data: dict, document_metadata: dict) -> str:
    """Extracts and standardize the DOI from XML or document metadata."""
    # Extract DOI from XML data
    doi = str(xml_data.get("doi", ""))

    # If the DOI does not start with '10.', attempt to get it from document metadata
    if not doi.startswith("10."):
        doi_candidates = document_metadata.get("doi", [])
        doi = str(doi_candidates[0]) if isinstance(doi_candidates, list) and doi_candidates else ""

    # Remove trailing non-alphanumeric characters from the DOI
    doi = re.sub(r"[^\w\-:./]", "", doi)

    # Remove any 'https://doi.org/' or 'http://doi.org/' prefix from the DOI
    doi = re.sub(r"https?://doi\.org/", "", doi, flags=re.IGNORECASE).strip()

    # Return the DOI in uppercase
    return doi.upper()


def extract_field(
    field_name: str, document_metadata: dict, xml_data: dict, default: Any = ""
) -> Union[str, list]:
    """Extracts a specified field from document or XML metadata, prioritizing the document source."""
    # Try to get the field from document metadata
    value = document_metadata.get(field_name, default)

    # If not found, fall back to XML metadata
    if not value:
        value = xml_data.get(field_name, default)

    return value


def clean_countries(
    xml_countries: List[str], unpaywall_metadata_authors: List[dict[str, str]]
) -> List[str]:
    """Clean and merge country lists from XML and Unpaywall metadata."""
    # Load valid country list
    valid_countries = utils.load_country_list()

    # Extract countries from Unpaywall metadata
    unpaywall_metadata_countries = {
        author.get("country", "").strip()
        for author in unpaywall_metadata_authors
        if author.get("country", "")
    }

    # Combine with XML countries and clean
    all_countries = set(xml_countries) | unpaywall_metadata_countries
    cleaned_countries = {
        utils.clean_country_name(country, valid_countries)
        for country in all_countries
        if country.strip()
    }

    return sorted(cleaned_countries)  # Return a sorted list for consistency


class MetadataExtractor:
    def __init__(
        self,
        input_dir: str,
        output_dir: Optional[str],
        create_csv: bool,
        output_csv: str,
        launch_years_path: Optional[str] = None,
    ):
        self.input_dir = input_dir
        self.output_dir = output_dir if output_dir is not None else input_dir
        self.create_csv = create_csv
        self.output_csv = output_csv
        self.launch_years = self._load_launch_years(launch_years_path)

    def _load_launch_years(self, path: Optional[str]) -> dict:
        """Load launch years from YAML file once to avoid repeated file I/O."""
        try:
            with open(path or LAUNCH_YEARS, "r") as file:
                return yaml.safe_load(file)
        except Exception as e:
            logger.error(f"Failed to load launch years data: {e}")
            return {}

    def _parse_xml_metadata(self, input_dir: str) -> dict:
        """Parse XML metadata if available."""
        try:
            xml_file_path = utils.find_xml_in_directory(input_dir)
            xml_parser = XMLTEIParser(xml_file_path)
            return xml_parser.parse()
        except MetadataError:
            return {}

    def _get_repo_info(self, file_name: str, document_metadata: dict) -> tuple[str, str]:
        """Extract repository name and article ID from filename."""
        if document_metadata.get("document_type", "") == "esa":
            return "esa_public", file_name
        parts = file_name.split("-")
        return parts[0] if parts else "unknown", parts[2] if len(parts) > 2 else "unknown"

    def _is_article_metadata_valid(self, article_metadata: dict) -> bool:
        """Validates an article's metadata against specific filters for language, genre, and publication year."""
        # Extract metadata with defaults
        lang = article_metadata.get("language") or "null"
        genre = article_metadata.get("genre") or "null"
        mission = article_metadata.get("mission", "")
        file_id = article_metadata.get("file_id", "unknown")
        doc_type = article_metadata.get("doc_type", "")
        academic_flag = doc_type == "academic"
        launch_year = self.launch_years.get(mission, 0)

        # Parse publication year
        try:
            pub_year = pd.to_datetime(article_metadata.get("published", ""), errors="coerce").year
        except Exception:
            pub_year = None

        # Apply filter validation
        if lang != "en":
            logger.warning(f"{file_id}.json not created. Reason: Unsupported language '{lang}'.")
            return False

        if genre != "journal-article" and academic_flag:
            logger.warning(f"{file_id}.json not created. Reason: Unsupported genre '{genre}'.")
            return False

        if (pub_year is None or pub_year < launch_year) and academic_flag:
            logger.warning(
                f"{file_id}.json not created. Reason: Publication year '{pub_year}' is invalid or earlier than the mission launch year '{launch_year}'."
            )
            return False

        return True

    def _extract_metadata(
        self,
        input_dir: str,
        xml_data: dict,
        document_metadata: dict,
        metadata_sources: dict[str, dict],
        language: str,
        tf_idf_score: float,
        full_text: str,
    ) -> dict:
        """Generates structured metadata from multiple sources."""
        # Extract file name, repo name and article-id
        file_name = os.path.basename(input_dir)
        repo_name, article_id = self._get_repo_info(file_name, document_metadata)

        # Extract the maximum number of citations found (more updated)
        citations = max(
            int(metadata_sources.get(src, {}).get("num_citations", 0)) for src in metadata_sources
        )

        # Extract the authors
        authors = extract_authors(document_metadata, metadata_sources["unpaywall"], xml_data)

        # Extract the full_text
        if document_metadata.get("document_type", "") == "academic":
            full_text = (
                full_text if metadata_sources["unpaywall"].get("license", "") == "cc-by" else ""
            )

        # Extract the abstract
        abstract = extract_abstract(document_metadata, xml_data, full_text)

        # Extract the titles
        title = extract_field("title", document_metadata, xml_data)
        if title == "" or title == "None":
            title = file_name.replace("-", " ").strip()

        # Extract the author names
        author_names = list(set([author["author_name"] for author in authors]))
        if document_metadata.get("document_type", "") == "esa" and not author_names:
            if document_metadata.get("initiative", "") == "earth-explorers":
                author_names.append("ESA Earth Online")
            elif document_metadata.get("initiative", "") == "copernicus":
                author_names.append("ESA SentiWiki")

        metadata = {
            "file_id": file_name,
            "doc_type": document_metadata.get("document_type", ""),
            "repo_name": repo_name,
            "article_id": article_id,
            "initiative": document_metadata.get("initiative", ""),
            "mission": document_metadata.get("mission", ""),
            "keyword_dictionary_id": document_metadata.get("kw_dict_id", ""),
            "doi": extract_doi(xml_data, document_metadata),
            "title": utils.clean_title(title),
            "publisher": metadata_sources["unpaywall"].get("publisher", ""),
            "funders": xml_data.get("funders", []),
            "authors": authors,
            "author_names": author_names,
            "affiliations": xml_data.get("affiliations", []),
            "countries": clean_countries(
                xml_data.get("countries", []), metadata_sources["unpaywall"].get("authors", [])
            ),
            "genre": metadata_sources["unpaywall"].get("genre", ""),
            "journal_name": metadata_sources["unpaywall"].get("journal_name", ""),
            "language": language,
            "published": extract_field("publication_date", document_metadata, xml_data),
            "is_oa": metadata_sources["unpaywall"].get("is_oa", ""),
            "oa_status": metadata_sources["unpaywall"].get("oa_status", ""),
            "license": metadata_sources["unpaywall"].get("license", ""),
            "tf_idf_score": tf_idf_score,
            "citations": citations,
            "JIF": document_metadata.get("jif", 0.0),
            "abstract": abstract,
            "full_text": full_text,
            "overton_score": document_metadata.get("overton_score", 0),
            "reference": metadata_sources["crossref"].get("reference", []),
            "citings": metadata_sources["opencitations"].get("citings", []),
            "thumbnail": f"{file_name}.jpeg",
        }

        return metadata

    def extract(self, input_dir: Optional[str] = None) -> Optional[dict]:
        # Initialize input directory
        input_dir = input_dir or self.input_dir

        try:
            # Load and extract json metadata
            json_parser = JSONParser(input_dir)
            document_metadata = json_parser.parse("document")
            metadata_sources = {
                "unpaywall": json_parser.parse("unpaywall"),
                "crossref": json_parser.parse("crossref"),
                "opencitations": json_parser.parse("opencitations"),
            }

            # Parse xml and extract data
            if document_metadata.get("document_type", "") in ["esa", "policy"]:
                xml_data = self._parse_xml_metadata(input_dir)
                tika_metadata = json_parser.parse("tika")
                full_text = utils.get_readable_full_text(xml_data)
                full_text = utils.choose_longer_full_text(
                    tika_metadata.get("full_text", ""), full_text
                )
            else:
                xml_file_path = utils.find_xml_in_directory(input_dir)
                xml_parser = XMLTEIParser(xml_file_path)
                xml_data = xml_parser.parse()
                full_text = utils.get_readable_full_text(xml_data)

            # Compute additional metadata informations
            language = utils.detect_language(full_text)
            tf_idf_score = utils.get_tf_idf(full_text, document_metadata.get("mission", ""))

            # Collect document metadata
            article_metadata = self._extract_metadata(
                input_dir,
                xml_data,
                document_metadata,
                metadata_sources,
                language,
                tf_idf_score,
                full_text,
            )

            # Generate JSON output
            if self._is_article_metadata_valid(article_metadata):
                writer = JSONWriter(os.path.basename(input_dir), self.output_dir)
                writer.write_json(article_metadata)
                if self.create_csv:
                    return article_metadata

        except MetadataError as err:
            logger.warning(err)
            if self.create_csv:
                return None

    def extract_all(self):
        # Get directory names
        dirs = os.listdir(self.input_dir)
        documents_metadata = []
        counter = 0
        logger.info(f"Found {len(dirs)} documents in the directory {self.input_dir}.")

        logger.info("Processing documents..")
        for dir_ in dirs:
            article_metadata = self.extract(os.path.join(self.input_dir, dir_))
            if article_metadata is not None:
                documents_metadata.append(article_metadata)
                counter += 1

        # Generate CSV output
        if self.create_csv:
            writer = CSVWriter(self.input_dir, self.output_csv)
            writer.write_csv(documents_metadata)

        logger.info(f"Processed {counter} documents.")
