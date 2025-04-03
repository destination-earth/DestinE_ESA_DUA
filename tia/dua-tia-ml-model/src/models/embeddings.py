import logging
from concurrent.futures import ThreadPoolExecutor
from typing import List

import numpy as np
import torch
from torch.utils.data import DataLoader, Dataset
from transformers import PreTrainedModel, PreTrainedTokenizer

from utils.log import configure_logger

configure_logger()
logger = logging.getLogger(__name__)


def generate_embeddings(
    texts: List[str],
    tokenizer: PreTrainedTokenizer,
    model: PreTrainedModel,
    batch_size: int = 16,
    max_len: int = 512,
):
    """Generate embeddings for a list of texts using a pre-trained model."""
    model.eval()  # Set the model to evaluation mode
    embeddings = []

    # Dataset for tekenizing text
    class TextDataset(Dataset):
        def __init__(self, texts: List[str]):
            self.texts = texts

        def __len__(self):
            return len(self.texts)

        def __getitem__(self, idx: int) -> str:
            return self.texts[idx]

    # DataLoader for batching
    text_dataset = TextDataset(texts)
    text_loader = DataLoader(text_dataset, batch_size=batch_size, num_workers=0, shuffle=False)

    # Process text batches
    for batch_texts in text_loader:
        # Tokenize the batch of texts
        inputs = tokenizer.batch_encode_plus(
            batch_texts,
            add_special_tokens=True,
            max_length=max_len,
            padding="longest",
            truncation=True,
            return_tensors="pt",
        )

        # Move tensors to the model's device
        input_ids = inputs["input_ids"].to(model.device)
        attention_mask = inputs["attention_mask"].to(model.device)

        with torch.no_grad():
            outputs = model(input_ids=input_ids, attention_mask=attention_mask)
            # Extract [CLS] token embedding
            cls_embeddings = outputs.last_hidden_state[:, 0, :].cpu().numpy()
            embeddings.append(cls_embeddings)

    return np.vstack(embeddings)  # Concatenate all embeddings


def generate_embeddings_parallel(
    texts: List[str], tokenizers: PreTrainedTokenizer, models: PreTrainedModel
):
    """Generate embeddings for multiple models in parallel."""

    def generate_single_model_embeddings(model_name: str):
        logger.info(f"Generating {model_name} embeddings..")
        tokenizer = tokenizers[model_name]
        model = models[model_name]
        return model_name, generate_embeddings(texts, tokenizer, model)

    embeddings = {}
    with ThreadPoolExecutor(max_workers=len(models)) as executor:
        futures = {
            executor.submit(generate_single_model_embeddings, model_name): model_name
            for model_name in models
        }
        for future in futures:
            try:
                model_name, model_embeddings = future.result()
                embeddings[model_name] = model_embeddings
            except Exception as e:
                logger.error(f"Error generating embedding for model {futures[future]}: {e}")
                raise RuntimeError(f"Embedding generation failed for {futures[future]}.")
    return embeddings
