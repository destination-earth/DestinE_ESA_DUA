# dua-tia-ml-model

## Overview

This is the **Machine Learning** (**ML**) model component of the **Traceability and Impact Analysis** (**TIA**) of **DestinE Usage Assessment** framework service (**DUA**). TIA is a multi-level traceability operation, powered by automated and powerful ML models based on Natural Language Processing methods. This pipeline is designed to classify items according to the impact that ESA missions and initiatives had on them into three classes, in order of relevance:

* **Standard** (low impact)
* **Influential** (medium impact)
* **Benchmark** (high impact)

The system leverages text embeddings, similarity measurements, and ensemble models to assign relevance scores and predict classifications. It supports different training methodologies and model configurations, allowing flexibility in processing various types of metadata. The pipeline consists of two main components:

* **Training** (`train.py`): generates a training dataset from structured metadata and trains a classification model.
* **Inference** (`inference.py`): uses a trained model to classify new documents based on extracted metadata.

### Project structure

```
dua-tia-ml-model/src/
|-- bin/
|   |-- __init__.py
|   |-- inference.py	# Script to run inference
|   |-- train.py	# Script to train models
|-- config/		# Configuration files for models and keywords
|-- core/		# Data processing and dataset generation
|-- models/		# Model definitions, embeddings, and saving utilities
|-- utils/		# Utility scripts for logging and additional functions
|-- requirements.txt
|-- setup.py
```

### Installation

This component has been made using **Python 3.9.18**. It is recommended to use a virtual environment.

```
python -m venv venv
source venv/bin/activate

pip install -r src/requirements.txt
```

## Training mode

The training script processes document metadata, generates a dataset, and trains a classification model. The input to the training script must be a folder containing document metadata in JSON format, which is the output of the `dua-tia-metadata-retrieval-tool`. This data can be processed using two different training techniques:

* **IMRaD**: designed for academic documents, this mode takes into account the standard [Introduction, Methods, Results, and Discussion structure of scientific articles](https://en.wikipedia.org/wiki/IMRAD).
* **Policy**: designed for policy documents, this mode categorize documents based on their relevance.

To support these training modes, two distinct classification models have been implemented:

* **Ensemble model** (for academic documents): combines three different text embeddings, uses multiple **Random Forest** classifiers, and a **Voting Classifier** makes the final prediction based on the outputs of the individual classifiers.
* **Cosine Similarity model** (for policy documents): classifies documents based on scores obtained from *Overton* queries, documents are divided into three groups based on their computed similarity scores.

### Basic usage

```
python ./bin/train.py <input_dir> -o <output_dir> -c <config_file>
```

### Arguments

| Argument             | Type     | Default        | Description                                                       |
| -------------------- | -------- | -------------- | ----------------------------------------------------------------- |
| `input_dir`        | `str`  | -              | Directory containing JSON document metadata.                      |
| `-o, --output-dir` | `str`  | `./output`   | Directory to save the output files.                               |
| `-m, --method`     | `str`  | `"imrad"`    | Training method:`imrad`, `policy`.                            |
| `-n, --name`       | `str`  | `None`       | Custom name for the training run. Defaults to `input_dir` name. |
| `-c, --config`     | `str`  | -              | Path to the YAML configuration file.                              |
| `--model`          | `str`  | `"ensemble"` | Model type:`ensemble`, `cos-sim`.                             |
| `--no-csv`         | `flag` | `False`      | If set, does not create a dataset CSV file.                       |
| `--dry-run`        | `flag` | `False`      | If set, only prepares the dataset without training.               |

### Training dataset creation

The process begins by reading metadata JSON files from a specified directory. Each file contains metadata describing a scientific document. Only relevant fields are included in the process: file ID, repository name, initiative, mission, document type, DOI, title, publisher, abstract, TF-IDF score, citations, JIF, Overton score, and full text. See documentation of `dua-tia-metadata-retrieval-tool` for more information about these fields. Then, each document is processed according to a specific method. For academic documents, the available method is `imrad` which uses the **IMRaD** (Introduction, Methods, Results, and Discussion) structure to extract information. There is the possibility to implement, in the future, two more methods: `biblio`, which processes bibliometric information; and `mixed` which balances these last two methods' outputs. For policy documents, the available method is called `policy`.

#### **IMRaD** method

For the IMRaD method, the class `IMRaDDataGenerator` is used to process the document. Its key steps include:

1. **Keyword Extraction**: keywords specific to a mission are loaded from a YAML configuration file. These are organized into a hierarchical structure (Level 1, Level 2, Level 3, and Level 4).
2. **Sentence Extraction**: the full text is split into paragraphs. Sentences containing mission-related keywords are identified. Each sentence is assigned to an IMRaD section (Introduction, Methods, Results, and Discussion) based on section headers or, if unavailable, the sentence's position in the document. Keyword occurences are counted at different hierarchical levels.
3. **Scores Computation**: for each sentence with Level 1, Level 2, or Level 3 keyword are computed the following scores:

   * `Keyword Score`: a weighted sum of keyword occurences at different levels.
   * `Citation Score`: keyword score adjusted based on IMRaD section weights.

   Then, scores are aggregated at document level, computing total, mean, and maximum values.
4. **Citation Context Formation**: all sentences containing keywords are concatenated to form a citation context. Later, it will be transformed into vector embeddings for use in ML models.
5. **Keyword Count in Title and Abstract**: keywords are counted separately within the document title and abstract.

After processing all documents, the data is structured into a Pandas DataFrame and undergoes further transformation:

* Removal of documents with no *citation context*.
* Removal of duplicate documents based on *mission* and *DOI*, following a repository priority.
* Normalization of numerical columns within each *mission* group, capping outliers at 1.0.

Documents are labeled into the three categories based on their normalized **citation score**.

* **Standard**: `cit_score` $<$ 0.33
* **Influential**: 0.33 $\le$ `cit_score` $\le$ 0.66
* **Benchmark**: `cit_score` $>$ 0.66

The final output dataset is a structured DataFrame that includes: raw metadata fields, extracted citation context, computed keyword and citation scores, keyword count in title and abstract, and assigned classification labels. This structured dataset is then used for training ML model to classify documents based on mission relevance.

#### **Policy** method

For the policy method, the class `PolicyDataGenerator` is used to process the document. Its only step is to count keywords within the document title and abstract. Then, the data is structured into a Pandas DataFrame and undergoes the following preprocessing steps:

* Removal of documents with no detected *abstract*.
* Normalization of numerical columns within each *mission* group, capping outliers at 1.0.

Documents are labeled into the three categories based on their normalized **Overton score**.

* **Standard**: `overton_score` $<$ 0.33
* **Influential**: 0.33 $\le$ `overton_score` $\le$ 0.66
* **Benchmark**: `overton_score` $>$ 0.66

The final output dataset is a structured DataFrame that includes: raw metadata fields, abstract, overton score, keyword count in title and abstract, and assigned classification labels. This structured dataset is then used for training ML model to classify documents based on mission relevance.

#### Configuration

The training dataset creation relies on a YAML configuration file (`src/config/conf.yml`) to guide the labeling and weighting of documetn sections, keywords, and data processing priorities. Below is a detailed breakdown of its components. To ensure reusability, maintain the configuration structure and verify that all required files are present.

##### `IMRaD.lookup_table`

This section maps various section headers in academic documents to their corresponfing IMRaD categories:

- **Introduction**: it is recognized by terms like "Introduction", "Background", and "Related Work".
- **Methods**: identified using "Method", "Model", and "Approach".
- **Results**: includes terms such as "Result", "Experience", and "Evaluation". Additionally, "unknown" headers and figures description are mapped to this section.
- **Discussion**: mapped to "Discussion", "Conclusion", and "Future"

##### `IMRaD.weights`

Each IMRaD section is assigned a weight to influence the computation of **citation scores**.

| Section      | Weight |
| ------------ | ------ |
| Introduction | 0.2    |
| Methods      | 0.4    |
| Results      | 0.1    |
| Discussion   | 0.3    |

These weights determine how keyword occurences contribute to the overall citation score, with the Methods and Discussion sections being the most influential.

##### `keyword_weights`

This section defines the relative importance of keywords appearing at different hierarchical levels:

| Keyword Level    | Weight |
| ---------------- | ------ |
| Level 1 Keywords | 0.4    |
| Level 2 Keywords | 0.3    |
| Level 3 Keywords | 0.2    |
| Level 4 Keywords | 0.1    |

Higher-level keywords (e.g., Level 1) contribute more to the final keyword score than lower-level ones.

Keyword configuration files are located in `src/config/`. Typically:

* *Level 1* represents *initiative* name.
* *Level 2* corresponds to the *mission* name.
* *Level 3* and *Level 4* contain additional keywords structured hierarchically.

##### `mission_aliases`

Some mission names have alternative forms in documents. This mapping ensures consistency:

- "ESA Swarm" is treated as "Swarm".
- "CryoSat-2" is treated as "CryoSat".

##### `ac_repo_priority`

Repositories storing academic documents are prioritized for deduplication, with lower values indicating higher priority and importance:

| Priority | Repository       |
| -------- | ---------------- |
| 1        | Scopus           |
| 2        | Nature           |
| 3        | OpenAlex         |
| 4        | Semantic Scholar |
| 5        | Core             |
| 6        | JRC              |

When duplicates exist, documents from higher-priority repositories (e.g., Scopus) are preferred over lower-ranked sources.

#### Outputs

At the end of the dataset creation pipeline, the following outputs are produced:

* **Training dataset (CSV)**: a CSV file containing the processed training dataset. An example can be found in `src/output/`.
* **Normalization Metadata**: upper boundaries for each *mission* group are saved in `.pkl` format to ensure a correct normalization during inference. These are stored in `src/models/saved/norm/`.

### ML models for classification

This module provides ML models for document classification, leveraging transformer-based embeddings and ensemble learning. Two primary classification approaches are implemented:

1. **Ensemble Model Classification** - Uses multiple transformer-based embeddings and a Random Forest classifier to categorize academic documents following the *IMRaD* structure.
2. **Cosine Similarity-Based Classification** - Precomputes and stores embeddings for policy documents and classifies new documents based on cosine similarity.

Each model has a structured training pipeline, including data preprocessing, embedding generation, model training, and evalution. Configuration settings are managed via YAML files to ensure reproducibility and flexibility.

#### Model download and preparation

Before training, the necessary models must be downloaded using the `save_models.py` script. This script retrieves transformer-based models from [Hugging Face](https://huggingface.co/), and saves them locally for embedding generation. The script performs the following tasks:

* Loads the tokenizer and model for each specified embedding model.
* If applicable, loads an adapter for the model and activates it.
* Ensures model parameters' tensors are contiguous in memory to prevent errors.
* Saves both the tokenizer and model to a predefined local directory.

In particular, it downloads and saves the following models:

1. **SciBERT** (`allenai/scibert_scivocab_uncased`): a BERT-based model trained on scientific texts, optimized for domain-specific language in academic literature.
2. **Specter2** (`allenai/specter2_base` with adapter `allenai/specter2`): a transformer-based model designed for document-level embeddings, effective for scientific paper similarity tasks.
3. **AstroBERT** (`adsabs/astroBERT`): a BERT model fine-tuned for astrophysics literature, improving text representation for astronomy-related research papers.

To download and save the models, run:

```
python ./models/save_models.py
```

After execution, the models and tokenizers will be stored in:

* `./models/saved/scibert`
* `./models/saved/specter2`
* `./models/saved/astrobert`

If models are already downloaded, re-running the script will overwrite existing files.

#### Ensemble Model Classification

An **ensemble model** is used for classifying academic documents structured according to the *IMRaD* method. The model utilizes embeddings for multiple transformer-based models and applies a **Random Forest** classifier to predict the document category. The training pipeline includes:

1. **Data Loading and Preprocessing**:
   * The dataset is loaded, and relevant feature are extracted.
   * Transformer-based tokenizers and models are initialized.
   * **SMOTE** *(Synthetic Minority Over-sampling Technique)* is applied to balance class distribution.
   * The dataset is split into training and testing sets.
2. **Text Embedding Generation**:
   * Input text is tokenized and transformed into dense vector representations using: **SciBERT**, **Specter2**, and **AstroBERT**.
   * Embeddings from each model are stacked as features for classification.
3. **Model Training**:
   * **Randomized Search Cross-Validation** is applied to optimize **Random Forest** hyperparameters:

     * `n_estimators`: number of trees in the forest.
     * `max_depth`: maximum tree depth.
     * `min_samples_split`: minimum samples to split an internal node.
     * `min_samples_leaf`: minimum samples for a leaf node.
     * `max_features`: number of features considered for splitting.
     * `bootstrap`: whether bootstrap sampling is used.
   * Individual models are trained separately for each embedding type.
   * The final **ensemble model** aggregates outputs using a **soft voting** classifier.
4. **Testing and Evaluation**:
   * The trained ensemble classifier is tested on a separate dataset.
   * Evaluation metrics include **weighted F1-score** and a confusion matrix.
   * The trained ensemble model is saved for future inference.

The training configuration is managed through `src/config/ensemble.yml`, specifying parameters such as:

* Dataset split ratio
* Random state for reproducibility
* Classifier algorithm and its hyperparameter space
* List of embedding models to use

For consistency, use this structure when testing different classifiers, hyperparameters, or embedding models.

##### Outputs

After training, the following outputs are stored in `./models/saved/runs/`:

* A `.pkl` file containing the saved model for inference.
* A `JSON` report detailing: training time, host system information, model and dataset details, training parameters, classification metrics, and confusion matrix.

#### Cosine Similarity-Based Classification

This model is designed for **policy documents** using a similarity based approach. Instead of training a classifier, it **precomputes and stores embeddings**, enabling fast classification via cosine similarity. The training pipeline includes:

1. **Feature extraction**: Extracts numerical feature columns and the relevant text column (`abstract` for policy documents).
2. **Grouping documents**: Documents are grouped by *mission* and *label*.
3. **Embedding Generation**: **SciBERT embeddings** are generated for each document.
4. **Feature Matrix Construction**: Embeddings are combined with numerical feature vectors.
5. **Storage of precomputed embeddings**: Processed embeddings are saved as `.npy` files for fast retrieval. Files are structured under mission-specific directories (`./models/saved/policy/<mission_name>/`).

The training configuration is managed through `src/config/cos-sim.yml` which defines: model name, similarity algorithm, and embedding model used. For consistency, use this structure when testing different similarity algorithms or embedding models.

##### Output

After training, a `JSON` report is stored in `./models/saved/runs/`, containing training time, host system information, model and dataset details.

## Inference mode

The inference script classifies a new document based on a pre-trained model. The script takes as input a `JSON` file containing document metadata, which is typically generated by the `dua-tia-metadata-retrieval-tool`. The classification method is determined based on the document type:

* **Ensemble model**: Used for academic documents classified under the *IMRaD* structure.
* **Cosine Similarity model**: Used for policy documents classified based on similarity scores.

### Basic usage

```
python ./bin/inference.py <input_json> -c <config_file>
```

### Arguments

| Argument         | Type    | Default | Description                                             |
| ---------------- | ------- | ------- | ------------------------------------------------------- |
| `input_json`   | `str` | -       | Path to the `JSON` file containing document metadata. |
| `-c, --config` | `str` | -       | Path to the `YAML` configuration file.                |

### Inference process

1. **Document Type Identification**

   * The script identifies whether the document is *academic* or *policy* based on its metadata.
   * If the document type is unrecognized, inference is stopped.
2. **Load Model Configuration**

   * The appropriate model (`ensemble` for academic, `cos-sim` for policy) is determined based on the document type.
   * For a valid trained model ID, the script retrieves the last trained model from `./models/saved/runs/`.
   * The `YAML` configuration for the selected model is loaded.
3. **Metadata Processing**

   * Metadata is processed to extract relevant features required for classification.
   * If no valid inference data is produced, the document is labeled as **na**.
4. **Publisher Classification** (specific to academic documents)

   * If the document was published by ESA, it is automatically classified as **influential**.
5. **Model Execution**

   * The script loads the appropriate classifier and runs inference using one of the following approaches.

#### Ensemble Classifier (IMRaD Mode)

It begins by extracting numerical featuers from the document metadata, ensuring they are correctly formatted for processing. It then verifies the presence of a *citation context*, which is essential for classification. Once validated, text embeddings are generated using the three transformer models. These embeddings are combined with the extracted numerical features to from a comprehensive feature vector. This vector is then passed to a **pre-trained Random Forest ensemble classifier**, which predicts the document's classification label. The assigned label, along with its confidence score, is stored in the `JSON` file. 

#### Cosine Similarity Classifier (Policy Mode)

It first identifies the mission associated with the new document and creates embeddings using *SciBERT* for the *abstract* text column. Next, it loads stored embeddings corresponding to the mission and computes **cosine similarity** between the new document's embedding and those of each class. The class with the highest similarity score is assigned as the predicted label. The final classification label and its similarity score are then recorded in the `JSON` file. 

6. **Updating JSON output**
   * The classification result along with the model details and confidence score, is written back to the input `JSON` file.
   * The classification node includes:

   | Key | Description |
   | ---- | ---- |
   | Label | The predicted label (*standard*, *influential*, or *benchmark*). |
   | Model | The model used (*ensemble* or *cos-sim*). |
   | Algorithm | The algorithm name used (*Random Forest* or *Cosine Similarity*). |
   | Training ID | The ID assigned to the training run. |
   | Confidence | The **confidence score** (for ensemble) or **similarity score** (for cosine similarity). |
