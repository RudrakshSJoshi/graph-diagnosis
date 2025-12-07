import json
import os
import numpy as np
import pickle
import re
from typing import Tuple, Optional, List, Dict, Any
from sentence_transformers import SentenceTransformer
from sklearn.metrics.pairwise import cosine_similarity
from utils import *
import logging

logger = logging.getLogger("chat-backend")

# ==========================================
# RAG SYSTEM IMPLEMENTATION (Singleton)
# ==========================================

class DiseaseRAGSystem:
    _instance = None

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            cls._instance = super(DiseaseRAGSystem, cls).__new__(cls)
            cls._instance.initialized = False
        return cls._instance

    def __init__(self, json_file_path: str = None, model_name: str = 'all-MiniLM-L6-v2'):
        # Prevent re-initialization if already initialized
        if self.initialized:
            return

        print("--- LOADING RAG SYSTEM (This should only happen once at startup) ---")
        try:
            self.model = SentenceTransformer(model_name)

            # Resolve path relative to this script
            if json_file_path is None:
                base_dir = os.path.dirname(os.path.abspath(__file__))
                json_file_path = os.path.join(base_dir, 'data', 'medical_dataset.json')
            
            # Path for persistent embeddings storage
            base_dir = os.path.dirname(os.path.abspath(__file__))
            self.embeddings_cache_path = os.path.join(base_dir, 'data', 'symptom_embeddings.pkl')

            self.diseases_data = self._load_data(json_file_path)
            
            # Load or precompute embeddings (persistent storage optimization)
            if os.path.exists(self.embeddings_cache_path):
                print("--- Loading precomputed embeddings from cache ---")
                self.symptom_embeddings = self._load_embeddings()
            else:
                print("--- Computing embeddings for the first time (this may take a moment) ---")
                self.symptom_embeddings = self._precompute_symptom_embeddings()
                self._save_embeddings()
            
            self.initialized = True
            print("--- RAG SYSTEM LOADED SUCCESSFULLY ---")
        except Exception as e:
            # Log full stacktrace so we know why initialization failed
            logger.exception("RAG system initialization failed")
            # Ensure instance remains marked uninitialized so future attempts can retry
            self.initialized = False
            # Re-raise so calling code can decide how to handle it
            raise

    def _load_data(self, json_file_path):
        try:
            with open(json_file_path, 'r') as f:
                data = json.load(f)
            return data
        except FileNotFoundError:
            print(f"Error: Dataset not found at {json_file_path}")
            return []

    def _precompute_symptom_embeddings(self):
        """Precompute embeddings for all official symptoms once."""
        symptom_embeddings = {}
        for disease_info in self.diseases_data:
            disease = disease_info['disease']
            symptoms = disease_info['symptoms']
            # Compute embeddings for each symptom
            embeddings = self.model.encode(symptoms)
            symptom_embeddings[disease] = {
                'symptoms': symptoms,
                'embeddings': embeddings
            }
        return symptom_embeddings
    
    def _save_embeddings(self):
        """Save precomputed embeddings to disk for faster subsequent startups."""
        try:
            with open(self.embeddings_cache_path, 'wb') as f:
                pickle.dump(self.symptom_embeddings, f)
            print(f"--- Embeddings saved to {self.embeddings_cache_path} ---")
        except Exception as e:
            logger.warning(f"Failed to save embeddings cache: {e}")
    
    def _load_embeddings(self):
        """Load precomputed embeddings from disk."""
        try:
            with open(self.embeddings_cache_path, 'rb') as f:
                embeddings = pickle.load(f)
            print(f"--- Loaded {len(embeddings)} disease embeddings from cache ---")
            return embeddings
        except Exception as e:
            logger.warning(f"Failed to load embeddings cache: {e}")
            # Fallback to recomputing
            return self._precompute_symptom_embeddings()

    def extract_symptoms_by_sentence(self, query, similarity_threshold=0.45):
        # Split query into sentences
        sentences = re.split(r'[.!?]+', query)
        sentences = [s.strip() for s in sentences if s.strip()]
        if not sentences:
            sentences = [query]

        # Encode all sentences
        sentence_embeddings = self.model.encode(sentences)
        matched_symptoms = {} 

        for sentence, sent_embedding in zip(sentences, sentence_embeddings):
            for disease, symptom_data in self.symptom_embeddings.items():
                symptoms = symptom_data['symptoms']
                embeddings = symptom_data['embeddings']
                
                # Calculate cosine similarity
                similarities = cosine_similarity([sent_embedding], embeddings)[0]
                
                for symptom, similarity in zip(symptoms, similarities):
                    if similarity >= similarity_threshold:
                        key = (symptom, disease)
                        if key not in matched_symptoms or matched_symptoms[key] < similarity:
                            matched_symptoms[key] = similarity
        
        result = [(symptom, score, disease) for (symptom, disease), score in matched_symptoms.items()]
        result.sort(key=lambda x: x[1], reverse=True)
        return result

    def calculate_disease_scores(self, matched_symptoms):
        disease_matches = {}
        for symptom, similarity, disease in matched_symptoms:
            if disease not in disease_matches:
                disease_matches[disease] = []
            disease_matches[disease].append((symptom, similarity))
        
        disease_scores = {}
        total_matched_symptoms = len(set([s[0] for s in matched_symptoms]))
        
        for disease, matches in disease_matches.items():
            num_disease_symptoms = len(self.symptom_embeddings[disease]['symptoms'])
            score = 0
            for symptom, similarity in matches:
                base_score = (1.0 / num_disease_symptoms) + (1.0 / total_matched_symptoms)
                score += base_score * similarity
            
            disease_info = next((item for item in self.diseases_data if item["disease"] == disease), None)
            
            # Convert numpy types to native Python types for JSON serialization
            disease_scores[disease] = {
                'score': float(score),
                'matched_symptoms': [str(s[0]) for s in matches],
                'num_matches': int(len(matches)),
                'total_symptoms': int(num_disease_symptoms),
                'all_symptoms': disease_info['symptoms'] if disease_info else [],
                'precautions': disease_info['precautions'] if disease_info else []
            }
        
        sorted_diseases = sorted(disease_scores.items(), key=lambda x: x[1]['score'], reverse=True)
        return dict(sorted_diseases)

    def diagnose(self, query, top_k=5, similarity_threshold=0.45):
        """
        Returns raw diagnosis data to be processed by the Agentic LLM.
        """
        matched_symptoms = self.extract_symptoms_by_sentence(query, similarity_threshold)
        
        if not matched_symptoms:
            return {
                'status': 'no_match',
                'query': query,
                'message': 'No symptoms identified.',
                'top_diseases': []
            }

        disease_scores = self.calculate_disease_scores(matched_symptoms)
        
        top_diseases = []
        for i, (disease, score_info) in enumerate(disease_scores.items()):
            if i >= top_k:
                break
            top_diseases.append({
                'disease': disease,
                'score': score_info['score'],
                'matched_symptoms': score_info['matched_symptoms'],
                'precautions': score_info['precautions']
            })

        return {
            'status': 'success' if top_diseases else 'low_confidence',
            'query': query,
            'top_diseases': top_diseases
        }

# Global helper to ensure we don't load the model on every request
def get_rag_system():
    print("--- get_rag_system called ---")
    return DiseaseRAGSystem()

# ==========================================
# MAIN LOGIC (examine_query)
# ==========================================

def examine_query2(query: str, first_query: bool = False, punish_factor: int = 1) -> Tuple[str, bool]:
    """
    Dedicated Subsequent-Turn Agent: Handles follow-up queries using chat history 
    and a working list of diseases to narrow the diagnosis.
    """
    
    # ----------------------------------------------------------------------
    # 1. Error Guard (The function should ideally not be called with first_query=True in this context)
    # ----------------------------------------------------------------------
    if first_query:
        print("Warning: examine_query2 called with first_query=True. Proceeding to invoke LLM based on internal memory.")

    # ----------------------------------------------------------------------
    # 2. Setup Context and System Prompt
    # ----------------------------------------------------------------------
    print(punish_factor)
    can_ask = "You cannot ask any more questions, you must give a final diagnosis." if punish_factor == 3 else "You may ask clarifying questions to narrow down the diagnosis, only if required."
    print(can_ask)
    
    # Fetch current state from memory (from the first turn or previous subsequent turn)
    current_diseases = process_memory("list", "fetch")
    past_convo = process_memory("chat", "fetch")
    previous_diagnosis = ", ".join(current_diseases)

    # The system prompt is focused on iterative refinement
    system_prompt = f"""
You are a friendly, expert medical diagnostic assistant engaged in a multi-turn conversation. Your goal is to narrow the current list of plausible diagnoses to one final diagnosis.

**CONTEXT:**
You have access to the full 'Past conversation' and the 'Current possible diagnoses'.

**DECISION RULES:**
1.  **Final Diagnosis:** If the 'Current possible diagnoses' list contains only **one disease**, or if 'punish_factor' implies no more questions can be asked (punish_factor == 3), you MUST provide the final diagnosis.
    * **Response Style:** State the diagnosis and provide **full, actionable recommendations (precautions)** in a **warm, reassuring, and friendly tone**, using clear markdown formatting (like bullet points). Set <CONTINUE> to False.
2.  **Continue Narrowing:** If the list contains **multiple diseases** and you are allowed to ask questions, ask **ONE short, diagnostic question** based on the user's latest answer, aiming to eliminate at least one disease. Set <CONTINUE> to True.

OUTPUT FORMAT (must follow exactly):

<THINK>
Private reasoning only. Include short notes used to track which diseases were eliminated and the justification for the next question or final diagnosis.
Do NOT reveal <THINK> contents in <RESPONSE>.
</THINK>

<DISEASES>
Comma-separated list of the **remaining plausible diagnoses**. This list MUST be a subset of the previous list unless the user's answer forces re-evaluation. If giving a final diagnosis, this list MUST contain exactly one disease.
</DISEASES>

<RESPONSE>
If <CONTINUE> is True:
  - Ask ONE short diagnostic question that eliminates ≥1 disease.
If <CONTINUE> is False (Final Answer):
  - **If single diagnosis:** State the diagnosis and provide the full, actionable recommendations in a friendly, medical assistant tone.
  - **If multiple diagnoses (final turn due to punish_factor=3):** List the tests/doctor that would definitively distinguish them, and give clear interim recommendations (care, precautions, red flags).
</RESPONSE>

<CONTINUE>
True or False. True only if you need clarification AND are allowed to ask more questions. If punish_factor is 3, CONTINUE must be False.
</CONTINUE>
"""

    # User Prompt for subsequent turn
    user_prompt = f"Current possible diagnoses: {previous_diagnosis}\n\nPast conversation:\n" + "\n".join(past_convo) + f"\n\nUser follow-up: {query}"
    print(user_prompt)
    
    # ----------------------------------------------------------------------
    # 3. Invoke LLM and Parse Response
    # ----------------------------------------------------------------------
    response = invoke_llm(
        system_prompt=system_prompt,
        user_prompt=user_prompt,
        model_id="openai/gpt-oss-120b"
    )

    print(response)

    # Extract blocks
    try:
        disease_block = response.split("<DISEASES>")[1].split("</DISEASES>")[0].strip()
        disease_list = [d.strip() for d in disease_block.split(",") if d.strip()]
    except IndexError:
        disease_list = current_diseases # Retain old list on parsing failure
        
    try:
        user_response = response.split("<RESPONSE>")[1].split("</RESPONSE>")[0].strip()
    except IndexError:
        user_response = "I encountered a parsing error during diagnosis. Could you please repeat your last piece of information?"

    try:
        continue_flag = response.split("<CONTINUE>")[1].split("</CONTINUE>")[0].strip().lower() == "true"
    except IndexError:
        continue_flag = False

    # ----------------------------------------------------------------------
    # 4. Handle Punishment Factor (Final Override)
    # ----------------------------------------------------------------------
    if punish_factor == 3 and continue_flag:
        # If the LLM incorrectly asked a question on the final turn, 
        # we override the response with a final forced diagnosis.
        system_prompt_final = f"""
You are now forced to give a final diagnosis and complete recommendations based on the entire conversation. You must select one disease from the list: {", ".join(disease_list)}, and provide its recommendations in a **friendly, medical assistant tone** using clear markdown formatting. Do not ask any questions.
"""
        total_chat = process_memory("chat", "fetch")
        user_prompt_final = "I am not giving you any more information. Based on the entire conversation, provide a final diagnosis and complete recommendations.\n\n"
        user_prompt_final += "\n".join(total_chat)
        user_prompt_final += "\n\nPossible diagnoses after last step: " + ", ".join(disease_list)
        final_response = invoke_llm(
            system_prompt=system_prompt_final,
            user_prompt=user_prompt_final,
            model_id="openai/gpt-oss-120b"
        )
        # Update memory one last time with the correct final response
        process_memory("list", "update", [disease_list[0]] if disease_list else [])
        process_memory("chat", "append", [f"Bot: {final_response}"])
        return final_response, False

    # ----------------------------------------------------------------------
    # 5. Update Memory and Return
    # ----------------------------------------------------------------------
    process_memory("list", "update", disease_list)
    process_memory("chat", "append", [f"User: {query}", f"Bot: {user_response}"])

    if continue_flag and punish_factor < 3:
        return user_response, True
    
    # If continue_flag is False, or punish_factor is 3 (and we didn't hit the override):
    return user_response, False

def examine_query_hybrid(query: str, first_query: bool = True, punish_factor: int = 1) -> Tuple[str, bool]:
    """
    Hybrid handler for the medical RAG bot:
    1. First Query (first_query=True): Uses RAG to retrieve top diseases, then passes to LLM for decision.
       - If RAG result is conclusive, gives final answer (continue=False).
       - If RAG result is inconclusive, transitions to multi-turn mode (continue=True).
    2. Subsequent Queries (first_query=False): Uses the Agentic, memory-based logic of examine_query2.
    """
    print("--- examine_query_hybrid called ---")
    
    # ----------------------------------------------------------------------
    # 1. Subsequent Queries: Fallback to examine_query2 logic
    # ----------------------------------------------------------------------
    if not first_query:
        # Subsequent turns are handled entirely by the examine_query2 logic 
        # (relying on memory, not RAG)
        # Assuming examine_query2 is defined elsewhere and handles its own imports/logic
        return examine_query2(query, first_query=False, punish_factor=punish_factor)

    # ----------------------------------------------------------------------
    # 1.5. Clear chat memory for new diagnosis session (first_query=True)
    # ----------------------------------------------------------------------
    print("--- Clearing chat memory for new diagnosis session ---")
    process_memory("chat", "update", [])
    
    # ----------------------------------------------------------------------
    # 2. First Query: Hybrid RAG + Agentic LLM
    # ----------------------------------------------------------------------
    
    print("--- Running Hybrid RAG for First Query ---")
    
    # Run RAG Diagnosis (Retrieval Step)
    try:
        rag = get_rag_system() # Assuming get_rag_system is defined and works
        # Retrieve up to 8 diseases as requested
        diagnosis_result = rag.diagnose(query, top_k=8, similarity_threshold=0.45)
    except Exception as e:
        # Fallback if RAG fails
        diagnosis_result = {"status": "error", "message": str(e)}

    # Define the Agentic LLM System Prompt (Based on examine_query2, but tailored for RAG input)
    can_ask = "You cannot ask any more questions, you must give a final diagnosis." if punish_factor == 3 else "You may ask clarifying questions to narrow down the diagnosis, only if required."
    
    # Extract the top diseases from the RAG result for the LLM prompt
    top_diseases_rag = diagnosis_result.get('top_diseases', [])
    initial_diagnoses = ", ".join([d['disease'] for d in top_diseases_rag])

    # Provide the RAG result directly to the LLM for evaluation
    rag_context = json.dumps(diagnosis_result, indent=2)

    # --- START MODIFIED SYSTEM PROMPT ---
    system_prompt = f"""
You are a friendly, expert medical AI assistant. You will analyze a user's query and RAG context (a list of top 8 scored diseases).

{can_ask}

**CRITICAL RULE FOR FIRST TURN:**
Evaluate the RAG Context (JSON):
1.  **Clear Winner (High Confidence):** If one, two or three diseases have a significantly higher score than all others. You MUST provide a FINAL diagnosis.
    * **Response Style:** State the final diagnosis for all the selected diseases separately and provide the diseases' precautions (from the JSON context) in a **warm, reassuring, and friendly tone**, like a medical assistant. Use bullet points for clarity. Set <CONTINUE> to False.
2.  **Low Confidence / Tie:** If scores are too close, or all scores are very low (e.g., all under 0.2), you MUST start the iterative diagnostic process. Pick the top 5 diseases as plausible diagnoses and ask ONE clarifying question that aims to eliminate the most diseases. Set <CONTINUE> to True.
3.  **No Match:** If 'status' is 'no_match', use your general knowledge and ask a clarifying question. Set <CONTINUE> to True.

OUTPUT FORMAT (must follow exactly):

<THINK>
Private reasoning only. Include short notes used to pick diagnoses and your decision (Clear Winner/Low Confidence). Use the RAG scores to justify your choice.
Do NOT reveal <THINK> contents in <RESPONSE>.
</THINK>

<DISEASES>
Comma-separated list of all plausible diagnoses. If you found some Clear Winners, this list MUST contain only those diseases. If you need clarification, list the top 5 diseases.
</DISEASES>

<RESPONSE>
If <CONTINUE> is True:
  - Ask ONE short diagnostic question that helps distinguish between the diseases in <DISEASES>.
If <CONTINUE> is False (Final Answer):
  - **If single diagnosis:** State the diagnosis and provide the **precautions listed in the RAG context** in a friendly, medical assistant tone, using clear markdown formatting.
  - **If multiple diagnoses (final turn):** List the tests/doctor that would definitively distinguish them, and give clear interim recommendations (care, precautions, red flags).
</RESPONSE>

<CONTINUE>
True or False. True only if you require one short clarifying answer to narrow diagnoses AND can_ask_more_questions is True. If can_ask_more_questions is False, CONTINUE must be False.
</CONTINUE>
"""
    # --- END MODIFIED SYSTEM PROMPT ---

    user_prompt_content = f"USER QUERY: {query}\n\nRAG SYSTEM OUTPUT (Context):\n{rag_context}\n\nInitial Diagnoses from RAG: {initial_diagnoses}"

    # Invoke LLM
    try:
        # Assuming invoke_llm is defined and works
        llm_response_str = invoke_llm(
            system_prompt=system_prompt,
            user_prompt=user_prompt_content,
            model_id="openai/gpt-oss-120b"
        )
        
        # --- Parsing the LLM's Structured Output ---
        
        # Extract <DISEASES> block
        try:
            disease_block = llm_response_str.split("<DISEASES>")[1].split("</DISEASES>")[0].strip()
            disease_list = [d.strip() for d in disease_block.split(",") if d.strip()]
        except IndexError:
            disease_list = [] # Handle parsing failure

        # Extract <RESPONSE> block
        try:
            user_response = llm_response_str.split("<RESPONSE>")[1].split("</RESPONSE>")[0].strip()
        except IndexError:
            user_response = "I encountered an error while processing the diagnosis. Please try rephrasing your symptoms."

        # Extract <CONTINUE> block
        try:
            continue_flag = llm_response_str.split("<CONTINUE>")[1].split("</CONTINUE>")[0].strip().lower() == "true"
        except IndexError:
            continue_flag = False

    except Exception as e:
        # Fallback if LLM invocation fails (rate limit, network error, etc)
        disease_list = [d['disease'] for d in top_diseases_rag[:5]] if top_diseases_rag else []
        user_response = "I'm having trouble analyzing the medical data right now. Please try again later."
        continue_flag = False
        logger.exception(f"LLM Error during first query: {e}")

    # --- Update Memory and Return ---
    
    # Update memory with the diseases chosen by the LLM
    # Assuming process_memory is defined and works
    process_memory("list", "update", disease_list)
    # Update chat memory
    process_memory("chat", "append", [f"User: {query}", f"Bot: {user_response}"])

    return user_response, continue_flag