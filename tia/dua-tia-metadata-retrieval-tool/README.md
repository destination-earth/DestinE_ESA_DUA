# dua-tia-metadata-retrieval-tool

## Overview

The **Metadata Retrieval Tool** collects metadata for an article from various document repositories and produces structured outputs for **machine learning** (**ML**) model training and the **UAD** service. It is designed to extract, process, and structure metadata from different document types, including parsed PDFs and external metadata sources.

#### Supported Document Types

* **Academic**: Scientific literature from repositories such as Scopus, Nature, OpenAlex, Semantic Scholar, Core, etc.
* **Policy**: Policy-related documents from sources like Overton, JRC, etc.
* **ESA Public**: ESA mission technical documents available online.

#### Additional Metadata Sources

* [**Unpaywall**](https://unpaywall.org/): Open database of 20 million free scholarly articles.
* [**CrossRef**](https://www.crossref.org/): Infrastructure linking research objects, entities, and actions.
* [**OpenCitations**](https://opencitations.net/):  Infrastructure dedicated to open bibliographic and citation data.

#### Extracted Metadata

Below is a list of extracted metadata fields, their types, descriptions, and sources (in priority order):

| Metadata              | Type           | Description                                                                                                                                                                                                                                              | Source                               |
| --------------------- | -------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------ |
| File ID               | `str`        | Unique file identifier within the DUA service.                                                                                                                                                                                                          | Input                                |
| Document Type         | `str`        | Type of document:*academic*, *policy* or *ESA Public*.                                                                                                                                                                                             | Document metadata                    |
| Repository Name       | `str`        | Name of the source repository.                                                                                                                                                                                                                           | Input                                |
| Article ID            | `str`        | Repository-specific article identifier.                                                                                                                                                                                                                  | Input                                |
| Initiative            | `str`        | Name of the associated ESA initiative.                                                                                                                                                                                                                   | Document metadata                    |
| Mission               | `str`        | Name of the associated ESA mission.                                                                                                                                                                                                                      | Document metadata                    |
| Keyword Dictionary ID | `str`        | ID of the keyword dictionary used to retrieve the document.                                                                                                                                                                                              | Document metadata                    |
| DOI                   | `str`        | Digital Object Identifier (DOI) of the article.                                                                                                                                                                                                          | GROBID, Document metadata            |
| Title                 | `str`        | Title of the article.                                                                                                                                                                                                                                    | Document metadata, GROBID            |
| Publisher             | `str`        | Publisher of the article.                                                                                                                                                                                                                                | Unpaywall                            |
| Funders               | `list`       | Funders of the article.                                                                                                                                                                                                                                  | GROBID                               |
| Authors               | `list[dict]` | List of authors, including name, affiliation, and country.                                                                                                                                                                                               | Unpaywall, GROBID, Document metadata |
| Author names          | `list`       | List of the authors of the article.                                                                                                                                                                                                                      | Unpaywall, GROBID, Document metadata |
| Affiliations          | `list`       | List of the institutions affiliated with the article.                                                                                                                                                                                                    | GROBID                               |
| Countries             | `list`       | List of the affiliations' countries.                                                                                                                                                                                                                     | Unpaywall, GROBID                    |
| Genre                 | `str`        | Genre of the article.                                                                                                                                                                                                                                    | Unpaywall                            |
| Journal name          | `str`        | Name of the journal where the article was published.                                                                                                                                                                                                    | Unpaywall                            |
| Language              | `str`        | Detected language of the article's full text.                                                                                                                                                                                                            | `langdetect`                       |
| Published             | `str`        | Date when the article was published.                                                                                                                                                                                                                     | Document metadata, GROBID            |
| is OA                 | `bool`       | Flag indicating whether the article is open-access.                                                                                                                                                                                                     | Unpaywall                            |
| OA status             | `str`        | OA status of the article assigned by Unpaywall: closed, green, gold, hybrid, and bronze.                                                                                                                                                                | Unpaywall                            |
| License               | `str`        | License distribution of the article.                                                                                                                                                                                                                     | Unpaywall                            |
| TF-IDF score          | `float`      | TF-IDF score of the mission name within the article's full text.                                                                                                                                                                                         | `sklearn.TfidfVectorizer`          |
| Citations             | `int`        | Number of citations obtained by the article (highest value from all sources).                                                                                                                                                                            | Unpaywall, CrossRef, OpenCitations   |
| JIF                   | `float`      | The [Journal Impact Factor](https://clarivate.com/academia-government/scientific-and-academic-research/research-funding-analytics/journal-citation-reports/publishers/first-time-publishers/) is a measure of journal influence based on citation metrics. | Document metadata                    |
| Abstract              | `str`        | Article abstract (or the first 400 characters of the full text if unavailable).                                                                                                                                                                          | Document metadata, GROBID            |
| Full text             | `str`        | Full article text (for academic articles only available if **CC-BY** licensed)                                                                                                                                                                      | GROBID, Tika                         |
| Overton scre          | `float`      | Relevance score assigned by Overton (for policy articles).                                                                                                                                                                                               | Document metadata                    |
| Reference             | `list[dict]` | List of references cited in the article.                                                                                                                                                                                                                 | CrossRef                             |
| Citings               | `list`       | List of articles that cite this article.                                                                                                                                                                                                                 | OpenCitations                        |
| Thumbnail             | `str`        | Name of the thumbnail to be displayed.                                                                                                                                                                                                                   | Input                                |

#### Criteria for Valid Articles

An article is considered valid only if has specific characteristics:

* **English** language: articles not in English are excluded.
* **Journal Article** genre: other genres (books, chapters, conference proceedings, ecc.) are not supported by the DUA service (only for academic articles).
* **Publication Year** valid for the mission: academic articles published before the related mission launch year are excluded, as the service focuses on evaluating ESA mission contributions.

#### File ID Naming Convention

The unique file identifier (**File ID**) must follow this naming pattern:

`<repo>-<mission>-<article_id>`

For example, `jrc-Aeolus-JRC131651` refers to the document *[The European Commission Atmospheric Observatory](https://dx.doi.org/10.2760/145714)* from the JRC repository.

##### Expected Files in each input folder

The input folder name must follow the File ID naming convention. The expected files inside are:

* **PDF** file of the document (to be parsed by GROBID and/or TIKA).
* `<repo>-<mission>-<article_id>.xml`: output from GROBID.
* `<repo>-<mission>-<article_id>-tika.json`: output from Tika.
* `document-metadata.json`: metadata generated by the dua-tia-document-retriever with informations derived mainly from the repository itself.
* `unpaywall-metadata.json`: metadata from Unpaywall.
* `cross-ref-metadata.json`: metadata from CrossRef.
* `open-citations-metadata.json`: metadata from OpenCitations.
* **JPEG** thumbnail image of the document.
* `thumbnail.json`: file used to generate the document thumbnail.

## Installation

Ensure **Python 3.8+** is installed. It is recommended to use a virtual environment:

```
python -m venv venv
source venv/bin/activate

pip install -r src/requirements.txt
```

## Usage

Before running the metadata extraction tool, parse PDFs using GROBID and Tika with the `dua-tia-metadata-retrieval-tool.py` script inside the `bin/` folder to produce their respective outputs. Once preprocessing is complete, run metadata extraction tool using as input a directory with the before specified characteristics:

```
python src/bin/metadata.py <input_dir> -o <output_dir>
```

#### Optional arguments

* `--create-csv`: generates a summary CSV file.
* `--output-csv <path>`: specifies the directory to save the CSV file.
* `--extract-all`: to use if the input is a folder containing many subfolders for many documents

## Output format

An example of a JSON output can be found at `src/output`.
