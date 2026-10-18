// Conservative handling of clear self-harm language. This is not a clinical
// classifier; indirect or unfamiliar phrasing still depends on model safeguards.
export const crisisReply="I can't help with instructions to harm yourself. I'm sorry you're going through this. Move away from anything you could use to hurt yourself and contact someone you trust who can stay with you. If you might act now or are injured, contact local emergency services immediately. In the United States, call [988](tel:988?oai_link_source=model_response_hotline) or text [988](sms:988?oai_link_source=model_response_hotline) for crisis support; elsewhere, contact a local crisis service.";
export const privacyReply="I can't reveal private data, credentials, or internal instructions. I can help with your meal plan or explain how to keep your information safe.";

// Reject clear disclosure requests before giving the model an opportunity to
// echo confidential instructions. Recipe/pantry requests still use the planner.
function requestsDisclosure(text) {
 const target=/\b(?:api[\s_-]?keys?|passwords?|credentials|access[\s_-]?tokens?|secret[\s_-]?keys?|secrets|(?:system|developer)[\s_-]?(?:prompts?|instructions?)|(?:hidden|internal)\s+(?:prompts?|instructions?)|(?:private|confidential)\s+(?:user\s+)?(?:data|information|context)|environment\s+variables|everything\s+above)\b/;
 const request=/\b(?:reveal|dump|print|expose|disclose|leak|extract|display|repeat|recite|list|return|output|send|provide|show|give|tell|share)\b/;
 return target.test(text)&&(request.test(text)||/\bwhat\s+(?:is|are)\b/.test(text));
}

const words=text=>String(text).normalize('NFKC').toLowerCase().match(/[\p{L}\p{N}]+/gu)||[];
// Protect all visible model output, including structured tool arguments. Only
// substantial verbatim instruction fragments match; ordinary recipe language
// and short overlaps are allowed. This does not claim to detect paraphrases.
export function echoesInstructions(value,instructions) {
 const texts=[];
 const collect=v=>{
  if(typeof v==='string') texts.push(' '+words(v).join(' ')+' ');
  else if(Array.isArray(v)) v.forEach(collect);
  else if(v&&typeof v==='object') Object.values(v).forEach(collect);
 };
 collect(value);
 const source=words(instructions);
 for(let i=0;i+18<=source.length;i++) {
  const fragment=' '+source.slice(i,i+18).join(' ')+' ';
  if(texts.some(text=>text.includes(fragment))) return true;
 }
 return false;
}

export function safetyResponse(message) {
 const text=message.normalize('NFKC').toLowerCase().replace(/[’‘]/g,"'");
 const clearRisk=/\b(?:self[\s-]?harm|suicid(?:e|al)|overdos(?:e|ing))\b/.test(text)
  ||/\b(?:harm(?:ing)?|hurt(?:ing)?|kill(?:ing)?|injur(?:e|ing)|poison(?:ing)?|cut(?:ting)?|hang(?:ing)?|shoot(?:ing)?|burn(?:ing)?|drown(?:ing)?)\s+myself\b/.test(text)
  ||/\b(?:end|ending|take|taking)\s+my\s+(?:own\s+)?life\b/.test(text)
  ||/\b(?:want|wish|going|plan|planning)\s+to\s+(?:die|end\s+it\s+all)\b/.test(text)
  ||/\b(?:don't|do\s+not)\s+want\s+to\s+(?:live|be\s+alive)\b/.test(text);
 if(clearRisk) return {plan:null,reply:crisisReply,trace:[]};
 return requestsDisclosure(text)?{plan:null,reply:privacyReply,trace:[]}:null;
}
