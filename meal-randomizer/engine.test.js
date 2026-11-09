// Run with: node meal-randomizer/engine.test.js
const assert = require('assert');
const E = require('./engine.js');

function seq(values) { let i = 0; return () => values[i++ % values.length]; }
function state(dishes, history = [], settings = {}) {
  return { dishes, history, settings: Object.assign({ gapDays: 7, weighted: false }, settings) };
}
const dal = { id: 'dal', name: 'Dal Tadka', meals: ['lunch', 'dinner'], gap: null };
const rajma = { id: 'rajma', name: 'Rajma', meals: ['lunch'], gap: null };
const pav = { id: 'pav', name: 'Pav Bhaji', meals: ['dinner'], gap: 14 };

let passed = 0;
function test(name, fn) { fn(); passed++; console.log('  ok -', name); }

test('date helpers', () => {
  assert.strictEqual(E.addDays('2026-02-27', 3), '2026-03-02');
  assert.strictEqual(E.daysBetween('2026-11-01', '2026-11-08'), 7);
  assert.strictEqual(E.daysBetween('2026-11-08', '2026-11-01'), -7);
});

test('global gap blocks repeat until N days have passed', () => {
  const s = state([dal], [{ date: '2026-11-01', meal: 'lunch', dishId: 'dal' }]);
  assert.strictEqual(E.isEligible(dal, '2026-11-07', 'dinner', s.history, s.settings), false);
  assert.strictEqual(E.isEligible(dal, '2026-11-08', 'dinner', s.history, s.settings), true);
  assert.strictEqual(E.nextAvailable(dal, '2026-11-02', s.history, s.settings), '2026-11-08');
});

test('gap also applies to future planned entries', () => {
  const s = state([dal], [{ date: '2026-11-10', meal: 'dinner', dishId: 'dal' }]);
  assert.strictEqual(E.isEligible(dal, '2026-11-05', 'lunch', s.history, s.settings), false);
  assert.strictEqual(E.isEligible(dal, '2026-11-03', 'lunch', s.history, s.settings), true);
});

test('same dish cannot fill lunch and dinner on one day', () => {
  const s = state([dal], [{ date: '2026-11-09', meal: 'lunch', dishId: 'dal' }]);
  assert.strictEqual(E.pickDish(s, '2026-11-09', 'dinner'), null);
});

test('replacing the same slot ignores its own entry', () => {
  const s = state([dal], [{ date: '2026-11-09', meal: 'lunch', dishId: 'dal' }]);
  assert.strictEqual(E.isEligible(dal, '2026-11-09', 'lunch', s.history, s.settings), true);
});

test('per-dish gap overrides global gap; gap 0 disables rule', () => {
  const h = [{ date: '2026-11-01', meal: 'dinner', dishId: 'pav' }];
  const s = state([pav], h);
  assert.strictEqual(E.isEligible(pav, '2026-11-10', 'dinner', h, s.settings), false);
  assert.strictEqual(E.isEligible(pav, '2026-11-15', 'dinner', h, s.settings), true);
  const free = Object.assign({}, pav, { gap: 0 });
  assert.strictEqual(E.isEligible(free, '2026-11-01', 'lunch', h, s.settings), false, 'not a lunch dish');
  assert.strictEqual(E.isEligible(free, '2026-11-02', 'dinner', h, s.settings), true);
});

test('meal type and inactive dishes are respected', () => {
  const s = state([rajma, Object.assign({}, dal, { active: false })]);
  assert.deepStrictEqual(E.eligibleDishes(s, '2026-11-09', 'dinner').map(d => d.id), []);
  assert.deepStrictEqual(E.eligibleDishes(s, '2026-11-09', 'lunch').map(d => d.id), ['rajma']);
});

test('weighted pick favours the dish not cooked for longest', () => {
  const s = state([dal, rajma], [
    { date: '2026-11-01', meal: 'lunch', dishId: 'dal' },     // 8 days ago
    { date: '2026-09-01', meal: 'lunch', dishId: 'rajma' }    // 60+ days ago
  ], { weighted: true });
  // weights: dal 8, rajma 60 -> r = 0.5 * 68 = 34 lands in rajma
  assert.strictEqual(E.pickDish(s, '2026-11-09', 'lunch', { rng: () => 0.5 }).id, 'rajma');
  assert.strictEqual(E.pickDish(s, '2026-11-09', 'lunch', { rng: () => 0.05 }).id, 'dal');
});

test('week plan never repeats a dish inside the gap and keeps fixed slots', () => {
  const dishes = Array.from({ length: 10 }, (_, i) => ({ id: 'd' + i, name: 'D' + i, meals: ['lunch', 'dinner'], gap: null }));
  const s = state(dishes, [{ date: '2026-11-09', meal: 'lunch', dishId: 'd0' }], { gapDays: 3, weighted: true });
  const plan = E.planRange(s, '2026-11-09', 7, ['lunch', 'dinner'], { rng: seq([0.13, 0.71, 0.42, 0.99, 0.05, 0.58]) });
  assert.strictEqual(plan.length, 14);
  assert.deepStrictEqual(plan[0], { date: '2026-11-09', meal: 'lunch', dishId: 'd0', fixed: true });
  plan.forEach(a => plan.forEach(b => {
    if (a !== b && a.dishId && a.dishId === b.dishId) {
      assert.ok(Math.abs(E.daysBetween(a.date, b.date)) >= 3, `${a.dishId} repeated within gap`);
    }
  }));
  assert.ok(plan.every(p => p.dishId), 'all slots filled');
});

test('plan returns null slots when the pool runs dry', () => {
  const s = state([dal], [], { gapDays: 7 });
  const plan = E.planRange(s, '2026-11-09', 3, ['lunch'], { rng: () => 0 });
  assert.deepStrictEqual(plan.map(p => p.dishId), ['dal', null, null]);
});

test('blockedDishes lists soonest-available first', () => {
  const s = state([dal, pav], [
    { date: '2026-11-08', meal: 'dinner', dishId: 'dal' },
    { date: '2026-11-05', meal: 'dinner', dishId: 'pav' }
  ]);
  const b = E.blockedDishes(s, '2026-11-09', 'dinner');
  assert.deepStrictEqual(b.map(x => [x.dish.id, x.availableOn]), [['dal', '2026-11-15'], ['pav', '2026-11-19']]);
});

test('upsertEntry replaces the slot', () => {
  let h = E.upsertEntry([], { date: '2026-11-09', meal: 'lunch', dishId: 'dal' });
  h = E.upsertEntry(h, { date: '2026-11-09', meal: 'lunch', dishId: 'rajma' });
  assert.strictEqual(h.length, 1);
  assert.strictEqual(h[0].dishId, 'rajma');
});

console.log(`\n${passed} tests passed`);
