import argparse

from core.extractor import MetadataExtractor


def main():
    parser = argparse.ArgumentParser(description="Metadata Extraction Tool")
    parser.add_argument(
        "input_dir", type=str, help="Directory containing subdirectories for documents."
    )
    parser.add_argument(
        "-o",
        "--output-dir",
        type=str,
        default=None,
        help="Directory to save output files. Defaults to document directory.",
    )
    parser.add_argument(
        "--create-csv",
        action="store_true",
        help="Whether to create a summary CSV file. Defaults to False.",
    )
    parser.add_argument(
        "--output-csv",
        type=str,
        default=None,
        help="Directory to save CSV file. Defaults to output dir.",
    )
    parser.add_argument(
        "--extract-all",
        action="store_true",
        help="If set, extracts metadata for a folder of documents.",
    )
    args = parser.parse_args()

    output_csv = args.output_csv if args.output_csv is not None else args.output_dir

    extractor = MetadataExtractor(args.input_dir, args.output_dir, args.create_csv, output_csv)

    if args.extract_all:
        extractor.extract_all()
    else:
        extractor.extract()


if __name__ == "__main__":
    main()
