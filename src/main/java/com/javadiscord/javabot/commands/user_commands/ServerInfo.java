package com.javadiscord.javabot.commands.user_commands;

import com.javadiscord.javabot.other.Constants;
import com.javadiscord.javabot.other.TimeUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.Button;

import java.awt.*;
import java.util.Date;

public class ServerInfo {

    public static void execute(SlashCommandEvent event) {

        long roleCount = event.getGuild().getRoles().stream().count() - 1;
        long catCount = event.getGuild().getCategories().stream().count();
        long textChannelCount = event.getGuild().getTextChannels().stream().count();
        long voiceChannelCount = event.getGuild().getVoiceChannels().stream().count();
        long channelCount = event.getGuild().getChannels().stream().count() - catCount;

        String guildDate = event.getGuild().getTimeCreated().format(TimeUtils.STANDARD_FORMATTER);
        String createdDiff = " (" + new TimeUtils().formatDurationToNow(event.getGuild().getTimeCreated()) + ")";

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(0x2F3136))
                .setThumbnail(event.getGuild().getIconUrl())
                .setAuthor(event.getGuild().getName(), null, event.getGuild().getIconUrl())
                .addField("Name", "```" + event.getGuild().getName() + "```", true)
                .addField("Owner", "```" + event.getGuild().getOwner().getUser().getAsTag() + "```", true)
                .addField("ID", "```" + event.getGuild().getId() + "```", false)
                .addField("Region", "```" + event.getGuild().getRegion() + "```", true)
                .addField("Roles", "```" + roleCount + " Roles```", true)
                .addField("Channel Count", "```" + channelCount + " Channel, " + catCount + " Categories" +
                        "\n → Text: " + textChannelCount +
                        "\n → Voice: " + voiceChannelCount + "```", false)

                .addField("Member Count", "```" + event.getGuild().getMemberCount() + " members```", false)
                .addField("Server created on", "```" + guildDate + createdDiff + "```", false)
                .setTimestamp(new Date().toInstant());

        if (event.getGuild().getId().equals("648956210850299986")) {
            event.replyEmbeds(eb.build()).addActionRow(Button.link(Constants.WEBSITE, "Website")).queue();
        } else { event.replyEmbeds(eb.build()).queue(); }
    }
}
