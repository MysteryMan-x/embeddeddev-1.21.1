const ingredient = id => id.startsWith('#') ? {tag: id.slice(1)} : {item: id.includes(':') ? id : `minecraft:${id}`};
const mod = id => `embeddeddev:${id}`;
const siliconOres = ['silicon_ore', 'deepslate_silicon_ore'];

export async function generateSurvivalData(json) {
  async function recipe(id, data, ...unlockItems) {
    const result = data.result ?? {id: mod(id)};
    await json(`data/embeddeddev/recipe/${id}.json`, {...data, result});
    await json(`data/embeddeddev/advancement/recipes/${id}.json`, {
      parent: 'minecraft:recipes/root',
      criteria: {
        has_ingredient: {
          trigger: 'minecraft:inventory_changed',
          conditions: {items: [{items: unlockItems.map(item => ingredient(item).item)}]}
        },
        has_the_recipe: {
          trigger: 'minecraft:recipe_unlocked', conditions: {recipe: mod(id)}
        }
      },
      requirements: [['has_ingredient', 'has_the_recipe']],
      rewards: {recipes: [mod(id)]}
    });
  }

  const shaped = (pattern, keys) => ({
    type: 'minecraft:crafting_shaped', category: 'redstone', pattern,
    key: Object.fromEntries(Object.entries(keys).map(([symbol, item]) => [symbol, ingredient(item)]))
  });
  const shapeless = (...items) => ({
    type: 'minecraft:crafting_shapeless', category: 'redstone',
    ingredients: items.map(item => Array.isArray(item) ? item.map(ingredient) : ingredient(item))
  });

  const raw = mod('raw_silicon');
  const wafer = mod('silicon_wafer');
  const pcb = mod('circuit_board');
  const resonator = [wafer, 'quartz', 'amethyst_shard'];
  await recipe('raw_silicon', {
    ...shaped([' S ', 'SCS', ' S '], {S: 'sand', C: 'charcoal'}), category: 'misc'
  }, 'charcoal');
  const siliconSources = [raw, ...siliconOres.map(mod)];
  for (const [method, time] of [['smelting', 200], ['blasting', 100]]) {
    await recipe(`silicon_wafer_from_${method}`, {
      type: `minecraft:${method}`, category: 'misc', group: 'silicon_wafer',
      ingredient: siliconSources.map(ingredient), cookingtime: time, experience: 0.5,
      result: {id: wafer}
    }, ...siliconSources);
  }
  await recipe('circuit_board', {
    ...shapeless('copper_ingot', 'clay_ball', 'redstone'), result: {id: pcb, count: 2}
  }, 'redstone');

  await recipe('stc89c52rc', shaped(['IRI', 'GWG', 'IRI'], {
    I: 'iron_nugget', R: 'redstone', G: 'gold_nugget', W: wafer
  }), wafer);
  await recipe('51systemboard', shaped([' X ', 'CMC', ' P '], {
    X: mod('crystal_11_0592mhz'), C: 'copper_ingot', M: mod('stc89c52rc'), P: pcb
  }), mod('stc89c52rc'));
  await recipe('oled', shaped(['GGG', 'RWR', ' P '], {
    G: 'glass_pane', R: 'redstone', W: wafer, P: pcb
  }), pcb);
  await recipe('i2c_sensor', shaped([' G ', 'RWC', ' P '], {
    G: 'glass', R: 'redstone_torch', W: wafer, C: 'copper_ingot', P: pcb
  }), pcb);
  await recipe('redstone_port', {
    ...shaped(['CRC', ' P '], {C: 'copper_ingot', R: 'repeater', P: pcb}),
    result: {id: mod('redstone_port'), count: 2}
  }, pcb);
  await recipe('button_module', shapeless('stone_button', 'copper_ingot', pcb), pcb);
  await recipe('led_module', shapeless('redstone_torch', 'glass', pcb), pcb);
  await recipe('seven_segment', shaped(['LLL', 'LPL', 'LLL'], {L: mod('led_module'), P: pcb}), mod('led_module'));
  await recipe('ds1302', shaped([' T ', 'GWG', ' P '], {
    T: 'clock', G: 'gold_nugget', W: wafer, P: pcb
  }), pcb);
  await recipe('pcf8591', shaped([' R ', 'CWC', ' P '], {
    R: 'comparator', C: 'copper_ingot', W: wafer, P: pcb
  }), pcb);
  await recipe('ds18b20', shaped([' I ', 'CWC', ' P '], {I: 'iron_ingot', C: 'copper_ingot', W: wafer, P: pcb}), pcb);
  await recipe('manual', {...shapeless('book', 'paper', 'redstone'), category: 'misc'}, 'book');
  await recipe('compass', {...shapeless('compass'), category: 'misc'}, 'compass');
  await recipe('wire_coil', {
    ...shapeless('copper_ingot', '#minecraft:wool'), result: {id: mod('wire_coil'), count: 8}
  }, 'copper_ingot');
  await recipe('wire_cutter', shaped(['I I', ' C ', 'C C'], {I: 'iron_ingot', C: 'copper_ingot'}), mod('wire_coil'));
  await recipe('wire_relay', shaped([' C ', 'CLC', ' S '], {C: 'copper_ingot', L: 'clay_ball', S: 'stone'}), mod('wire_coil'));

  await recipe('crystal_6mhz', shapeless(resonator, 'iron_nugget'), ...resonator);
  await recipe('crystal_11_0592mhz', shapeless(resonator, 'iron_nugget', 'gold_nugget'), ...resonator);
  await recipe('crystal_12mhz', shapeless(resonator, 'iron_nugget', 'copper_ingot'), ...resonator);
  await recipe('crystal_22_1184mhz', shapeless('quartz', 'iron_nugget', 'gold_ingot'), 'quartz');
  await recipe('crystal_24mhz', shapeless('quartz', 'amethyst_shard', 'iron_nugget'), 'amethyst_shard');

  for (const id of siliconOres) {
    await json(`data/embeddeddev/loot_table/blocks/${id}.json`, {
      type: 'minecraft:block',
      pools: [{rolls: 1, entries: [{type: 'minecraft:alternatives', children: [
        {type: 'minecraft:item', name: mod(id), conditions: [{condition: 'minecraft:match_tool', predicate: {
          predicates: {'minecraft:enchantments': [{enchantments: 'minecraft:silk_touch', levels: {min: 1}}]}
        }}]},
        {type: 'minecraft:item', name: raw, functions: [
          {function: 'minecraft:apply_bonus', enchantment: 'minecraft:fortune', formula: 'minecraft:ore_drops'},
          {function: 'minecraft:explosion_decay'}
        ]}
      ]}]}],
      random_sequence: mod(`blocks/${id}`)
    });
  }
  await json('data/minecraft/tags/block/needs_stone_tool.json', {replace: false, values: siliconOres.map(mod)});
  await json('data/embeddeddev/worldgen/configured_feature/silicon_ore.json', {
    type: 'minecraft:ore', config: {
      size: 6, discard_chance_on_air_exposure: 0,
      targets: siliconOres.map((id, index) => ({
        target: {predicate_type: 'minecraft:tag_match', tag: `minecraft:${index === 0 ? 'stone' : 'deepslate'}_ore_replaceables`},
        state: {Name: mod(id)}
      }))
    }
  });
  await json('data/embeddeddev/worldgen/placed_feature/silicon_ore.json', {
    feature: mod('silicon_ore'), placement: [
      {type: 'minecraft:count', count: 6}, {type: 'minecraft:in_square'},
      {type: 'minecraft:height_range', height: {
        type: 'minecraft:trapezoid', min_inclusive: {absolute: -56}, max_inclusive: {absolute: 40}
      }},
      {type: 'minecraft:biome'}
    ]
  });
  await json('data/embeddeddev/neoforge/biome_modifier/add_silicon_ore.json', {
    type: 'neoforge:add_features', biomes: '#minecraft:is_overworld', features: mod('silicon_ore'), step: 'underground_ores'
  });
}
