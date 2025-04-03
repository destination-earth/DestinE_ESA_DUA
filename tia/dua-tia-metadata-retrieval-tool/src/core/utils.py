import os
import re
import string
from typing import List, Optional

import contractions
import langdetect
import nltk
from sklearn.feature_extraction.text import TfidfVectorizer, ENGLISH_STOP_WORDS

from core.exceptions import MetadataError

MISSION_KEYWORDS = {"ESA Swarm": "swarm", "CryoSat-2": "cryosat"}
VALID_COUNTRIES = os.path.abspath(
    os.path.join(os.path.dirname(__file__), "../config/countries.txt")
)


def find_xml_in_directory(input_dir: str) -> Optional[str]:
    """Finds a single XML file in the specified directory."""
    if not os.path.exists(input_dir):
        raise FileNotFoundError(f"The directory {input_dir} does not exist.")

    if not os.path.isdir(input_dir):
        raise ValueError(f"The path {input_dir} is not a directory.")

    xml_files = [os.path.join(input_dir, f) for f in os.listdir(input_dir) if f.endswith(".xml")]

    if len(xml_files) == 1:
        return xml_files[0]

    elif len(xml_files) == 0:
        raise MetadataError(
            f"{os.path.basename(input_dir)}.json not created. Reason: XML not found."
        )

    else:
        raise ValueError(
            f"Multiple XML files found in {input_dir}. Please ensure that only one XML file exists."
        )


def get_readable_full_text(xml_data: dict) -> str:
    """Extracts and concatenates the full text content from the XML data."""
    full_txt = []

    content = xml_data.get("content", [])
    if not isinstance(content, list):
        return ""

    for section in content:
        section_title = section.get("section", "")
        if section_title:
            full_txt.append(f"\t{section_title}\n")

        text_content = section.get("text", [])
        if isinstance(text_content, list):
            full_txt.append(" ".join(text_content))

        full_txt.append("\n\n")

    return "".join(full_txt)


def choose_longer_full_text(tika_full_text: str, grobid_full_text: str) -> str:
    """Chooses the longer of the two given full-text options."""
    return tika_full_text if len(tika_full_text) >= len(grobid_full_text) else grobid_full_text


def detect_language(full_text: str) -> str:
    """Detect the language of the given text."""
    if not isinstance(full_text, str) or not full_text.strip():
        return ""
    try:
        return langdetect.detect(full_text)
    except langdetect.lang_detect_exception.LangDetectException:
        return ""


def text_preprocessing(doc: str, stopwords: set = None) -> str:
    """
    Preprocesses the input text by performing the following steps:
    1. Lowercasing
    2. Expanding contractions
    3. Tokenizing
    4. Removing punctuation
    5. Removing stopwords
    """
    if stopwords is None:
        stopwords = ENGLISH_STOP_WORDS  # Default to sklearn's stopwords

    doc = contractions.fix(doc.lower())  # Expand contractions and lowercase text
    tokens = nltk.tokenize.word_tokenize(doc)  # Tokenize
    # Remove punctuation and stopwords
    tokens = [word for word in tokens if word not in string.punctuation and word not in stopwords]

    return " ".join(tokens)


def get_tf_idf(full_text: str, mission: str) -> float:
    """Computes the TF-IDF score of a specific word in the given full text."""
    if not isinstance(full_text, str) or not full_text.strip():
        return 0.0  # Return 0.0 for invalid or empty text inputs

    # Map mission word to specific keyword
    mission_word = MISSION_KEYWORDS.get(mission, mission.lower())

    # Preprocess the text
    full_text_preprocessed = text_preprocessing(full_text)

    # Compute TF-IDF score
    try:
        vectorizer = TfidfVectorizer(strip_accents="unicode", min_df=1)
        tf_idf_matrix = vectorizer.fit_transform([full_text_preprocessed])

        # Retrieve the TF-IDF score for the mission word
        word_index = vectorizer.vocabulary_.get(mission_word)
        return float(tf_idf_matrix[0, word_index]) if word_index is not None else 0.0

    except ValueError:  # TF-IDF computation failed
        return 0.0


def load_country_list(path: Optional[str] = None) -> List[str]:
    """Load a list of valid country names from a text file."""
    if path is None:
        path = VALID_COUNTRIES
    try:
        with open(path, "r") as file:
            countries = file.read().splitlines()
        # Ensure the list is not empty
        if not countries:
            raise ValueError(f"The file at {path} is empty.")
        return [country.strip() for country in countries if country.strip()]
    except FileNotFoundError:
        raise FileNotFoundError(f"Country list file not found at: {path}")
    except Exception as e:
        raise RuntimeError(f"Error loading country list: {e}")


def clean_country_name(country: str, valid_countries: List[str]) -> str:
    """Clean and standardize a country name, and check it against a list of valid country names."""
    if not country:
        return ""  # Handle empty or None input

    # Remove punctuation
    country = re.sub(r"[^\w\s]", "", country)

    # Further cleaning
    country = str(country).split(",")[0].split(".")[0].split(";")[0]
    country = country.replace("The ", "").replace("(", "").replace(")", "").strip()
    if len(country) > 3:
        country = country.title()

    # Standardize known variants using a mapping
    country_lower = country.lower()
    standard_country_names = {
        "uk": "United Kingdom",
        "usa": "United States",
        "china": "China",
        "russia": "Russia",
        "korea": "Republic of Korea",
    }
    country = standard_country_names.get(country_lower, country)

    # Match against valid country names
    for valid_country in valid_countries:
        if (valid_country.lower() in country_lower) or (country_lower in valid_country.lower()):
            return valid_country

    return country  # Return cleaned name if no match found


def clean_title(title: str) -> str:
    """Cleans a title by removing trailing points, extra whitespaces, unwanted characters, and formats special characters."""
    if not isinstance(title, str):
        raise ValueError("Input title must be a string.")

    # Remove trailing periods
    title = title.rstrip(".")

    # Replace newlines and tabs with a space
    title = re.sub(r"[\n\r\t]", " ", title)
    title = re.sub(r"[^\w\s\(\[\-,.!?;:\]\)]", "", title)

    # Remove multiple spaces
    title = re.sub(r"\s+", " ", title)

    # Remove whitespace between words and punctuation
    title = re.sub(r"\s([,.!?;:])", r"\1", title)

    # Strip leading and trailing spaces
    return title.strip()
