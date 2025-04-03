from datetime import datetime
import importlib
import json
import logging
import os
import pickle
import platform
import shutil
from typing import Callable, List, Literal, Type, Union

import numpy as np
import pandas as pd
import psutil
import torch

from utils.log import configure_logger

LABEL_MAPPING = {"standard": 0, "influential": 1, "benchmark": 2, "na": None}

configure_logger()
logger = logging.getLogger(__name__)


def import_trainer(conf: dict, module_name: str = "models.models") -> Union[Callable, Type]:
    """Import a class trainer from a string path."""
    attr_name = conf.get("MODULE_NAME", "")
    return getattr(importlib.import_module(module_name), attr_name)


def load_json(path: str) -> dict:
    """Load JSON file from the specified path."""
    try:
        with open(path, "r") as file:
            json_metadata = json.load(file)
            return json_metadata
    except (FileNotFoundError, IOError):
        logger.error(f"{path} not found.")
        raise


def get_doc_type(path: str):
    """Extract document type from JSON."""
    data = load_json(path)
    return data.get("doc_type", "")


def find_valid_training_id(folder_path: str, model_name: str) -> str:
    """Looks inside a folder, finds files starting with the given model name,
    orders them alphabetically, and returns the last one."""
    # Ensure the folder exists
    if not os.path.isdir(folder_path):
        raise FileNotFoundError(f"Folder not found: {folder_path}")

    # List and filter files by incipit
    files = [f.split(".")[0] for f in os.listdir(folder_path) if f.startswith(model_name)]

    # If no files are found, return an empty string
    if not files:
        return ""

    # Sort alphabetically and take the last one
    files.sort()
    return files[-1]


def drop_doc_duplicates(df: pd.DataFrame, config: dict) -> pd.DataFrame:
    """Drops duplicate documents based on 'mission' and 'doi', prioritizing repositories."""
    required_columns = {"repo_name", "mission", "doi"}
    if not required_columns.issubset(df.columns):
        raise ValueError("Input DataFrame must contain 'repo_name', 'mission', and 'doi' columns.")

    initial_count = df.shape[0]
    repo_priority = config.get("ac_repo_priority", {})

    # Assign repository priority, defaulting to a low priority if missing
    df = (
        df.assign(repo_priority=df["repo_name"].map(repo_priority).fillna(float("inf")))
        .sort_values(by="repo_priority", na_position="last")
        .drop_duplicates(subset=["mission", "doi"], keep="first")
        .drop(columns=["repo_priority"])
    )

    removed_count = initial_count - df.shape[0]
    if removed_count > 0:
        logger.info(f"Removed {removed_count} duplicated documents. Kept {df.shape[0]} documents.")
    else:
        logger.info("No duplicate documents found.")

    return df


def drop_rows_with_missing_elements(
    df: pd.DataFrame, method: Literal["imrad", "biblio", "mixed", "policy"]
) -> pd.DataFrame:
    """Drops rows with missing element according to the specified method."""
    initial_count = df.shape[0]

    method_missing_columns = {
        "imrad": ["citation_context"],
        "policy": ["abstract"],
    }

    if method in method_missing_columns:
        df = df.dropna(subset=method_missing_columns[method])
        removed_count = initial_count - df.shape[0]
        if removed_count > 0:
            logger.info(
                f"Removed {removed_count} documents due to missing {method_missing_columns[method]}."
            )
        else:
            logger.info("No documents were removed due to missing elements.")

    elif method in {"biblio", "mixed"}:
        raise NotImplementedError(
            f"Data preprocessing for method '{method}' is not yet implemented."
        )
    else:
        raise ValueError(f"Unsupported method: {method}")

    return df


def preprocess_documents(
    df: pd.DataFrame, config: dict, method: Literal["imrad", "mixed", "biblio", "policy"]
) -> pd.DataFrame:
    """Preprocess the documents by handling missing elements and removing duplicates."""
    if df.empty:
        logger.warning("Received an empty DataFrame for preprocessing.")
        return df

    # Step 1: Drop rows with missing elements
    df = drop_rows_with_missing_elements(df, method)

    # Step 2: Remove duplicate documents for applicable methods
    if method in {"imrad", "mixed", "biblio"}:
        df = drop_doc_duplicates(df, config)

    return df


def clip_normalized_values(arr: pd.Series) -> np.ndarray:
    """Normalize values in an array, capping them at 1.0 and excluding outliers."""
    if arr.empty:
        return np.array([])  # Return an empty array for empty input

    # Handle NaNs
    arr = arr.fillna(0.0)

    # Check if the array is composed entirely of zeros
    if np.all(np.isclose(arr.values, 0.0)):
        return arr.values

    # Compute quartiles and interquartiles range
    q1, q3 = np.percentile(arr.values, [25, 75])
    iqr = q3 - q1

    # Adjust upper boundary to avoid divide-by-zero
    upper_boundary = q3 + 1.5 * iqr if iqr > 0 else np.max(arr.values)

    # If the upper boundary is still invalid, set it to a fallback value
    upper_boundary = 1.0 if np.isnan(upper_boundary) else upper_boundary

    # Normalize and clip values
    arr_norm = arr.values / upper_boundary
    arr_norm = np.nan_to_num(arr_norm, nan=0.0, posinf=1.0, neginf=0.0)
    return np.clip(arr_norm, 0, 1)


def normalize_all_numerical(df: pd.DataFrame) -> pd.DataFrame:
    """Normalize all numerical columns in the DataFrame within each group (by 'mission')."""
    if "mission" not in df.columns:
        raise ValueError("The DataFrame must contain a 'mission' column for grouping.")

    # Save normalization metadata
    save_normalization_metadata(df, doc_type="-".join(df["doc_type"].unique()))

    # Identify numerical columns
    numerical_cols = df.select_dtypes(include=["number"]).columns

    # Normalize each numerical column
    for col in numerical_cols:
        normalized_col_name = f"normalized_{col}"
        df[normalized_col_name] = df.groupby("mission")[col].transform(clip_normalized_values)

    return df


def save_normalization_metadata(df: pd.DataFrame, doc_type: str):
    """Save upper boundaries for normalization."""
    if "mission" not in df.columns:
        raise ValueError("The DataFrame must contain a 'mission' column for grouping.")

    # Ensure directory exists
    os.makedirs("models/saved/norm", exist_ok=True)

    # Identify numerical columns
    numerical_cols = df.select_dtypes(include=["number"]).columns
    metadata = {}

    # Compute and save upper boundaries for each mission and column
    for mission, group in df.groupby("mission"):
        metadata[mission] = {}
        for col in numerical_cols:
            if group[col].empty:
                upper_boundary = 1.0
            else:
                q1, q3 = np.percentile(group[col].dropna(), [25, 75])
                iqr = q3 - q1
                upper_boundary = q3 + 1.5 * iqr if iqr > 0 else np.max(group[col].values)
                upper_boundary = 1.0 if np.isnan(upper_boundary) else upper_boundary
            metadata[mission][col] = upper_boundary

    with open(f"models/saved/norm/normalization_{doc_type}.pkl", "wb") as f:
        pickle.dump(metadata, f)

    logger.info(f"Saved normalization metadata for {doc_type}!")


def clip_values_inference(value: float, upper_boundary: float) -> float:
    """Normalize a single value using the upper boundary, capping at 1.0."""
    if np.isnan(value):
        return 0.0
    if np.isclose(value, 0.0):
        return value
    norm_value = value / upper_boundary if upper_boundary > 0 else 0.0
    norm_value = np.nan_to_num(norm_value, nan=0.0, posinf=1.0, neginf=0.0)
    return np.clip(norm_value, 0, 1)


def normalize_single_element(element: dict, doc_type: str) -> dict:
    """Normalize a single data element using saved metadata."""
    # Load metadata
    with open(f"models/saved/norm/normalization_{doc_type}.pkl", "rb") as f:
        metadata = pickle.load(f)

    mission = element.get("mission", "")
    if mission not in metadata:
        raise ValueError(f"Mission '{mission}' not found in normalization metadata.")

    normalized_element = element.copy()
    for col, upper_boundary in metadata[mission].items():
        if col in element:
            normalized_element[f"normalized_{col}"] = clip_values_inference(
                element[col], upper_boundary
            )

    return normalized_element


def determine_label(
    score, thresh_dict: tuple[float, float] = (0.33, 0.66)
) -> Literal["na", "standard", "influential", "benchmark"]:
    """Determines the label for a given score based on thresholds."""
    if len(thresh_dict) != 2 or not all(isinstance(th, (int, float)) for th in thresh_dict):
        raise ValueError("`thresh_dict` must be a tuple of two numerical values (low, high).")

    if pd.isna(score):
        return "na"
    elif score < thresh_dict[0]:
        return "standard"
    elif thresh_dict[0] <= score <= thresh_dict[1]:
        return "influential"
    else:
        return "benchmark"


def assign_label(df: pd.DataFrame, mode: str, label_mapping: dict = LABEL_MAPPING) -> pd.DataFrame:
    """Assigns labels to data based on a specific mode."""
    # Map modes to feature columns
    mode_feature_map = {
        "imrad": "normalized_cit_score_sum",
        "biblio": None,  # Not implemented
        "mixed": None,  # Not implemented
        "policy": "normalized_overton_score",
    }
    thresholds = (0.33, 0.66)

    feature_col = mode_feature_map.get(mode)
    if feature_col is None:
        raise NotImplementedError(f"Mode '{mode}' is not yet implemented.")

    if feature_col not in df.columns:
        raise ValueError(f"Feature column '{feature_col}' not found in the DataFrame.")

    # Apply label determination and mapping
    df["text_label"] = df[feature_col].apply(determine_label, thresh_dict=thresholds)
    df["label"] = df["text_label"].map(label_mapping)

    return df


def process_publisher(data: pd.Series) -> bool:
    """Check if the 'publisher' field in the data contains references to the European Space Agency (ESA)."""
    # Check if 'publisher' exists and is a string
    publisher = data.get("publisher", "")
    if not isinstance(publisher, str) or not publisher.strip():
        return False

    # Normalize and check
    publisher = publisher.lower().strip()
    return "esa" in publisher or "european space agency" in publisher


def update_json_files(
    df: pd.DataFrame,
    input_dir: str,
    method: Literal["imrad", "mixed", "biblio", "policy"],
    mode: Literal["training", "classification"],
):
    """Update JSON files with new data from the DataFrame."""
    required_columns = {"file_id", "text_label"}
    if not required_columns.issubset(df.columns):
        raise ValueError(f"The DataFrame must contain the following columns: {required_columns}")

    # Process each row in the DataFrame
    counter = 0
    for _, row in df.iterrows():
        file_id = row["file_id"]
        text_label = row["text_label"]
        file_path = os.path.join(input_dir, f"{file_id}.json")

        if not os.path.exists(file_path):
            logger.warning(f"File not found: {file_path}. Skipping.")
            continue

        try:
            # Read the JSON file
            with open(file_path, "r") as f:
                data = json.load(f)

            # Update JSON file with new information
            data[mode].append({"label": text_label, "method": method})

            # Write the updated JSON back to file
            with open(file_path, "w") as f:
                json.dump(data, f)
            counter += 1

        except json.JSONDecodeError:
            logger.error(f"Failed to parse JSON file: {file_path}. Skipping.")
        except Exception as e:
            logger.error(f"Unexpected error while processing file {file_path}: {e}")

    logger.info(f"Updated {counter} JSON metadata.")


def update_json_classification(json_path: str, event: dict):
    """Update a JSON file by appending a classification event to the 'classification' node."""
    if not os.path.exists(json_path):
        logger.error(f"JSON file not found: {json_path}")
        raise FileNotFoundError(f"JSON file not found: {json_path}")

    try:
        # Load JSON file
        with open(json_path, "r") as f:
            data = json.load(f)

        # Ensure the 'classification' node exists and is a list
        if "classification" not in data:
            logger.warning("'classification' key not found in JSON. Creating it.")
            data["classification"] = []
        elif not isinstance(data["classification"], list):
            logger.error("'classification' node must be a list.")
            raise ValueError("'classification' node must be a list")

        # Append the event
        data["classification"].append(event)

        # Save updated JSON
        with open(json_path, "w") as f:
            json.dump(data, f)

        logger.info(f"Classification event added to {json_path}")

    except json.JSONDecodeError as e:
        logger.error(f"Error decoding JSON file at {json_path}: {e}")
        raise ValueError(f"Invalid JSON format: {e}")
    except Exception as e:
        logger.error(f"Unexpected error while updating JSON: {e}")
        raise


def get_os_version() -> str:
    """Get the operating system version using the PRETTY_NAME from /etc/os-release."""
    os_release_file = "/etc/os-release"
    pretty_name = "Unknown OS"

    if os.path.exists(os_release_file):
        with open(os_release_file, "r") as file:
            for line in file:
                if line.startswith("PRETTY_NAME"):
                    pretty_name = line.strip().split("=")[1].strip('"')
                    break
    return pretty_name


def get_vars(start: Union[float, int], stop: Union[float, int]) -> dict:
    """Calculate system and run details."""
    if not isinstance(start, (float, int)) or not isinstance(stop, (float, int)):
        raise ValueError("Start and stop must be timestamps (float or int).")

    if stop < start:
        raise ValueError("Stop time must be greater than or equal to start time.")

    return {
        "total_disk_usage": shutil.disk_usage("/")[0],
        "device": "gpu" if torch.cuda.is_available() else "cpu",
        "start_time_date": datetime.fromtimestamp(start).strftime("%Y-%m-%dT%H:%M:%S"),
        "stop_time_date": datetime.fromtimestamp(stop).strftime("%Y-%m-%dT%H:%M:%S"),
        "elapsed_time": stop - start,
    }


def write_info_run(
    model_name: str,
    report: dict,
    config: dict,
    input_dir: str,
    method: Literal["imrad", "biblio", "mixed", "policy"],
    doc_type: Literal["academic", "policy"],
    start: float,
    stop: float,
    conf_matrix: np.ndarray,
    class_weights: List[float] = [],
):
    """Write information about a model run to a JSON file."""
    if not isinstance(report, dict):
        raise ValueError("Report must be a dictionary.")

    info = get_vars(start, stop)

    def format_metrics(metrics: dict) -> dict:
        return {key: round(value, 4) for key, value in metrics.items()}

    info_run = {
        "id": f"{model_name}_{int(stop)}",
        "start_time": info.get("start_time_date", ""),
        "stop_time": info.get("stop_time_date", ""),
        "elapsed_hours": round(info.get("elapsed_time", 0) / 3600, 4),
        "node": {
            "hostname": platform.node(),
            "os": get_os_version(),
            "ram_gb": int(psutil.virtual_memory().total / (1024**3)),
            "cores": psutil.cpu_count(logical=True),
            "type": info.get("device", ""),
            "storage_gb": int(info.get("total_disk_usage", 0) / (1024**3)),
        },
        "model": {
            "name": model_name,
            "url": "",
            "version": "",
            "type": model_name,
        },
        "dataset": {
            "name": os.path.basename(input_dir),
            "url": input_dir,
            "method": method,
            "doc_type": doc_type,
        },
        "params": {
            "algorithm": config.get("ALGORITHM", ""),
            "tokenizer": config.get("EMBEDDING_MODELS", ""),
            "batch_size": config.get("BATCH_SIZE", ""),
            "epochs": config.get("EPOCHS", ""),
            "optimizer": config.get("OPTIMIZER", ""),
            "learning_rate": config.get("LEARNING_RATE", ""),
            "loss": {"function": "", "weights": [round(weight, 4) for weight in class_weights]},
        },
        "classification_report": {
            **{
                str(key).replace(" ", "_").lower(): format_metrics(value)
                for key, value in report.items()
                if isinstance(value, dict)
            },
            "accuracy": round(report.get("accuracy"), 4) if report.get("accuracy") else "",
        },
        "confusion_matrix": conf_matrix.tolist(),
    }

    output_path = f"./models/saved/runs/{info_run.get('id', '')}.json"
    with open(output_path, "w") as f:
        json.dump(info_run, f, indent=4)

    logger.info(f"Info run saved to {output_path}")
