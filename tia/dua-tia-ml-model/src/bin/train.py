import argparse
import os

import yaml

from core.processor import DataProcessor
from utils import utils


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
    parser = argparse.ArgumentParser(description="Train ML model for document classification.")
    parser.add_argument("input_dir", type=str, help="Directory containing JSON document metadata.")
    parser.add_argument(
        "-o",
        "--output-dir",
        type=str,
        default="./output",
        help="Directory to save output files. Defaults to output",
    )
    parser.add_argument(
        "-m",
        "--method",
        type=str,
        default="imrad",
        help="Training methods: 'imrad', 'mixed', 'biblio', 'policy'",
    )
    parser.add_argument(
        "-n",
        "--name",
        type=str,
        default=None,
        help="Name of the training run. Defaults to input_dir name.",
    )
    parser.add_argument(
        "-c",
        "--config",
        type=str,
        default=valid_file,
        help="Path to YAML file with training configuration options.",
    )
    parser.add_argument(
        "--model",
        type=str,
        default="ensemble",
        help="Model name used for training ['ensemble', 'cos-sim']. Default to ensemble model.",
    )
    parser.add_argument(
        "--no-csv",
        action="store_false",
        help="Whether to create dataset CSV file.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Whether to perform model training or preparing only the dataset.",
    )

    args = parser.parse_args()

    # Load config
    with open(args.config) as c:
        config = yaml.safe_load(c)

    # Load model config
    model_config_path = os.path.abspath(
        os.path.join(os.path.dirname(__file__), f"../config/{args.model}.yml")
    )
    with open(model_config_path) as k:
        model_config = yaml.safe_load(k)

    # Process metadata and generate train dataset
    data = DataProcessor(
        input_dir=args.input_dir,
        output_dir=args.output_dir,
        method=args.method,
        name=args.name,
        config=config,
    )
    dataset = data.process_train_data()

    if args.no_csv:
        data.save(dataset)

    # Run model
    if not args.dry_run:
        trainer = utils.import_trainer(model_config)(args.input_dir, args.method, model_config)
        trainer.run(dataset)


if __name__ == "__main__":
    main()
