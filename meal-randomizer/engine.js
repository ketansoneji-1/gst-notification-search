/*
 * Meal Randomizer – core engine (no DOM access).
 *
 * Gap rule: a dish with a minimum gap of N days may only be scheduled on a date
 * that is at least N days away from EVERY other date on which that dish is
 * logged or planned (past or future). Example: gap = 7, cooked on Mon 1st ->
 * next eligible on Mon 8th. A gap of 0 disables the rule for that dish.
 *
 * Works in the browser (window.MealEngine) and in Node (module.exports).
 */
(function (root) {
  'use strict';

  var MEALS = ['lunch', 'dinner'];
  var DAY_MS = 86400000;
  var SEARCH_LIMIT_DAYS = 730;

  /* ---------- date helpers (YYYY-MM-DD, timezone-safe) ---------- */
  function toUTC(dateStr) {
    var p = dateStr.split('-');
    return Date.UTC(+p[0], +p[1] - 1, +p[2]);
  }
  function fromUTC(ms) {
    var d = new Date(ms);
    return d.getUTCFullYear() + '-' +
      String(d.getUTCMonth() + 1).padStart(2, '0') + '-' +
      String(d.getUTCDate()).padStart(2, '0');
  }
  function addDays(dateStr, n) { return fromUTC(toUTC(dateStr) + n * DAY_MS); }
  function daysBetween(a, b) { return Math.round((toUTC(b) - toUTC(a)) / DAY_MS); }
  function todayStr(now) {
    var d = now || new Date();
    return d.getFullYear() + '-' +
      String(d.getMonth() + 1).padStart(2, '0') + '-' +
      String(d.getDate()).padStart(2, '0');
  }

  /* ---------- rules ---------- */
  function effectiveGap(dish, settings) {
    if (dish.gap !== null && dish.gap !== undefined && dish.gap !== '') return Math.max(0, +dish.gap);
    return Math.max(0, +(settings.gapDays || 0));
  }

  function servesMeal(dish, meal) {
    return dish.active !== false && Array.isArray(dish.meals) && dish.meals.indexOf(meal) !== -1;
  }

  function sameSlot(e, slot) {
    return slot && e.date === slot.date && e.meal === slot.meal;
  }

  /** Entries of this dish that block `date` (ignoring the slot being filled). */
  function conflicts(dish, date, history, settings, ignoreSlot) {
    var gap = effectiveGap(dish, settings);
    if (gap === 0) return [];
    return history.filter(function (e) {
      return e.dishId === dish.id && !sameSlot(e, ignoreSlot) &&
        Math.abs(daysBetween(e.date, date)) < gap;
    });
  }

  function isEligible(dish, date, meal, history, settings) {
    return servesMeal(dish, meal) &&
      conflicts(dish, date, history, settings, { date: date, meal: meal }).length === 0;
  }

  /** Most recent date on/before `date` the dish was logged (or null). */
  function lastCooked(dish, history, onOrBefore) {
    var best = null;
    history.forEach(function (e) {
      if (e.dishId !== dish.id) return;
      if (onOrBefore && e.date > onOrBefore) return;
      if (!best || e.date > best) best = e.date;
    });
    return best;
  }

  /** Earliest date >= fromDate on which the dish is free of conflicts. */
  function nextAvailable(dish, fromDate, history, settings) {
    for (var i = 0; i <= SEARCH_LIMIT_DAYS; i++) {
      var d = addDays(fromDate, i);
      if (conflicts(dish, d, history, settings, null).length === 0) return d;
    }
    return null;
  }

  function eligibleDishes(state, date, meal, history, exclude) {
    var h = history || state.history;
    var ex = exclude || [];
    return state.dishes.filter(function (d) {
      return ex.indexOf(d.id) === -1 && isEligible(d, date, meal, h, state.settings);
    });
  }

  /**
   * Random pick. With settings.weighted, dishes not cooked for longer get a
   * proportionally higher chance (weight = days since last cooked, capped at
   * 60; never-cooked dishes get the cap).
   */
  function pickDish(state, date, meal, opts) {
    opts = opts || {};
    var rng = opts.rng || Math.random;
    var history = opts.history || state.history;
    var pool = eligibleDishes(state, date, meal, history, opts.exclude);
    if (!pool.length) return null;
    if (!state.settings.weighted) return pool[Math.floor(rng() * pool.length)];

    var weights = pool.map(function (d) {
      var last = lastCooked(d, history, date);
      var since = last ? daysBetween(last, date) : 60;
      return Math.min(60, Math.max(1, since));
    });
    var total = weights.reduce(function (a, b) { return a + b; }, 0);
    var r = rng() * total;
    for (var i = 0; i < pool.length; i++) {
      r -= weights[i];
      if (r < 0) return pool[i];
    }
    return pool[pool.length - 1];
  }

  /** Dishes that would serve this meal but are blocked by the gap, soonest first. */
  function blockedDishes(state, date, meal) {
    return state.dishes
      .filter(function (d) { return servesMeal(d, meal) && !isEligible(d, date, meal, state.history, state.settings); })
      .map(function (d) { return { dish: d, availableOn: nextAvailable(d, date, state.history, state.settings) }; })
      .sort(function (a, b) { return (a.availableOn || '9999') < (b.availableOn || '9999') ? -1 : 1; });
  }

  /**
   * Build a multi-day plan. Slots already in history are kept as-is (fixed).
   * Each new pick is added to a working history so later days respect it.
   * Returns [{date, meal, dishId|null, fixed}].
   */
  function planRange(state, startDate, days, meals, opts) {
    opts = opts || {};
    var working = state.history.slice();
    var out = [];
    for (var i = 0; i < days; i++) {
      var date = addDays(startDate, i);
      meals.forEach(function (meal) {
        var existing = working.filter(function (e) { return e.date === date && e.meal === meal; })[0];
        if (existing) { out.push({ date: date, meal: meal, dishId: existing.dishId, fixed: true }); return; }
        var dish = pickDish(state, date, meal, { rng: opts.rng, history: working });
        var entry = { date: date, meal: meal, dishId: dish ? dish.id : null, fixed: false };
        if (dish) working.push({ date: date, meal: meal, dishId: dish.id });
        out.push(entry);
      });
    }
    return out;
  }

  /** Insert or replace the entry for (date, meal). Returns a new array. */
  function upsertEntry(history, entry) {
    return history.filter(function (e) { return !sameSlot(e, entry); }).concat([{
      id: entry.id || ('h' + Date.now().toString(36) + Math.random().toString(36).slice(2, 7)),
      date: entry.date, meal: entry.meal, dishId: entry.dishId
    }]);
  }

  var api = {
    MEALS: MEALS, addDays: addDays, daysBetween: daysBetween, todayStr: todayStr,
    effectiveGap: effectiveGap, servesMeal: servesMeal, conflicts: conflicts,
    isEligible: isEligible, lastCooked: lastCooked, nextAvailable: nextAvailable,
    eligibleDishes: eligibleDishes, pickDish: pickDish, blockedDishes: blockedDishes,
    planRange: planRange, upsertEntry: upsertEntry
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.MealEngine = api;
})(this);
