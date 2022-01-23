package net.javadiscord.javabot.systems.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyAction;
import net.javadiscord.javabot.Bot;
import net.javadiscord.javabot.Constants;
import net.javadiscord.javabot.command.Responses;
import net.javadiscord.javabot.command.SlashCommandHandler;
import net.javadiscord.javabot.data.config.guild.SlashCommandConfig;

import java.time.Instant;

/**
 * Command that displays some server information.
 */
public class ServerInfoCommand implements SlashCommandHandler {
	@Override
	public ReplyAction handle(SlashCommandEvent event) {
		if (event.getGuild() == null) return Responses.warning(event, "This can only be used in a guild.");
		var embed = buildServerInfoEmbed(event.getGuild(), Bot.config.get(event.getGuild()).getSlashCommand());
		return event.replyEmbeds(embed).addActionRow(Button.link(Constants.WEBSITE_LINK, "Website"));
	}

	private MessageEmbed buildServerInfoEmbed(Guild guild, SlashCommandConfig config) {
		long textChannels = guild.getTextChannels().size();
		long voiceChannels = guild.getVoiceChannels().size();
		long categories = guild.getCategories().size();
		long channels = guild.getChannels().size() - categories;
		return new EmbedBuilder()
				.setColor(config.getDefaultColor())
				.setThumbnail(guild.getIconUrl())
				.setAuthor(guild.getName(), null, guild.getIconUrl())
				.setTitle("Server Information")
				.addField("Name", String.format("```%s```", guild.getName()), true)
				.addField("Owner", guild.getOwner().getAsMention(), true)
				.addField("ID", String.format("```%s```", guild.getIdLong()), false)
				.addField("Roles", String.format("%s Roles", guild.getRoles().size() - 1L), true)
				.addField("Channel Count",
						String.format(
								"```%s Channels, %s Categories" +
										"\n→ Text: %s" +
										"\n→ Voice: %s```", channels, categories, textChannels, voiceChannels), false)
				.addField("Member Count", "```" + guild.getMemberCount() + " members```", false)
				.addField("Server created on", String.format("<t:%s:f>", guild.getTimeCreated().toInstant()), false)
				.setTimestamp(Instant.now())
				.build();
	}
}
