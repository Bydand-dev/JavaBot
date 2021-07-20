package com.javadiscord.javabot.commands.configuation.welcome_system.subcommands;

import com.javadiscord.javabot.commands.configuation.welcome_system.WelcomeCommandHandler;
import com.javadiscord.javabot.other.Database;
import com.javadiscord.javabot.other.Embeds;
import com.javadiscord.javabot.other.Misc;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;

public class SetOverlayUrl implements WelcomeCommandHandler {

    @Override
    public void handle(SlashCommandEvent event) {

        String url = event.getOption("url").getAsString();
        if (Misc.isImage(url)) {
            new Database().queryConfig(event.getGuild().getId(), "welcome_system.image.overlayURL", url);
            event.replyEmbeds(Embeds.configEmbed(event, "Welcome Image Overlay", "Welcome Image Overlay successfully changed to ", Misc.checkImage(url), url, true)).queue();
        } else {
            event.replyEmbeds(Embeds.emptyError("```URL must be a valid HTTP(S) or Attachment URL.```", event.getUser())).queue();
        }
    }
}
