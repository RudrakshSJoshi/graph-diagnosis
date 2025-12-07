"""
utils.py

Small utility module providing a simple file-backed memory abstraction for two memory "forms":
 - "list" : used to store lists like possible diagnoses, short items, etc.
 - "chat" : used to store chat flow as a flat list of alternating user/bot messages:
            [user_msg1, bot_msg1, user_msg2, bot_msg2, ...]

Primary function exported to use:
    process_memory(form: str, type: str, content: Optional[list[str]] = None)
... (Memory management functions remain the same)
"""

import json
import os
import google.generativeai as genai # <-- Changed from Groq
from typing import List, Optional, Union, Dict, Any

DATA_DIR = os.path.join(os.path.dirname(__file__), "data")
os.makedirs(DATA_DIR, exist_ok=True)

_LIST_FILE = os.path.join(DATA_DIR, "memory_list.json")
_CHAT_FILE = os.path.join(DATA_DIR, "memory_chat.json")


# --- Internal helpers ------------------------------------------------------
def _get_filepath(form: str) -> str:
    if form == "list":
        return _LIST_FILE
    elif form == "chat":
        return _CHAT_FILE
    else:
        raise ValueError("form must be 'list' or 'chat'.")


def _read_memory(form: str) -> List[str]:
    path = _get_filepath(form)
    if not os.path.exists(path):
        return []
    with open(path, "r", encoding="utf-8") as fh:
        try:
            data = json.load(fh)
            if not isinstance(data, list):
                return []
            return [str(x) for x in data]
        except json.JSONDecodeError:
            return []


def _write_memory(form: str, items: List[str]) -> None:
    path = _get_filepath(form)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(items, fh, indent=2, ensure_ascii=False)


# --- Public API ------------------------------------------------------------
def process_memory(form: str, type: str, content: Optional[List[str]] = None) -> Union[List[str], bool]:
    """
    Manage the file-backed memory.
    """
    form = str(form)
    type = str(type).lower()

    if form not in ("list", "chat"):
        raise ValueError("Invalid form. Must be 'list' or 'chat'.")

    if type not in ("append", "update", "fetch"):
        raise ValueError("Invalid type. Must be 'append', 'update', or 'fetch'.")

    if type == "fetch":
        return _read_memory(form)

    if type == "append":
        if not isinstance(content, list):
            raise ValueError("For append, content must be a list of strings.")
        content = [str(x) for x in content]
        existing = _read_memory(form)
        new = existing + content
        _write_memory(form, new)
        return True

    if type == "update":
        if content is None:
            content_items = []
        else:
            if not isinstance(content, list):
                raise ValueError("For update, content must be a list of strings or None (to reset).")
            content_items = [str(x) for x in content]
        _write_memory(form, content_items)
        return True


# Convenience helpers (optional)
def fetch_memory(form: str) -> List[str]:
    return process_memory(form=form, type="fetch")


def append_memory(form: str, items: List[str]) -> bool:
    return process_memory(form=form, type="append", content=items)


def update_memory(form: str, items: Optional[List[str]]) -> bool:
    return process_memory(form=form, type="update", content=items)

# --- Public API for LLM invocation ----------------------------------------------
# We configure the API key globally here
# Make sure to set the GEMINI_API_KEY environment variable!
genai.configure(api_key=os.getenv("GEMINI_API_KEY")) 

def invoke_llm(
    system_prompt: str = "",
    user_prompt: str = "",
    model_id: str = "gemini-2.5-pro", # Changed default model to Gemini
    temperature: float = 0.5,
    max_completion_tokens: int = 2048,
    top_p: float = 1.0,
    structured_schema: Optional[Dict[str, Any]] = None, # Structured outputs via response_mime_type/response_schema
) -> str:
    """
    Execute a Gemini LLM completion request with optional structured output (JSON mode).
    This function performs a non-streaming call and returns the final completion text 
    (or structured JSON as text).
    """

    if user_prompt.strip() == "":
        raise ValueError("user_prompt must be a non-empty string.")

    # 1. Prepare Configuration (generation_config)
    config_params = {
        "temperature": temperature,
        "max_output_tokens": max_completion_tokens,
        "top_p": top_p,
    }

    # 2. Handle Structured Output (JSON Mode)
    if structured_schema is not None:
        config_params["response_mime_type"] = "application/json"
        
        # Note: For complex schemas, you may need to convert the dict to 
        # genai.types.ResponseSchema
        pass 

    # 3. Handle System Prompt
    # Using `system_instruction` is the standard Gemini approach:
    config_params["system_instruction"] = system_prompt
    
    # 4. Construct Messages (contents)
    contents = [
        {"role": "user", "parts": [{"text": user_prompt}]},
    ]
    
    # 5. Create GenerationConfig object
    config = genai.types.GenerationConfig(**config_params)

    # 6. Perform the request (non-streaming)
    try:
        client = genai.Client()
        response = client.models.generate_content(
            model=model_id,
            contents=contents,
            config=config,
        )
    except Exception as e:
        print(f"Gemini API call failed: {e}")
        raise

    # 7. Return the final message content
    return response.text