package net.javadiscord.javabot.systems.help.forum;

import com.dynxsty.dih4jda.interactions.ComponentIdBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.javadiscord.javabot.Bot;
import net.javadiscord.javabot.data.config.guild.HelpConfig;
import net.javadiscord.javabot.data.h2db.DbActions;
import net.javadiscord.javabot.systems.help.HelpExperienceService;
import net.javadiscord.javabot.systems.help.model.HelpTransactionMessage;
import net.javadiscord.javabot.util.ExceptionLogger;
import net.javadiscord.javabot.util.MessageActionUtils;
import net.javadiscord.javabot.util.Responses;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages all interactions regarding the help forum system.
 *
 * @param postThread The posts' {@link ThreadChannel}.
 */
public record ForumHelpManager(ThreadChannel postThread) {
	/**
	 * Static String that contains the Thank Message Text.
	 */
	public static final String THANK_MESSAGE_TEXT = "Before your post will be closed, would you like to express your gratitude to any of the people who helped you? When you're done, click **I'm done here. Close this post!**.";

	/**
	 * The identifier used for all help thanks-related buttons.
	 */
	public static final String HELP_THANKS_IDENTIFIER = "forum-help-thank";

	/**
	 * Builds and replies {@link ActionRow}s with all members which helped the
	 * owner of the {@link ForumHelpManager#postThread} forum post.
	 *
	 * @param callback The callback to reply to.
	 * @param helpers  The list of helpers to thank.
	 * @return The {@link ReplyCallbackAction}.
	 */
	public ReplyCallbackAction replyHelpThanks(IReplyCallback callback, @NotNull List<Member> helpers) {
		List<ItemComponent> helperThanksButtons = new ArrayList<>(20);
		for (Member helper : helpers.subList(0, Math.min(helpers.size(), 20))) {
			helperThanksButtons.add(Button.success(ComponentIdBuilder.build(HELP_THANKS_IDENTIFIER, postThread.getId(), helper.getId()), helper.getEffectiveName())
					.withEmoji(Emoji.fromUnicode("❤"))
			);
		}
		ActionRow controlsRow = ActionRow.of(
				Button.primary(ComponentIdBuilder.build(HELP_THANKS_IDENTIFIER, postThread.getId(), "done"), "I'm done here. Close this post!"),
				Button.danger(ComponentIdBuilder.build(HELP_THANKS_IDENTIFIER, postThread.getId(), "cancel"), "Cancel Closing")
		);
		List<ActionRow> rows = new ArrayList<>();
		rows.add(controlsRow);
		rows.addAll(MessageActionUtils.toActionRows(helperThanksButtons));
		return callback.reply(THANK_MESSAGE_TEXT)
				.setComponents(rows);
	}

	/**
	 * Closes the {@link ForumHelpManager#postThread}.
	 *
	 * @param callback    The callback to reply to.
	 * @param withHelpers Whether the help-thanks message should be displayed.
	 * @param reason      The reason for closing this post.
	 */
	public void close(IReplyCallback callback, boolean withHelpers, @Nullable String reason) {
		List<Member> helpers = getPostHelpers();
		if (withHelpers && !helpers.isEmpty()) {
			replyHelpThanks(callback, helpers).queue();
			return;
		}
		Responses.info(callback, "Post Closed", "This post has been closed by %s%s", callback.getUser().getAsMention(), reason != null ? " for the following reason:\n> " + reason : ".")
				.setEphemeral(false)
				.queue(s -> postThread.getManager().setLocked(true).setArchived(true).queue());
	}

	/**
	 * Thanks a single user.
	 *
	 * @param event      The {@link ButtonInteractionEvent} that was fired.
	 * @param postThread The {@link ThreadChannel} post.
	 * @param helperId   The helpers' discord id.
	 */
	public void thankHelper(@NotNull ButtonInteractionEvent event, ThreadChannel postThread, long helperId) {
		event.getJDA().retrieveUserById(helperId).queue(helper -> {
			// First insert the new thanks data.
			try {
				DbActions.update(
						"INSERT INTO help_channel_thanks (reservation_id, user_id, channel_id, helper_id) VALUES (?, ?, ?, ?)",
						postThread.getIdLong(),
						postThread.getOwnerIdLong(),
						postThread.getIdLong(),
						helper.getIdLong()
				);
				HelpConfig config = Bot.getConfig().get(event.getGuild()).getHelpConfig();
				HelpExperienceService service = new HelpExperienceService(Bot.getDataSource());
				// Perform experience transactions
				service.performTransaction(helper.getIdLong(), config.getThankedExperience(), HelpTransactionMessage.GOT_THANKED, event.getGuild());
				service.performTransaction(postThread.getOwnerIdLong(), config.getThankExperience(), HelpTransactionMessage.THANKED_USER, event.getGuild());
			} catch (SQLException e) {
				ExceptionLogger.capture(e, getClass().getSimpleName());
				Bot.getConfig().get(event.getGuild()).getModerationConfig().getLogChannel().sendMessageFormat(
						"Could not record user %s thanking %s for help in post %s: %s",
						postThread.getOwner().getUser().getAsTag(),
						helper.getAsTag(),
						postThread.getAsMention(),
						e.getMessage()
				).queue();
			}
		});
	}


	private @NotNull List<Member> getPostHelpers() {
		List<Member> helpers = new ArrayList<>(20);
		List<Message> messages = ForumHelpListener.HELP_POST_MESSAGES.get(postThread.getIdLong());
		if (messages == null) return helpers;
		for (Message message : messages) {
			if (message.getMember() == null || message.getMember().getIdLong() == postThread.getOwnerIdLong() ||
					helpers.contains(message.getMember())
			) {
				continue;
			}
			helpers.add(message.getMember());
		}
		return helpers;
	}
}
