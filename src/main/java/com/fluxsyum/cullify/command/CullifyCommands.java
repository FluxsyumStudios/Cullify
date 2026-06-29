package com.fluxsyum.cullify.command;

import com.fluxsyum.cullify.benchmark.BenchmarkManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class CullifyCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("cullify")
                .then(Commands.literal("benchmark")
                    .then(Commands.literal("start")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                            .executes(context -> {
                                int seconds = IntegerArgumentType.getInteger(context, "seconds");
                                if (BenchmarkManager.isRunning()) {
                                    context.getSource().sendFailure(Component.literal("Benchmark is already running!"));
                                    return 0;
                                }
                                BenchmarkManager.start(seconds);
                                context.getSource().sendSuccess(() -> Component.literal("§aStarted Cullify benchmark for " + seconds + " seconds."), false);
                                return 1;
                            })
                        )
                    )
                    .then(Commands.literal("stop")
                        .executes(context -> {
                            if (!BenchmarkManager.isRunning()) {
                                context.getSource().sendFailure(Component.literal("No benchmark is currently running."));
                                return 0;
                            }
                            BenchmarkManager.stop();
                            context.getSource().sendSuccess(() -> Component.literal("§eBenchmark stopped manually."), false);
                            return 1;
                        })
                    )
                )
        );
    }
}
