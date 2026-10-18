// Conservative handling of clear self-harm language. This is not a clinical
// classifier; indirect or unfamiliar phrasing still depends on model safeguards.
export const crisisReply="I can't help with instructions to harm yourself. I'm sorry you're going through this. Move away from anything you could use to hurt yourself and contact someone you trust who can stay with you. If you might act now or are injured, contact local emergency services immediately. In the United States, call [988](tel:988?oai_link_source=model_response_hotline) or text [988](sms:988?oai_link_source=model_response_hotline) for crisis support; elsewhere, contact a local crisis service.";

export function safetyResponse(message) {
 const text=message.normalize('NFKC').toLowerCase().replace(/[’‘]/g,"'");
 const clearRisk=/\b(?:self[\s-]?harm|suicid(?:e|al)|overdos(?:e|ing))\b/.test(text)
  ||/\b(?:harm(?:ing)?|hurt(?:ing)?|kill(?:ing)?|injur(?:e|ing)|poison(?:ing)?|cut(?:ting)?|hang(?:ing)?|shoot(?:ing)?|burn(?:ing)?|drown(?:ing)?)\s+myself\b/.test(text)
  ||/\b(?:end|ending|take|taking)\s+my\s+(?:own\s+)?life\b/.test(text)
  ||/\b(?:want|wish|going|plan|planning)\s+to\s+(?:die|end\s+it\s+all)\b/.test(text)
  ||/\b(?:don't|do\s+not)\s+want\s+to\s+(?:live|be\s+alive)\b/.test(text);
 return clearRisk?{plan:null,reply:crisisReply,trace:[]}:null;
}
