package br.hirozn.bauchefe;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/** Grupos usados pela moldura: uma cenoura na moldura aceita a categoria inteira. */
public enum Categoria {
	MACAW("Móveis e decoração", Items.PAINTING),
	EQUIPAMENTO("Ferramentas e armaduras", Items.IRON_PICKAXE),
	COMIDA("Comida e plantações", Items.CARROT),
	MINERIOS("Minérios e lingotes", Items.IRON_INGOT),
	MADEIRA("Madeiras", Items.OAK_LOG),
	PEDRAS("Pedras e construção", Items.STONE_BRICKS),
	REDSTONE("Redstone", Items.REDSTONE),
	MOBS("Drops de mob", Items.BONE),
	DIVERSOS("Diversos", Items.COMPASS);

	public final String nome;
	public final Item icone;

	Categoria(String nome, Item icone) {
		this.nome = nome;
		this.icone = icone;
	}

	private static TagKey<Item> c(String path) {
		return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
	}

	private static final TagKey<Item> ORES = c("ores"), INGOTS = c("ingots"), RAW = c("raw_materials"),
			GEMS = c("gems"), NUGGETS = c("nuggets"), CROPS = c("crops"), SEEDS = c("seeds"), FOODS = c("foods"),
			TOOLS = c("tools"), ARMORS = c("armors"), DUSTS = c("dusts");

	private static final Set<String> MOB_DROPS = Set.of("string", "bone", "bone_meal", "rotten_flesh", "gunpowder",
			"spider_eye", "ender_pearl", "slime_ball", "leather", "feather", "arrow", "blaze_rod", "blaze_powder",
			"phantom_membrane", "ghast_tear", "egg", "ink_sac", "glow_ink_sac", "magma_cream", "rabbit_hide",
			"rabbit_foot", "shulker_shell", "breeze_rod", "armadillo_scute", "turtle_scute", "nautilus_shell",
			"prismarine_shard", "prismarine_crystals", "honeycomb", "wither_skeleton_skull", "skeleton_skull");

	private static final String[] REDSTONE_IDS = {"redstone", "repeater", "comparator", "piston", "observer", "hopper",
			"dispenser", "dropper", "lever", "_button", "pressure_plate", "rail", "tnt", "daylight_detector", "target",
			"note_block", "tripwire_hook", "sculk_sensor", "crafter", "lightning_rod"};

	private static final String[] PEDRA_IDS = {"stone", "cobble", "deepslate", "andesite", "diorite", "granite", "tuff",
			"brick", "sand", "gravel", "dirt", "clay", "terracotta", "concrete", "glass", "calcite", "basalt",
			"blackstone", "netherrack", "end_stone", "prismarine", "quartz_block", "mud", "obsidian"};

	public static Categoria de(ItemStack stack) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		String ns = id.getNamespace(), p = id.getPath();
		if (ns.startsWith("mcw")) return MACAW;
		if (stack.is(TOOLS) || stack.is(ARMORS) || stack.has(DataComponents.MAX_DAMAGE)) return EQUIPAMENTO;
		if (stack.has(DataComponents.FOOD) || stack.is(CROPS) || stack.is(SEEDS) || stack.is(FOODS)
				|| p.endsWith("_seeds") || p.equals("wheat") || p.equals("sugar_cane") || p.equals("pumpkin")
				|| p.equals("melon") || p.equals("cocoa_beans")) return COMIDA;
		if (stack.is(ORES) || stack.is(INGOTS) || stack.is(RAW) || stack.is(GEMS) || stack.is(NUGGETS)
				|| p.endsWith("_ore") || p.startsWith("raw_") || p.equals("coal") || p.equals("charcoal")
				|| p.equals("lapis_lazuli") || p.equals("emerald") || p.equals("diamond") || p.equals("quartz")
				|| p.equals("amethyst_shard") || p.equals("netherite_scrap")) return MINERIOS;
		if (stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS) || stack.is(ItemTags.WOODEN_SLABS)
				|| stack.is(ItemTags.WOODEN_STAIRS) || stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.LEAVES)
				|| p.equals("stick")) return MADEIRA;
		if (MOB_DROPS.contains(p)) return MOBS;
		if (stack.is(DUSTS) && p.contains("redstone")) return REDSTONE;
		for (String r : REDSTONE_IDS) if (p.contains(r)) return REDSTONE;
		for (String s : PEDRA_IDS) if (p.contains(s)) return PEDRAS;
		return DIVERSOS;
	}
}
