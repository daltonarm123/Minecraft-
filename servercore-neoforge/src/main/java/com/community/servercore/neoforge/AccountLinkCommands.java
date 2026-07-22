package com.community.servercore.neoforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

final class AccountLinkCommands {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private AccountLinkCommands() {
    }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("link")
                .then(Commands.argument("code", StringArgumentType.word())
                        .executes(context -> linkAccount(
                                context.getSource(),
                                StringArgumentType.getString(context, "code")))));
    }

    private static int linkAccount(CommandSourceStack source, String code)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        String apiBaseUrl = environment("SERVERCORE_API_URL", "http://127.0.0.1:8000");
        String apiKey = environment("SERVERCORE_API_KEY", "");
        String endpoint = stripTrailingSlash(apiBaseUrl) + "/api/portal/link-confirmations";
        String payload = "{"
                + "\"code\":\"" + jsonEscape(code.trim().toUpperCase()) + "\","
                + "\"player_id\":\"" + player.getUUID() + "\","
                + "\"username\":\"" + jsonEscape(player.getName().getString()) + "\""
                + "}";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload));
        if (!apiKey.isBlank()) {
            requestBuilder.header("X-ServerCore-Key", apiKey);
        }

        source.sendSuccess(() -> Component.literal("Checking your ServerCore link code..."), false);
        HTTP_CLIENT.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> server.execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.literal(
                                "The account service could not be reached. Try again in a moment."));
                        return;
                    }
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        source.sendSuccess(() -> Component.literal(
                                "Minecraft account linked to Discord successfully."), false);
                    } else {
                        source.sendFailure(Component.literal(
                                "Account link failed. Check that the code is current and try again."));
                    }
                }));
        return 1;
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
