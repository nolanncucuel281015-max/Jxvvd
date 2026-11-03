package fr.evotool.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.evotool.EvoTool;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Le manuel d'Evo Tool : un sommaire de volumes à gauche, et à droite les pages du volume choisi, avec des images
 * (vraies captures du jeu), des objets (survole-les pour leur nom) et des recettes dessinées. Le contenu est dans
 * assets/evotool/manual/&lt;langue&gt;.json (le français sert par défaut).
 */
public class ManualScreen extends Screen {
	private static final int WIDTH = 312;
	private static final int HEIGHT = 206;
	private static final int TOC_WIDTH = 96;
	private static final int GOLD = 0xFFFFE08A;
	private static final int INK = 0xFFE6DAFF;

	private record Recipe(List<ItemStack> grid, ItemStack result) {
	}

	private record Page(String title, String text, Identifier image, int imageWidth, int imageHeight, List<ItemStack> items, Recipe recipe) {
	}

	private record Chapter(String title, ItemStack icon, List<Page> pages) {
	}

	private static List<Chapter> chapters;
	/** Le volume et la page ouverts (on retrouve sa page en rouvrant le manuel). */
	private static int chapter;
	private static int page;

	private int left;
	private int top;
	private ButtonWidget previous;
	private ButtonWidget next;

	public ManualScreen() {
		super(Text.translatable("item.evotool.evotool_manual"));
	}

	public static void register() {
		fr.evotool.ClientHooks.openManual = () -> MinecraftClient.getInstance().setScreen(new ManualScreen());
	}

	// ------------------------------------------------------------------ contenu

	private static List<Chapter> load(MinecraftClient client) {
		String language = client.getLanguageManager().getLanguage();
		for (String code : new String[] {language, "fr_fr"}) {
			Optional<Resource> resource = client.getResourceManager().getResource(EvoTool.id("manual/" + code + ".json"));
			if (resource.isEmpty()) {
				continue;
			}
			try (Reader reader = resource.get().getReader()) {
				return parse(JsonParser.parseReader(reader).getAsJsonObject());
			} catch (Exception e) {
				EvoTool.LOGGER.error("Manuel illisible : {}", code, e);
			}
		}
		return List.of(new Chapter("?", ItemStack.EMPTY, List.of(new Page("Manuel introuvable", "", null, 0, 0, List.of(), null))));
	}

	private static List<Chapter> parse(JsonObject root) {
		List<Chapter> list = new ArrayList<>();
		for (JsonElement c : root.getAsJsonArray("chapters")) {
			JsonObject chapter = c.getAsJsonObject();
			List<Page> pages = new ArrayList<>();
			for (JsonElement p : chapter.getAsJsonArray("pages")) {
				JsonObject o = p.getAsJsonObject();
				Identifier image = o.has("image") ? Identifier.of(o.get("image").getAsString()) : null;
				Recipe recipe = null;
				if (o.has("recipe")) {
					JsonObject r = o.getAsJsonObject("recipe");
					recipe = new Recipe(stacks(r.getAsJsonArray("grid")), stack(r.get("result").getAsString()));
				}
				pages.add(new Page(o.get("title").getAsString(), o.has("text") ? o.get("text").getAsString() : "", image,
						o.has("imageWidth") ? o.get("imageWidth").getAsInt() : 256, o.has("imageHeight") ? o.get("imageHeight").getAsInt() : 144,
						o.has("items") ? stacks(o.getAsJsonArray("items")) : List.of(), recipe));
			}
			list.add(new Chapter(chapter.get("title").getAsString(), stack(chapter.get("icon").getAsString()), pages));
		}
		return list;
	}

	private static List<ItemStack> stacks(JsonArray array) {
		List<ItemStack> stacks = new ArrayList<>();
		for (JsonElement e : array) {
			stacks.add(stack(e.getAsString()));
		}
		return stacks;
	}

	private static ItemStack stack(String id) {
		if (id.isEmpty()) {
			return ItemStack.EMPTY;
		}
		return Registries.ITEM.getOptionalValue(Identifier.of(id)).map(ItemStack::new).orElse(ItemStack.EMPTY);
	}

	// ------------------------------------------------------------------ écran

	@Override
	protected void init() {
		if (chapters == null) {
			chapters = load(client);
		}
		chapter = Math.min(chapter, chapters.size() - 1);
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		for (int i = 0; i < chapters.size(); i++) {
			int index = i;
			addDrawableChild(ButtonWidget.builder(Text.literal(chapters.get(i).title()), b -> open(index, 0))
					.dimensions(left + 22, top + 20 + i * 16, TOC_WIDTH - 26, 15).build());
		}
		previous = addDrawableChild(ButtonWidget.builder(Text.literal("◀"), b -> turn(-1))
				.dimensions(left + TOC_WIDTH + 8, top + HEIGHT - 20, 22, 14).build());
		next = addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> turn(1))
				.dimensions(left + WIDTH - 30, top + HEIGHT - 20, 22, 14).build());
	}

	/** Ouvre ce volume à cette page (public pour le test visuel). */
	public void open(int chapterIndex, int pageIndex) {
		chapter = Math.max(0, Math.min(chapterIndex, chapters.size() - 1));
		page = Math.max(0, Math.min(pageIndex, chapters.get(chapter).pages().size() - 1));
	}

	/** Page suivante (ou précédente) ; au bout d'un volume, on passe au suivant. */
	private void turn(int direction) {
		int target = page + direction;
		if (target >= chapters.get(chapter).pages().size() && chapter + 1 < chapters.size()) {
			open(chapter + 1, 0);
		} else if (target < 0 && chapter > 0) {
			open(chapter - 1, chapters.get(chapter - 1).pages().size() - 1);
		} else {
			open(chapter, target);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		// le livre : couverture violette cerclée d'or, sommaire et page
		context.fill(left - 4, top - 4, left + WIDTH + 4, top + HEIGHT + 4, 0xFF2A0F48);
		context.fill(left - 3, top - 3, left + WIDTH + 3, top + HEIGHT + 3, 0xFFFFD23A);
		context.fill(left - 2, top - 2, left + WIDTH + 2, top + HEIGHT + 2, 0xFF2A0F48);
		context.fill(left, top, left + TOC_WIDTH, top + HEIGHT, 0xF0160E22);
		context.fill(left + TOC_WIDTH + 2, top, left + WIDTH, top + HEIGHT, 0xF01C1428);
		context.fill(left + TOC_WIDTH, top, left + TOC_WIDTH + 2, top + HEIGHT, 0xFFFFD23A);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (previous != null) {
			previous.active = chapter > 0 || page > 0;
			next.active = chapter < chapters.size() - 1 || page < chapters.get(chapter).pages().size() - 1;
		}
		super.render(context, mouseX, mouseY, delta);
		context.drawText(textRenderer, Text.translatable("gui.evotool.manual.contents"), left + 8, top + 7, GOLD, false);
		ItemStack hovered = ItemStack.EMPTY;
		// les icônes des volumes ; celui qui est ouvert est encadré
		for (int i = 0; i < chapters.size(); i++) {
			int y = top + 20 + i * 16;
			if (i == chapter) {
				context.fill(left + 2, y - 1, left + 20, y + 16, 0x80FFD23A);
			}
			context.drawItem(chapters.get(i).icon(), left + 3, y);
		}
		Chapter current = chapters.get(chapter);
		Page shown = current.pages().get(page);
		int x0 = left + TOC_WIDTH + 10;
		int pageWidth = WIDTH - TOC_WIDTH - 20;
		int y = top + 7;
		context.drawText(textRenderer, Text.literal(shown.title()), x0, y, GOLD, false);
		y += 13;
		if (shown.image() != null) {
			int w = pageWidth;
			int h = w * shown.imageHeight() / shown.imageWidth();
			if (h > 96) {
				h = 96;
				w = h * shown.imageWidth() / shown.imageHeight();
			}
			int ix = x0 + (pageWidth - w) / 2;
			context.fill(ix - 1, y - 1, ix + w + 1, y + h + 1, 0xFFFFD23A);
			context.drawTexture(RenderPipelines.GUI_TEXTURED, shown.image(), ix, y, 0.0F, 0.0F, w, h,
					shown.imageWidth(), shown.imageHeight(), shown.imageWidth(), shown.imageHeight());
			y += h + 5;
		}
		if (shown.recipe() != null) {
			hovered = drawRecipe(context, shown.recipe(), x0 + 20, y, mouseX, mouseY, hovered);
			y += 3 * 18 + 6;
		}
		if (!shown.items().isEmpty()) {
			int ix = x0;
			for (ItemStack stack : shown.items()) {
				context.fill(ix, y, ix + 18, y + 18, 0xFF3A3150);
				context.fill(ix + 1, y + 1, ix + 17, y + 17, 0xFF241A3A);
				context.drawItem(stack, ix + 1, y + 1);
				if (inside(mouseX, mouseY, ix, y, 18, 18)) {
					hovered = stack;
				}
				ix += 20;
			}
			y += 22;
		}
		// le texte, en plus petit pour tenir sur la page
		float scale = 0.8F;
		List<OrderedText> lines = textRenderer.wrapLines(Text.literal(shown.text()), (int) (pageWidth / scale));
		int maxLines = (int) ((top + HEIGHT - 26 - y) / (10 * scale));
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(x0, y);
		context.getMatrices().scale(scale, scale);
		for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
			context.drawText(textRenderer, lines.get(i), 0, i * 10, INK, false);
		}
		context.getMatrices().popMatrix();
		Text counter = Text.translatable("gui.evotool.manual.page", page + 1, current.pages().size());
		context.drawText(textRenderer, counter, left + TOC_WIDTH + (WIDTH - TOC_WIDTH - textRenderer.getWidth(counter)) / 2,
				top + HEIGHT - 17, 0xFFA594C8, false);
		if (!hovered.isEmpty()) {
			context.drawItemTooltip(textRenderer, hovered, mouseX, mouseY);
		}
	}

	/** Une recette en grille 3 x 3, une flèche et le résultat. */
	private ItemStack drawRecipe(DrawContext context, Recipe recipe, int x, int y, int mouseX, int mouseY, ItemStack hovered) {
		for (int i = 0; i < 9; i++) {
			int sx = x + (i % 3) * 18;
			int sy = y + (i / 3) * 18;
			context.fill(sx, sy, sx + 18, sy + 18, 0xFF3A3150);
			context.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF241A3A);
			ItemStack stack = i < recipe.grid().size() ? recipe.grid().get(i) : ItemStack.EMPTY;
			if (!stack.isEmpty()) {
				context.drawItem(stack, sx + 1, sy + 1);
				if (inside(mouseX, mouseY, sx, sy, 18, 18)) {
					hovered = stack;
				}
			}
		}
		int ax = x + 3 * 18 + 8;
		int ay = y + 25;
		context.fill(ax, ay, ax + 18, ay + 4, GOLD);
		for (int i = 0; i < 5; i++) {
			context.fill(ax + 18 + i, ay - 3 + i, ax + 19 + i, ay + 7 - i, GOLD);
		}
		int rx = ax + 30;
		int ry = y + 18;
		context.fill(rx - 4, ry - 4, rx + 22, ry + 22, 0xFFFFD23A);
		context.fill(rx - 3, ry - 3, rx + 21, ry + 21, 0xFF241A3A);
		context.drawItem(recipe.result(), rx + 1, ry + 1);
		if (inside(mouseX, mouseY, rx - 4, ry - 4, 26, 26)) {
			hovered = recipe.result();
		}
		return hovered;
	}

	private static boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}
}
