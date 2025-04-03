import logging
from pathlib import Path
from typing import Optional

from adapters import AutoAdapterModel
from transformers import AutoModel, AutoTokenizer

from utils.log import configure_logger

configure_logger()
logger = logging.getLogger(__name__)


class ModelHandler:
    """Handles loading and saving of models and tokenizers."""

    def __init__(self, model_name: str, save_path: Path, adapter_name: Optional[str] = None):
        self.model_name = model_name
        self.save_path = save_path
        self.adapter_name = adapter_name

    def save_model_and_tokenizer(self):
        """Load, optionally configure, and save the model and tokenizer."""
        # Load tokenizer and model
        tokenizer = AutoTokenizer.from_pretrained(self.model_name)

        if self.adapter_name:
            model = AutoAdapterModel.from_pretrained(self.model_name)
            model.load_adapter(self.adapter_name, set_active=True)
        else:
            model = AutoModel.from_pretrained(self.model_name)
            # Ensure tensors are contiguous (not strictly necessary in most cases but added to prevent errors)
            for param in model.parameters():
                if not param.is_contiguous():
                    param.data = param.data.contiguous()

        # Save tokenizer and model
        tokenizer.save_pretrained(self.save_path)
        model.save_pretrained(self.save_path)


def save_all_models_and_tokenizers():
    """Save multiple models and tokenizers."""
    # Define model configurations
    model_configs = [
        {
            "model_name": "allenai/scibert_scivocab_uncased",
            "save_path": Path("./models/saved/scibert"),
        },
        {
            "model_name": "allenai/specter2_base",
            "save_path": Path("./models/saved/specter2"),
            "adapter_name": "allenai/specter2",
        },
        {"model_name": "adsabs/astroBERT", "save_path": Path("./models/saved/astrobert")},
    ]

    # Process each model configuration
    for config in model_configs:
        handler = ModelHandler(**config)
        handler.save_model_and_tokenizer()

    logger.info("All models and tokenizers have been successfully saved!")


if __name__ == "__main__":
    save_all_models_and_tokenizers()
