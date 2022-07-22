package net.javadiscord.javabot.systems.moderation;

import com.dynxsty.dih4jda.interactions.commands.SlashCommand;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.javadiscord.javabot.util.Checks;
import net.javadiscord.javabot.util.Responses;

/**
 * Abstract class, that represents a single moderation command.
 */
public abstract class ModerateCommand extends SlashCommand {
	private boolean allowThreads = true;

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		if (event.getGuild() == null) {
			Responses.guildOnly(event).queue();
			return;
		}
		Member member = event.getMember();
		if (allowThreads) {
			if (event.getChannelType() != ChannelType.TEXT && !event.getChannelType().isThread()) {
				Responses.error(event, "This command can only be performed in a server text channel or thread.").queue();
				return;
			}
		} else {
			if (event.getChannelType() != ChannelType.TEXT) {
				Responses.error(event, "This command can only be performed in a server text channel.").queue();
				return;
			}
		}
		handleModerationCommand(event, member).queue();
	}

	protected void setAllowThreads(boolean allowThreads) {
		this.allowThreads = allowThreads;
	}

	protected abstract ReplyCallbackAction handleModerationCommand(SlashCommandInteractionEvent event, Member commandUser);
}
