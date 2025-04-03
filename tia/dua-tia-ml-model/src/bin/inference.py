import argparse
import logging
import os

import joblib
import yaml

from core.processor import DataProcessor
from utils import utils
from utils.log import configure_logger

configure_logger()
logger = logging.getLogger(__name__)


def valid_file(path: str) -> str:
    """Check if a file exists and can be opened."""
    try:
        with open(path, "r"):
            ...
    except IOError as e:
        raise argparse.ArgumentTypeError(e)
    else:
        return path


def main():
    parser = argparse.ArgumentParser(description="Get a classification for a new document.")
    parser.add_argument("input", type=str, help="Path to the JSON input.")
    parser.add_argument(
        "-c",
        "--config",
        type=valid_file,
        required=True,
        help="Path to YAML file with training configuration options.",
    )

    args = parser.parse_args()

    # Get document type
    doc_type = utils.get_doc_type(args.input)
    logger.info(f"Found {doc_type} document. Processing..")

    method_map = {"academic": "imrad", "policy": "policy"}
    method = method_map.get(doc_type, "")
    if not method:
        logger.error(f"Unknown document type: {doc_type}")
        return

    # Load config
    with open(args.config) as c:
        config = yaml.safe_load(c)

    # Load model config
    model_name = "cos-sim" if method == "policy" else "ensemble"
    saved_elements_folder = os.path.abspath(
        os.path.join(os.path.dirname(__file__), "../models/saved/runs")
    )

    training_id = utils.find_valid_training_id(saved_elements_folder, model_name)
    if not training_id:
        logger.error(f"No valid training ID found for {model_name}.")

    model_config_path = os.path.abspath(
        os.path.join(os.path.dirname(__file__), f"../config/{model_name}.yml")
    )
    with open(model_config_path) as file:
        model_config = yaml.safe_load(file)

    # Process metadata and generate inference data
    data = DataProcessor(input_dir=args.input, method=method, config=config)
    inference_data = data.process_inference_data()

    if inference_data is None:
        classification_event = {
            "label": "na",
            "model": "",
            "algorithm": "",
            "training_id": training_id,
            "confidence": "",
        }
        utils.update_json_classification(args.input, classification_event)
        return

    if utils.process_publisher(inference_data):
        logger.warning(
            "The document has been published by ESA, so it is classified as influential."
        )
        classification_event = {
            "label": "influential",
            "model": "",
            "algorithm": "",
            "training_id": training_id,
            "confidence": "",
        }
        utils.update_json_classification(args.input, classification_event)
        return

    # Load the classifier
    model_path = os.path.join(saved_elements_folder, f"{training_id}.pkl")
    classifier = None

    if method == "imrad":
        if not os.path.exists(model_path):
            logger.error(f"Model file not found: {model_path}")
            raise FileNotFoundError(f"Model file not found: {model_path}")

        classifier = joblib.load(model_path)

    # Import and run inference with the appropriate trainer
    trainer_class = utils.import_trainer(model_config)
    trainer = trainer_class(args.input, method, model_config)
    results = trainer.inference(inference_data, classifier)

    if results is None:
        classification_event = {
            "label": "na",
            "model": model_name,
            "algorithm": model_config.get("ALGORITHM", ""),
            "training_id": training_id,
            "confidence": "",
        }
        utils.update_json_classification(args.input, classification_event)
        return

    # Map predictions to labels
    labels = {"0": "standard", "1": "influential", "2": "benchmark"}
    predicted_label = labels.get(str(results[0]), "na")

    classification_event = {
        "label": predicted_label,
        "model": model_name,
        "algorithm": model_config.get("ALGORITHM", ""),
        "training_id": training_id,
        "confidence": results[1],
    }

    # Update JSON with classification results
    utils.update_json_classification(args.input, classification_event)
    logger.info(f"The document has been classified with label: {results[0]}: {predicted_label}")


if __name__ == "__main__":
    main()
