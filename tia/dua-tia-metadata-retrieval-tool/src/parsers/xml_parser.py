import logging
import re
import xml.etree.ElementTree as ET
from typing import List, Union

from core.exceptions import MetadataError
from core.log import configure_logger

NS = {"tei": "http://www.tei-c.org/ns/1.0"}

configure_logger()
logger = logging.getLogger(__name__)


def extract_text_from_element(element: Union[ET.Element, None]) -> str:
    text = str(element.text) if element is not None else ""
    return text


def get_title(root: ET.Element) -> str:
    """Extract the title from the XML-TEI file."""
    title_element = root.find(".//tei:titleStmt/tei:title", NS)
    title = extract_text_from_element(title_element)

    return title


def get_author_metadata(root: ET.Element) -> list:
    author_elements = root.findall(
        ".//tei:sourceDesc//tei:biblStruct//tei:analytic//tei:author", NS
    )
    authors_info = []

    for author in author_elements:
        # Extract the author's name
        forename_element = author.find("./tei:persName/tei:forename", NS)
        forename = extract_text_from_element(forename_element)

        surname_element = author.find("./tei:persName/tei:surname", NS)
        surname = extract_text_from_element(surname_element)

        if forename != "" and surname != "":
            full_name = forename + " " + surname
        else:
            full_name = ""

        # Extract the affiliation
        affiliation_element = author.find(
            "./tei:affiliation//tei:orgName[@type='institution']", NS
        )
        affiliation = extract_text_from_element(affiliation_element)

        # Extract the country
        country_element = author.find("./tei:affiliation//tei:address//tei:country", NS)
        country = extract_text_from_element(country_element)

        authors_info.append(
            {
                "name": full_name,
                "affiliation": affiliation,
                "country": country,
            }
        )

    return authors_info


def get_authors(root: ET.Element) -> List[str]:
    """Extract a list containing the authors from the XML-TEI file."""
    author_elements = root.findall(
        ".//tei:sourceDesc//tei:biblStruct//tei:analytic//tei:author", NS
    )
    authors_list = []

    for author_element in author_elements:
        forename_element = author_element.find("./tei:persName/tei:forename", NS)
        surname_element = author_element.find("./tei:persName/tei:surname", NS)

        forename = extract_text_from_element(forename_element)
        surname = extract_text_from_element(surname_element)

        if forename != "" and surname != "":
            full_name = forename + " " + surname
            authors_list.append(full_name)

    return authors_list


def get_affiliations(root: ET.Element) -> List[str]:
    """Extract a list containing the authors' affiliations from the XML-TEI file."""
    affiliations_path = ".//tei:sourceDesc//tei:biblStruct//tei:analytic//tei:author//tei:affiliation//tei:orgName[@type='institution']"
    affiliation_elements = root.findall(affiliations_path, NS)
    affiliations = [str(affiliation.text) for affiliation in affiliation_elements]

    return list(set(affiliations))


def get_countries(root: ET.Element) -> List[str]:
    """Extract a list containing the affiliation's countries from the XML-TEI file."""
    countries_path = ".//tei:sourceDesc//tei:biblStruct//tei:analytic//tei:author//tei:affiliation//tei:address//tei:country"
    country_elements = root.findall(countries_path, NS)
    countries = [str(country.text) for country in country_elements]

    return list(set(countries))


def get_pub_date(root: ET.Element) -> str:
    """Extract the publication date from the XML-TEI file."""
    pub_date_element = root.find(".//tei:publicationStmt//tei:date", NS)

    if pub_date_element is not None and "when" in pub_date_element.attrib:
        pub_date = str(pub_date_element.attrib["when"])
        return pub_date
    else:
        return ""


def get_funders(root: ET.Element) -> List[str]:
    """Extract a list containing all the funders from the XML-TEI file."""
    funder_elements = root.findall(".//tei:funder//tei:orgName", NS)
    funders = [str(funder.text) for funder in funder_elements]

    return funders


def extract_sentences(element: ET.Element) -> List[str]:
    """Extract sentences from a section of the XML-TEI file."""
    phrases = []
    paragraphs = element.findall(".//tei:p", NS)

    if len(paragraphs) > 0:
        for p in paragraphs:
            sentences = p.findall(".//tei:s", NS)
            if len(sentences) > 0:
                for s in sentences:
                    # Regular expression pattern to match the text content inside <ns0:s> and <ns0:ref> tags
                    tag_pattern = re.compile(r"<[^>]+>")
                    # Delete the tags
                    s_text = re.sub(
                        tag_pattern, "", ET.tostring(s, encoding="unicode", method="xml")
                    )
                    phrases.append(s_text.strip())

    return phrases


def get_abstract(root: ET.Element) -> List[str]:
    """Extract the abstract text from the XML-TEI file."""
    abstract = root.find(".//tei:abstract//tei:div", NS)

    if abstract is not None:
        abstract_sentences = extract_sentences(abstract)
    else:
        abstract_sentences = []

    return abstract_sentences


def get_doi(root: ET.Element) -> str:
    """Extract the DOI from the XML-TEI file."""
    doi_element = root.find(".//tei:sourceDesc//tei:biblStruct//tei:idno[@type='DOI']", NS)
    doi = extract_text_from_element(doi_element)

    return doi


def get_full_text_content(root: ET.Element) -> List[dict]:
    """Extract full-text content from the XML-TEI file."""
    sections = root.findall(".//tei:text//tei:body//tei:div", NS)
    sections_text = []

    for section in sections:
        section_title_element = section.find(".//tei:head", NS)
        section_title_num = (
            section_title_element.get("n", "").strip() if section_title_element is not None else ""
        )
        section_title = extract_text_from_element(section_title_element).strip()

        if section_title == "":
            section_title = (section_title_num + " " + "Unknown").strip()
        else:
            section_title = (section_title_num + " " + section_title).strip()

        sentences = extract_sentences(section)
        sections_text.append({"section": section_title, "text": sentences})

    return sections_text


class XMLTEIParser:
    def __init__(self, xml_path: str):
        self.xml_path = xml_path

    def parse(self) -> dict:
        # Parse XML file
        try:
            tree = ET.parse(self.xml_path)
            root = tree.getroot()

            # Extract metadata
            title = get_title(root)
            author_element = get_author_metadata(root)
            authors = get_authors(root)
            affiliations = get_affiliations(root)
            countries = get_countries(root)
            pub_date = get_pub_date(root)
            funders = get_funders(root)
            abstract = get_abstract(root)
            doi = get_doi(root)

            # Extract body content
            full_text_content = get_full_text_content(root)

            data = {
                "title": title,
                "author_info": author_element,
                "authors": authors,
                "affiliations": affiliations,
                "countries": countries,
                "publication_date": pub_date,
                "funders": funders,
                "doi": doi,
                "abstract": abstract,
                "content": full_text_content,
            }

            return data

        except ET.ParseError as pe:
            logger.warning(f"{self.xml_path} is not formatted as expected: {pe}")
            dir_name = self.xml_path.split("/")[-2]
            raise MetadataError(f"{dir_name}.json not created. Reason: Unsupported XML file.")
