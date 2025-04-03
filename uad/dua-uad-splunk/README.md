# dua-uad-splunk

This single module contains the custom Splunk App that provides the UAD Service’s three interactive dashboards.

## DestinE Documents Repository

**Function:** Displays metadata of retrieved and processed documents.

**Filters:**

- Use Case (Earth Explorers, Copernicus, DestinE)
- Mission (e.g., Aeolus, GOCE, Sentinel-1, etc.)
- Document Type (academic, policy, ESA tech)
- Repository (source document repository)
- Classification (standard, influential, benchmark, NA, or "-")
- DOI
- Title

**Key Features:**

- Query result statistics (pie charts for classification, missions, repositories, journals)
- List of top 5 authors
- Grid-based document representation including metadata, classification labels, and tags


## DestinE Documents Statistics: Aggregates document statistics for analytical insights.

**Function:**

Gives the user insight about documents distributions from a geographical point of view.

**Filters:**

- Use Case
- Mission
- Document Type

**Key Features:**

- Count of total documents, affiliation countries, authors, and journals
- Map visualization with document distribution by country
- Top 10 authors list
- Pie chart for top 10 journals


## AI Assistant for Earth Observation

**Function:** Provides an AI Assistant interface for querying the knowledge base.

**Features:**

- Mission selection dropdown
- Interactive chat for mission-related inquiries
- Citation details including snippet, document relevance, and link to DestinE Documents Repository for further analysis
