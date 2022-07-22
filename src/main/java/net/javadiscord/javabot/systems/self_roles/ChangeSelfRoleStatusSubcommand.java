package net.javadiscord.javabot.systems.self_roles;

import com.dynxsty.dih4jda.interactions.commands.SlashCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.javadiscord.javabot.systems.notification.GuildNotificationService;
import net.javadiscord.javabot.util.MessageActionUtils;
import net.javadiscord.javabot.util.Responses;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Subcommand that disables all Elements on an ActionRow.
 */
public class ChangeSelfRoleStatusSubcommand extends SlashCommand.Subcommand {
	/**
	 * The constructor of this class, which sets the corresponding {@link net.dv8tion.jda.api.interactions.commands.build.SlashCommandData}.
	 */
	public ChangeSelfRoleStatusSubcommand() {
		setSubcommandData(new SubcommandData("status", "Either enables or disables all message components (thus, the self role) on a single message.")
				.addOption(OptionType.STRING, "message-id", "The message's id.", true)
				.addOption(OptionType.BOOLEAN, "disable", "Should all action rows be disabled?", true)
		);
	}

	@Override
	public void execute(@NotNull SlashCommandInteractionEvent event) {
		OptionMapping idMapping = event.getOption("message-id");
		OptionMapping disabledMapping = event.getOption("disable");
		if (idMapping == null || disabledMapping == null) {
			Responses.missingArguments(event).queue();
			return;
		}
		boolean disabled = disabledMapping.getAsBoolean();
		event.deferReply(true).queue();
		event.getChannel().retrieveMessageById(idMapping.getAsString()).queue(message -> {
			message.editMessageComponents(disabled ?
					MessageActionUtils.disableActionRows(message.getActionRows()) :
					MessageActionUtils.enableActionRows(message.getActionRows())
			).queue();
			MessageEmbed embed = buildSelfRoleStatusEmbed(event.getUser(), message, disabled);
			new GuildNotificationService(event.getGuild()).sendLogChannelNotification(embed);
			event.getHook().sendMessageEmbeds(embed).setEphemeral(true).queue();
		}, e -> Responses.error(event.getHook(), e.getMessage()));
	}

	private @NotNull MessageEmbed buildSelfRoleStatusEmbed(@NotNull User changedBy, @NotNull Message message, boolean disabled) {
		return new EmbedBuilder()
				.setAuthor(changedBy.getAsTag(), message.getJumpUrl(), changedBy.getEffectiveAvatarUrl())
				.setTitle("Self Role " + (disabled ? "disabled" : "enabled"))
				.setColor(Responses.Type.DEFAULT.getColor())
				.addField("Channel", message.getChannel().getAsMention(), true)
				.addField("Message", String.format("[Jump to Message](%s)", message.getJumpUrl()), true)
				.setTimestamp(Instant.now())
				.build();
	}
}
