import logging
import os
from typing import Literal, Optional

import pandas as pd

from core.generator import IMRaDDataGenerator, PolicyDataGenerator
from utils import utils
from utils.log import configure_logger

configure_logger()
logger = logging.getLogger(__name__)


class DataProcessor:
    def __init__(
        self,
        input_dir: str,
        method: Literal["imrad", "mixed", "biblio", "policy"],
        config: dict,
        output_dir: Optional[str] = None,
        name: Optional[str] = None,
    ):
        self.input_dir = input_dir
        self.output_dir = output_dir
        self.method = method
        self.name = name if name is not None else os.path.basename(input_dir)
        self.config = config
        self.mode = None

    def process_metadata(self, data: dict) -> dict:
        """Process and filter metadata input."""
        # Extract only relevant fields
        relevant_fields = [
            "file_id",
            "repo_name",
            "initiative",
            "mission",
            "doc_type",
            "doi",
            "title",
            "publisher",
            "abstract",
            "tf_idf_score",
            "citations",
            "JIF",
            "overton_score",
            "full_text",
        ]
        return {key: data.get(key, "") for key in relevant_fields}

    def process_imrad(self, data: dict) -> dict:
        """Process metadata for IMRaD method."""
        generator = IMRaDDataGenerator(data, self.config)
        df = generator.run()
        for field in ["abstract", "full_text", "overton_score"]:
            _ = data.pop(field)
        return {**data, **df}

    def process_mixed(self, data: dict) -> dict:
        """Process metadata for mixed method."""
        raise NotImplementedError

    def process_biblio(self, data: dict) -> dict:
        """Process metadata for Biblio method."""
        raise NotImplementedError

    def process_policy(self, data: dict) -> dict:
        """Process metadata for Policy method."""
        generator = PolicyDataGenerator(data, self.config)
        df = generator.run()
        for field in ["full_text", "citations", "JIF"]:
            _ = data.pop(field)
        return {**data, **df}

    def process_train_data(self) -> pd.DataFrame:
        """Process a training dataset based on the processing method."""
        # Set mode
        self.mode = "training"

        # List all JSON files in the input directory
        doc_list = [doc for doc in os.listdir(self.input_dir) if doc.endswith(".json")]
        if not doc_list:
            warn = f"No metadata JSON files found in {self.input_dir}"
            logger.error(warn)
            raise FileNotFoundError(warn)

        documents = []
        logger.info(f"Found {len(doc_list)} documents. Processing..")
        for doc in doc_list:
            try:
                # Load and process metadata
                data = utils.load_json(os.path.join(self.input_dir, doc))
                data = self.process_metadata(data)

                # Method-specific processing
                processor = {
                    "imrad": self.process_imrad,
                    "mixed": self.process_mixed,
                    "biblio": self.process_biblio,
                    "policy": self.process_policy,
                }.get(self.method)

                if not processor:
                    raise ValueError(f"Unsupported method: {self.method}")

                document = processor(data)
                if document:
                    documents.append(document)
            except Exception as e:
                logger.error(f"Error processing document {doc}: {e}")
                raise

        # Step 1: Convert to DataFrame and preprocess it
        df_documents = pd.DataFrame(documents)
        df = utils.preprocess_documents(df_documents, self.config, self.method)

        # Step 2: Normalize numerical features
        documents_df = utils.normalize_all_numerical(df)

        # Step 3: Assign label
        documents_df = utils.assign_label(documents_df, self.method)

        # Step 4: Update JSON
        utils.update_json_files(documents_df, self.input_dir, self.method, self.mode)

        return documents_df

    def process_inference_data(self) -> Optional[pd.Series]:
        """Process inference data based on the selected method (imrad, mixed, biblio, policy).
        Returns a normalized pandas Series or None if processing fails."""
        self.mode = "classification"
        logger.info(f"Started processing {os.path.basename(self.input_dir)}..")

        try:
            # Load and process metadata
            data = utils.load_json(os.path.join(self.input_dir))
            if not data.get("full_text"):
                logger.warning("Document unclassified. Reason: full-text unavailable.")
                return None

            # Metadata preprocessing
            data = self.process_metadata(data)

            # Method-specific processing
            processor = {
                "imrad": self.process_imrad,
                "mixed": self.process_mixed,
                "biblio": self.process_biblio,
                "policy": self.process_policy,
            }.get(self.method)

            if not processor:
                raise ValueError(f"Unsupported mode: {self.method}")

            # Process the document using the selected processor
            document = processor(data)

            # Convert data to Series and normalize
            values = pd.Series(document)
            values = utils.normalize_single_element(values, data.get("doc_type", ""))

            return values

        except FileNotFoundError:
            logger.error(f"File not found at {self.input_dir}. Ensure the input path is correct.")
        except ValueError as ve:
            logger.error(f"Value error during processing: {ve}")
        except Exception as e:
            logger.error(f"Unexpected error while processing document {self.input_dir}: {e}")

        return None

    def save(self, df: pd.DataFrame):
        """Save a pandas DataFrame to a CSV file at the specified output directory."""
        try:
            # Ensure output directory exists
            os.makedirs(self.output_dir, exist_ok=True)
            output_path = os.path.join(self.output_dir, f"{self.name}.csv")

            # Save DataFrame to CSV
            df.to_csv(output_path, sep=";", index=False)
            logger.info(f"Dataset succesfully saved at: {output_path}")

        except PermissionError:
            logger.error(
                f"Permission denied while writing to {output_path}. Check your file permissions."
            )
        except IOError as io_err:
            logger.error(f"I/O error while saving to {output_path}: {io_err}")
        except Exception as e:
            logger.error(f"Unexpected error while saving file: {e}")
