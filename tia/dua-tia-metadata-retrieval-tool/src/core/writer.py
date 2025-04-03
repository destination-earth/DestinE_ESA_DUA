import json
import logging
import os
import time
from typing import List, Optional

import pandas as pd

from core.log import configure_logger

configure_logger()
logger = logging.getLogger(__name__)


class JSONWriter:
    def __init__(self, file_name: str, output_dir: str):
        self.file_name = file_name
        self.output_dir = output_dir

    def write_json(self, article_metadata: dict):
        """Generates a JSON representation of article metadata and saves it to a file."""
        # Add generation time
        article_metadata["generation_time"] = int(time.time())

        # Add default empty labels
        article_metadata["training"] = []
        article_metadata["classification"] = []

        json_output_path = os.path.join(self.output_dir, f"{self.file_name}.json")

        try:
            # Ensure output directory exists
            os.makedirs(self.output_dir, exist_ok=True)

            # Write JSON
            with open(json_output_path, "w", encoding="utf-8") as json_file:
                json.dump(article_metadata, json_file, ensure_ascii=False)

            logger.info(f"Wrote JSON file to {json_output_path}")

        except Exception as e:
            logger.error(f"Failed to write JSON file to {json_output_path}: {e}")


class CSVWriter:
    def __init__(self, input_dir: str, output_dir: str, drop_columns: Optional[List[str]] = None):
        self.output_dir = output_dir
        self.input_dir = input_dir
        self.drop_columns = drop_columns or [
            "authors",
            "full_text",
            "reference",
            "citings",
            "thumbnail",
            "generation_time",
            "training",
            "classification",
        ]

    def write_csv(self, documents_metadata: List[dict]):
        """Generates a CSV representation of article metadata and saves it to a file."""
        try:
            # Convert metadata to DataFrame
            documents_df = pd.DataFrame(documents_metadata)

            # Drop unnecessary columns
            doc_df = documents_df.drop(columns=self.drop_columns, errors="ignore")

            # Construct output path
            csv_file_path = os.path.join(
                self.output_dir, f"{os.path.basename(self.input_dir)}.csv"
            )

            # Ensure output directory exists
            os.makedirs(self.output_dir, exist_ok=True)

            # Write DataFrame to CSV
            doc_df.to_csv(csv_file_path, sep=";", index=False)

            logger.info(f"Wrote CSV file to {csv_file_path}")

        except Exception as e:
            logger.error(f"Failed to wrote CSV file: {e}")
