package dripleaffinder;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import java.util.function.Consumer;

final class Commands {
    static <S> LiteralArgumentBuilder<S> tree(Finder finder) {
        return LiteralArgumentBuilder.<S>literal("dripleaf")
            .executes(c -> run(finder::status))
            .then(LiteralArgumentBuilder.<S>literal("on").executes(c -> run(() -> finder.setEnabled(true))))
            .then(LiteralArgumentBuilder.<S>literal("off").executes(c -> run(() -> finder.setEnabled(false))))
            .then(toggle("masked", value -> finder.set("masked", value)))
            .then(toggle("azalea", value -> finder.set("azalea", value)))
            .then(LiteralArgumentBuilder.<S>literal("lush").executes(c -> run(finder::listNearestLush))
                .then(LiteralArgumentBuilder.<S>literal("on").executes(c -> run(() -> finder.set("lush", true))))
                .then(LiteralArgumentBuilder.<S>literal("off").executes(c -> run(() -> finder.set("lush", false)))))
            .then(LiteralArgumentBuilder.<S>literal("predictions").executes(c -> run(finder::listNearest)))
            .then(LiteralArgumentBuilder.<S>literal("seed").executes(c -> run(finder.search::showSeed))
                .then(LiteralArgumentBuilder.<S>literal("clear").executes(c -> run(() -> finder.search.setSeed(null))))
                .then(RequiredArgumentBuilder.<S, String>argument("seed", StringArgumentType.word())
                    .executes(c -> run(() -> finder.search.setSeed(StringArgumentType.getString(c, "seed"))))))
            .then(LiteralArgumentBuilder.<S>literal("version").executes(c -> run(finder.search::showVersion))
                .then(RequiredArgumentBuilder.<S, String>argument("version", StringArgumentType.word())
                    .executes(c -> run(() -> finder.search.setVersion(StringArgumentType.getString(c, "version"))))))
            .then(LiteralArgumentBuilder.<S>literal("search")
                .executes(c -> run(() -> finder.search.start(Minecraft.getInstance(), SeedSearch.DEFAULT_RADIUS)))
                .then(LiteralArgumentBuilder.<S>literal("stop").executes(c -> run(finder.search::stop)))
                .then(RequiredArgumentBuilder.<S, Integer>argument("radius", IntegerArgumentType.integer(64, SeedSearch.MAX_RADIUS))
                    .executes(c -> run(() -> finder.search.start(Minecraft.getInstance(), IntegerArgumentType.getInteger(c, "radius"))))));
    }

    private static <S> LiteralArgumentBuilder<S> toggle(String name, Consumer<Boolean> set) {
        return LiteralArgumentBuilder.<S>literal(name)
            .then(LiteralArgumentBuilder.<S>literal("on").executes(c -> run(() -> set.accept(true))))
            .then(LiteralArgumentBuilder.<S>literal("off").executes(c -> run(() -> set.accept(false))));
    }

    private static int run(Runnable action) {
        action.run();
        return 1;
    }
}
