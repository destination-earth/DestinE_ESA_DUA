from collections import defaultdict
import logging
import os
import time
from typing import Dict, Literal, Optional

import joblib
import numpy as np
import pandas as pd
from imblearn.over_sampling import SMOTE
from sklearn.ensemble import RandomForestClassifier, VotingClassifier
from sklearn.metrics import classification_report, confusion_matrix, f1_score
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.model_selection import RandomizedSearchCV, StratifiedKFold, train_test_split
from transformers import AutoModel, AutoTokenizer

from models.embeddings import generate_embeddings, generate_embeddings_parallel
from utils import utils
from utils.log import configure_logger

HF_MODELS_PATH = "./models/saved/"
os.environ["TOKENIZERS_PARALLELISM"] = "false"

configure_logger()
logger = logging.getLogger(__name__)


class BaseTrainer:
    def __init__(
        self,
        input_dir: str,
        method: Literal["imrad", "biblio", "mixed", "policy"],
        config: dict,
        hf_models_path: str = HF_MODELS_PATH,
    ):
        self.input_dir = input_dir
        self.method = method
        self.config = config
        self.hf_models_path = hf_models_path
        self.random_state = config.get("RANDOM_STATE", 42)
        self.test_size = config.get("TEST_SIZE", 0.2)

        self.smote = SMOTE(random_state=self.random_state)
        self.skf = StratifiedKFold(n_splits=5, shuffle=True, random_state=self.random_state)

        self.tokenizers, self.models = self._load_tokenizers_and_models()

    def _load_tokenizers_and_models(self) -> tuple[Dict[str, AutoTokenizer], Dict[str, AutoModel]]:
        """Load tokenizers and models from local paths."""
        tokenizers, models = {}, {}
        for model_name in self.config.get("EMBEDDING_MODELS", []):
            model_path = os.path.join(self.hf_models_path, model_name)
            try:
                tokenizers[model_name] = AutoTokenizer.from_pretrained(model_path)
                models[model_name] = AutoModel.from_pretrained(model_path)
            except Exception as e:
                logger.error(f"Error loading model {model_name} from {model_path}: {e}")
                raise RuntimeError(f"Failed to load model: {model_name}")
        return tokenizers, models

    def _apply_smote(self, x: np.ndarray, y: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
        """Apply SMOTE with class balance check."""
        if len(np.unique(y)) < 2:
            logger.warning("Skipping SMOTE due to a single class in dataset.")
            return x, y
        try:
            return self.smote.fit_resample(x, y)
        except ValueError as e:
            logger.error(f"SMOTE failed: {e}")
            raise RuntimeError("SMOTE application failed.") from e

    def _extract_text_column(self) -> str:
        """Determine text column based on method."""
        return "citation_context" if self.method == "imrad" else "abstract"

    def _evaluate(self, y_test: np.ndarray, y_pred: np.ndarray, classifier) -> dict:
        """Evaluate model performance and save the classifier."""
        f1 = f1_score(y_test, y_pred, average="weighted")
        logger.info(f"Test F1 Score: {f1:.3f}")

        conf_matrix = confusion_matrix(y_test, y_pred)
        report = classification_report(
            y_test, y_pred, target_names=["Standard", "Influential", "Benchmark"], output_dict=True
        )

        stop_time = int(time.time())
        model_path = f"./models/saved/runs/ensemble_{stop_time}.pkl"
        joblib.dump(classifier, model_path)
        logger.info("Model saved successfully!")

        return {"report": report, "conf_matrix": conf_matrix, "stop": stop_time}


class EnsembleTrainer(BaseTrainer):

    def train(self, x: np.ndarray, y: np.ndarray, param_dist: dict) -> RandomForestClassifier:
        """Train a Random Forest model."""
        classifier = RandomForestClassifier(random_state=self.random_state)
        random_search = RandomizedSearchCV(
            estimator=classifier,
            param_distributions=param_dist,
            n_iter=50,
            scoring="f1_weighted",
            cv=self.skf,
            n_jobs=-1,
            random_state=self.random_state,
            verbose=0,
        )
        random_search.fit(x, y)
        return random_search.best_estimator_

    def test(self, x_test: pd.DataFrame, classifier, feature_columns: list[str]) -> np.ndarray:
        """Test the trained model on unseen data."""
        logger.info("Testing model performances..")

        x_test_num = x_test[feature_columns]
        x_test_text = x_test["citation_context"].tolist()

        test_embeddings = np.hstack(
            [
                generate_embeddings(x_test_text, self.tokenizers[name], self.models[name])
                for name in self.models
            ]
        )

        x_test_combined = np.hstack((x_test_num, test_embeddings))
        return classifier.predict(x_test_combined)

    def run(self, df: pd.DataFrame):
        """Complete training pipeline."""
        param_dist = self.config.get("PARAM_DIST", {})
        if not param_dist:
            raise RuntimeError("No valid param distribution found in configuration.")

        start_time = time.time()
        feature_columns = [col for col in df.columns if col.startswith("normalized_")]
        text_column = self._extract_text_column()

        x = df[feature_columns + [text_column]]
        y = df["label"]

        # Split data
        x_train, x_test, y_train, y_test = train_test_split(
            x, y, test_size=self.test_size, stratify=y, random_state=self.random_state
        )

        # Generate embeddings for each model in parallel
        embeddings = generate_embeddings_parallel(
            x_train[text_column].tolist(), self.tokenizers, self.models
        )

        # Train individual models
        best_models = {}
        for model_name, model_embeddings in embeddings.items():
            x_train_combined = np.hstack((x_train[feature_columns], model_embeddings))
            x_train_resampled, y_train_resampled = self._apply_smote(x_train_combined, y_train)
            logger.info(f"Training {model_name} section..")
            best_models[model_name] = self.train(x_train_resampled, y_train_resampled, param_dist)

        # Train ensemble model
        combined_embeddings = np.hstack(list(embeddings.values()))
        x_train_combined = np.hstack((x_train[feature_columns], combined_embeddings))
        x_train_resampled, y_train_resampled = self._apply_smote(x_train_combined, y_train)
        ensemble_classifier = VotingClassifier(
            estimators=[(name, model) for name, model in best_models.items()], voting="soft"
        )
        logger.info("Training ensemble classifier..")
        ensemble_classifier.fit(x_train_resampled, y_train_resampled)

        # Test
        y_pred = self.test(x_test, ensemble_classifier, feature_columns)

        # Evaluate and save results
        results = self._evaluate(y_test, y_pred, ensemble_classifier)

        # Save run info
        utils.write_info_run(
            model_name="ensemble",
            **results,
            config=self.config,
            input_dir=self.input_dir,
            method=self.method,
            doc_type="-".join(df["doc_type"].unique()),
            start=start_time,
        )

    def inference(self, data: pd.Series, classifier) -> Optional[tuple[str, str]]:
        """Perform inference on a single data point using the provided classifier."""
        try:
            # Extract feature columns
            feature_columns = [col for col in data.keys() if str(col).startswith("normalized_")]
            if not feature_columns:
                raise ValueError("No normalized feature columns found in input data.")

            # Ensure "citation_context" exists
            if "citation_context" not in data:
                raise ValueError("Missing 'citation_context' in input data.")

            # Extract numerical and textual data
            data_num = data[feature_columns].to_numpy().reshape(1, -1)
            data_text = data["citation_context"]
            if not data_text:
                logger.warning("Document unclassified. Reason: citation-context unavailable.")
                return None

            # Generate embeddings for all models and concatenate
            test_embeddings = np.hstack(
                [
                    generate_embeddings([data_text], self.tokenizers[name], self.models[name])
                    for name in self.models
                ]
            )
            data_combined = np.hstack((data_num, test_embeddings))

            # Perform prediction
            y_pred = classifier.predict(data_combined)
            y_proba = classifier.predict_proba(data_combined)

            # Return the predicted label and its probability
            return str(int(y_pred[0])), str(round(y_proba[0][int(y_pred[0])], 2))

        except Exception as e:
            logger.error(f"Error during inference: {e}")

        return None


class PolicyTrainer(BaseTrainer):

    embedding_dir = "./models/saved/policy"

    def _load_embeddings(self):
        """Load all stored embeddings for cosine similarity comparison."""
        all_embeddings = defaultdict(dict)
        for mission in os.listdir(self.embedding_dir):
            mission_path = os.path.join(self.embedding_dir, mission)
            if os.path.isdir(mission_path):
                for file in os.listdir(mission_path):
                    label = file.replace(".npy", "")
                    file_path = os.path.join(mission_path, file)
                    all_embeddings[mission][label] = np.load(file_path)
        return all_embeddings

    def run(self, df: pd.DataFrame):
        """Training pipeline: group abstracts by mission and label, generate embeddings, and store them."""
        # Start timer
        start_time = time.time()

        # Extract features and labels
        feature_columns = [col for col in df.columns if col.startswith("normalized_")]
        text_column = self._extract_text_column()

        grouped = df.groupby(["mission", "text_label"])[text_column].apply(list)
        for (mission, label), abstract in grouped.items():
            embeddings_list = [
                generate_embeddings(abstract, self.tokenizers[name], self.models[name])
                for name in self.models
            ]
            mission_df = df[df["mission"] == mission]
            feature_matrix = mission_df[mission_df["text_label"] == label][feature_columns].values
            full_matrix = np.hstack([embeddings_list[0], feature_matrix])
            mission_dir = os.path.join(self.embedding_dir, mission)
            os.makedirs(mission_dir, exist_ok=True)
            file_path = os.path.join(mission_dir, f"{label}.npy")
            np.save(file_path, full_matrix)
            logger.info(f"Stored embeddings for {mission} - {label} at {file_path}")

        # Evaluate and save results
        results = {"report": {}, "conf_matrix": np.array([]), "stop": int(time.time())}

        # Save run info
        utils.write_info_run(
            model_name="cos-sim",
            **results,
            config=self.config,
            input_dir=self.input_dir,
            method=self.method,
            doc_type="-".join(df["doc_type"].unique()),
            start=start_time,
        )

    def inference(self, data: pd.Series, _classifier) -> Optional[tuple[str, str]]:
        """Infer the label for a new document by comparing it with stored embeddings."""
        feature_columns = [col for col in data.keys() if str(col).startswith("normalized_")]
        text_column = self._extract_text_column()
        text = data.get(text_column, "")
        mission = data.get("mission", "")

        # Generate embedding for the new document
        new_embeddings = [
            generate_embeddings([text], self.tokenizers[name], self.models[name])
            for name in self.models
        ]
        feature_matrix = data[feature_columns].to_numpy().reshape(1, -1)
        full_matrix = np.hstack([new_embeddings[0], feature_matrix])

        # Load stored embeddings for the specified mission
        all_embeddings = self._load_embeddings()
        if mission not in all_embeddings:
            raise ValueError(f"No embeddings found for mission: {mission}")

        # Compute cosine similarity with each label's embedding
        max_similarity = -1
        best_label = None
        for label, embeddings in all_embeddings[mission].items():
            similarities = cosine_similarity(full_matrix, embeddings)
            avg_similarity = np.mean(similarities)
            logger.info(
                f"Avg Cosine Similarity with {mission} - {label}: {round(avg_similarity, 2)}"
            )
            if avg_similarity > max_similarity:
                max_similarity = avg_similarity
                best_label = label

        logger.info(f"Assigned label: {best_label} (Similarity: {round(max_similarity, 2)})")
        # Map predictions to labels
        labels = {"standard": "0", "influential": "1", "benchmark": "2"}
        return labels.get(best_label, ""), str(round(max_similarity, 2))
