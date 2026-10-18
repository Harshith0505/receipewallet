// Selection policy is separate from price math: cheaper must not erase meal intent.
export const planningGoals = ['balanced', 'profile', 'pantry', 'surprise', 'cheapest'];
const cues = {
  noodles: /spicy|heat|bold|garlic noodles/i,
  bowl: /fresh|greens|green goddess|vegetable.forward/i,
  pasta: /pasta|comfort|cozy|classic|familiar/i,
  chana: /chana|masala|indian|warming spices|warmly spiced/i,
  'mexican-rice': /mexican|rice bowl|avocado tomato rice|citrus|zesty/i,
  'sesame-noodles': /chilled|japanese|cool|crisp|sesame noodles/i,
  'chickpea-salad': /salad|lemony|middle eastern|quick|low effort|refreshing/i
};
export function mealScore(recipe, constraints, {goal='balanced', recentRecipeIds=[], preferredRecipeId=null}={}) {
  const used = recipe.ingredients.filter(i => constraints.pantryIds.includes(i.id)).length;
  const coverage = used / recipe.ingredients.length;
  const preference = constraints.mealPreference.toLowerCase();
  let score = cues[recipe.id]?.test(preference) ? 10 : 0;
  if (preference.includes(recipe.name.toLowerCase())) score += 100;
  if (goal === 'profile' && recipe.id === preferredRecipeId) score += 30;
  if (goal === 'profile' && recentRecipeIds.at(-1) === recipe.id) score -= 45;
  if (goal === 'pantry') score = coverage * 100 + used - (recentRecipeIds.at(-1)===recipe.id ? 0.5 : 0);
  if (goal === 'surprise') score = -recentRecipeIds.filter(id => id === recipe.id).length * 100;
  if (goal === 'cheapest') score = 0;
  return {score, pantryUsed:used, pantryCoverage:coverage};
}
export function inferPlanningGoal(goal,message) {
 if(goal!=='balanced')return goal;
 const request=message.split('\n')[0];
 if(/surprise me|something different|different meal|another recipe/i.test(request))return 'surprise';
 if(/use what i have|use my pantry|use my ingredients|as many pantry/i.test(request))return 'pantry';
 if(/cheaper|cheapest|lowest (?:upfront |net )?cost/i.test(request))return 'cheapest';
 return goal;
}
export function rankMeals(options, constraints, policy) {
  const allOverBudget=options.length>0&&options.every(o=>!o.withinBudget);
  return options.map(option => ({...option, ...mealScore(option.recipe, constraints, policy)}))
    .sort((a,b) => Number(b.withinBudget)-Number(a.withinBudget) ||
      (policy.goal === 'cheapest'||allOverBudget ? a.amount-b.amount : b.score-a.score) || a.net-b.net || a.optionId.localeCompare(b.optionId));
}
