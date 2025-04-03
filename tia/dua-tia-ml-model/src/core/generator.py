import os
import re
from typing import Dict, List, Literal, Optional, Union

import numpy as np
import yaml


class DataGenerator:
    def __init__(
        self,
        data: dict,
        config: Dict[str, Dict[str, Union[float, Dict[str, Union[float, List[str]]]]]],
    ):
        self.data = data
        self.config = config
        self.keywords = self.load_keywords()
        self.mission = self.get_mission()

    def load_keywords(self) -> dict:
        """Load keywords from a YAML file based on the initiative specified in the data."""
        initiative = self.data.get("initiative", "")
        if not initiative:
            raise ValueError("Initiative not specified in the metadata.")

        keywords_path = os.path.abspath(
            os.path.join(os.path.dirname(__file__), f"../config/{initiative}-keywords.yml")
        )
        if not os.path.exists(keywords_path):
            raise FileNotFoundError(f"Keywords file not found: {keywords_path}")

        with open(keywords_path) as k:
            keywords = yaml.safe_load(k)

        return keywords

    def get_mission(self) -> str:
        """Retrieve mission name from the metadata, with special handling for known mission aliases."""
        mission_aliases = self.config.get("mission_aliases", {})
        mission_name = self.data.get("mission", "")
        return mission_aliases.get(mission_name, mission_name)

    def _prepare_sentence(self, sentence: Union[str, List[str]], _type: str) -> str:
        """Prepare the sentence for processing based on type and input format."""
        if _type == "abstract" and isinstance(sentence, list):
            return sentence[0] if sentence else ""
        return sentence if isinstance(sentence, str) else ""

    def _get_mission_keywords(self, mission_name: str) -> Dict[str, List[str]]:
        """Retrieve the mission-related keywords for the given mission name."""
        return next(
            (
                mission
                for mission in self.keywords.get("missions", [])
                if mission.get("name", "").lower() == mission_name.lower()
            ),
            {},
        )

    def _count_occurrences(self, sentence: str, keywords: Union[str, List[str]]) -> int:
        """Count the occurences of keywords in the sentence."""
        if isinstance(keywords, list):
            return sum(sentence.count(kw.lower()) for kw in keywords)
        elif isinstance(keywords, str):
            return sentence.count(keywords.lower())
        return 0

    def count_keywords(
        self, sentence: str, mission_name: str, _type: Literal["kw", "title", "abstract"] = "kw"
    ) -> dict:
        """Count the occurences of keywords across different levels in a given sentence."""
        sentence = self._prepare_sentence(sentence, _type)
        sentence_lower = sentence.lower()

        # Retrieve mission-related keywords
        mission_keywords = self._get_mission_keywords(mission_name)

        levels = {
            f"count-level1-{_type}": self.keywords.get("level1", ""),
            f"count-level2-{_type}": mission_name,
            f"count-level3-{_type}": mission_keywords.get("level3", []),
            f"count-level4-{_type}": mission_keywords.get("level4", []),
        }

        # Count occurences of keywords at each level
        return {
            level: self._count_occurrences(sentence_lower, keywords)
            for level, keywords in levels.items()
        }


class IMRaDDataGenerator(DataGenerator):

    def get_level3_keywords(self, mission: str) -> List[str]:
        """Retrieve level 3 keywords associated with a specific mission."""
        for mission_dict in self.keywords.get("missions", []):
            if mission_dict.get("name", {}).lower() == mission.lower():
                return mission_dict.get("level3", [])
        return []

    def get_kw_score(self, keyword_counts: dict) -> float:
        """Calculate the keyword score based on keyword counts and their corresponding weights."""
        weights = self.config.get("keyword_weights", {})
        score = sum(
            keyword_counts.get(level, 0) * weights.get(level, 0)
            for level in [
                "count-level1-kw",
                "count-level2-kw",
                "count-level3-kw",
                "count-level4-kw",
            ]
        )
        return round(score, 2)

    def extract_keyword_sentences_contextual(
        self, paragraph: str, mission: str, level3_keywords: List[str], offset: int
    ) -> List[Dict[str, Union[str, int, float]]]:
        """Extract sentences containing specific keywords and associate them with the relevant section header."""
        # Initialize current section and result
        current_section = "Unknown"
        results = []

        # Check if it's a possible section header
        lines = paragraph.split("\n")
        for line in lines:
            # Detect section headers
            if line.startswith("\t"):
                current_section = line.strip().replace("\t", "")
                offset += len(line) + 1  # Account for the section header's newline
            else:
                # Split line into sentences
                sentences = re.split(r"(?<=\.)\s+", line)
                for sentence in sentences:
                    # Identify the position of mission or level 3 keywords matches
                    matches = [
                        match.start()
                        for kw in [mission] + level3_keywords
                        for match in re.finditer(re.escape(kw), sentence, flags=re.IGNORECASE)
                    ]
                    if matches:
                        # Use the first match's position in the sentence
                        first_match_offset = min(matches)
                        citation_offset = offset + first_match_offset

                        # Count occurences of keywords in the sentence
                        keyword_counts = self.count_keywords(sentence, mission)

                        result = {
                            "section": current_section,
                            "sentence": sentence,
                            **keyword_counts,
                            "keyword_score": self.get_kw_score(keyword_counts),
                            "offset": citation_offset,
                        }
                        results.append(result)
        return results

    def get_imrad_label(self, result: dict) -> str:
        """Get the IMRaD label for a given section title or position in the document."""
        section_title = result.get("section", "")
        citation_offset = result.get("offset", 0)
        total_doc_length = len(self.data.get("full_text", ""))
        imrad_lookup_table = self.config["IMRaD"]["lookup_table"]

        # Check for keywords in section title
        for label, keywords in imrad_lookup_table.items():
            if any(keyword.lower() in section_title.lower() for keyword in keywords):
                return label

        # Calculate label according to its position inside the article
        prop = citation_offset / total_doc_length

        if prop <= 0.25:
            return "Introduction"
        elif 0.25 < prop <= 0.50:
            return "Methods"
        elif 0.50 < prop <= 0.75:
            return "Results"

        return "Discussion"

    def get_cit_score(self, result: dict) -> Optional[float]:
        """Calculate the citation score based on keyword score and IMRaD section weights."""
        try:
            imrad_section = result.get("imrad_section", "")
            kw_score = float(result.get("keyword_score", 0.0))
            weight = float(self.config["IMRaD"]["weights"].get(imrad_section, 0))
            return round(kw_score * weight, 2)

        except (KeyError, AttributeError):
            return None

    def aggregate_results(self, results: List[dict]) -> dict:
        """Aggregate scores and contexts from extracted results."""
        if not results:
            return {}

        kw_scores = [r.get("keyword_score", 0) for r in results]
        cit_scores = [r.get("cit_score", 0) for r in results]
        citation_contexts = [r.get("sentence", "") for r in results]

        return {
            "cit_score_sum": np.sum(cit_scores),
            "cit_score_mean": np.mean(cit_scores),
            "cit_score_max": max(cit_scores),
            "kw_score_sum": np.sum(kw_scores),
            "kw_score_mean": np.mean(kw_scores),
            "kw_score_max": max(kw_scores),
            "citation_context": " ".join(citation_contexts),
        }

    def run(self):
        """Process the full text to extract keyword insights, calculate scores, and aggregate results."""
        text = str(self.data.get("full_text", ""))
        if not text:
            return {}

        # Split the text into paragraphs based on blank lines
        paragraphs = text.split("\n\n")

        # Initialize variables
        results = []
        offset = 0

        # Get the list of level 3 keywords for the mission
        level3_keywords = self.get_level3_keywords(self.mission)
        # Process each paragraph
        for paragraph in paragraphs:
            if not paragraph:
                continue  # Skip empty paragraphs

            paragraph_results = self.extract_keyword_sentences_contextual(
                paragraph, self.mission, level3_keywords, offset
            )

            if paragraph_results:
                for paragraph_result in paragraph_results:
                    paragraph_result["imrad_section"] = self.get_imrad_label(paragraph_result)
                    paragraph_result["cit_score"] = self.get_cit_score(paragraph_result)
                    results.append(paragraph_result)

            # Update offset with the length of the current paragraph
            offset += len(paragraph) + 2

        return {
            **self.aggregate_results(results),
            **self.count_keywords(self.data.get("title", ""), self.mission, "title"),
            **self.count_keywords(self.data.get("abstract", ""), self.mission, "abstract"),
        }


class PolicyDataGenerator(DataGenerator):

    def run(self):
        return {
            **self.count_keywords(self.data.get("title", ""), self.mission, "title"),
            **self.count_keywords(self.data.get("abstract", ""), self.mission, "abstract"),
        }
