package net.javadiscord.javabot.systems.moderation.report;

import com.dynxsty.dih4jda.interactions.ComponentIdBuilder;
import com.dynxsty.dih4jda.interactions.components.ButtonHandler;
import com.dynxsty.dih4jda.interactions.components.ModalHandler;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.javadiscord.javabot.Bot;
import net.javadiscord.javabot.data.config.GuildConfig;
import net.javadiscord.javabot.data.config.guild.ModerationConfig;
import net.javadiscord.javabot.util.InteractionUtils;
import net.javadiscord.javabot.util.Responses;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Manages all interactions regarding the report-system.
 */
@Slf4j
public class ReportManager implements ButtonHandler, ModalHandler {

	@Override
	public void handleButton(@NotNull ButtonInteractionEvent event, Button button) {
		event.deferReply(true).queue();
		String[] id = ComponentIdBuilder.split(event.getComponentId());
		ThreadChannel thread = event.getGuild().getThreadChannelById(id[1]);
		if (thread == null) {
			Responses.error(event.getHook(), "Could not find the corresponding thread channel.").queue();
			return;
		}
		Responses.info(event.getHook(), "Report resolved", "Successfully resolved this report!").queue();
		event.getMessage().editMessageComponents(ActionRow.of(Button.secondary("report-resolved", "Resolved by " + event.getUser().getAsTag()).asDisabled())).queue();
		thread.sendMessage("This thread was resolved by " + event.getUser().getAsMention()).queue(
				success -> thread.getManager()
						.setName(String.format("[Resolved] %s", thread.getName()))
						.setArchived(true)
						.queue()
		);
	}

	@Override
	public void handleModal(ModalInteractionEvent event, List<ModalMapping> values) {
		event.deferReply(true).queue();
		String[] id = ComponentIdBuilder.split(event.getModalId());
		switch (id[1]) {
			case "user" -> handleUserReport(event.getHook(), event.getValue("reason").getAsString(), id[2]);
			case "message" -> handleMessageReport(event, id[2]);
		}
	}

	/**
	 * Builds a {@link Modal} for reporting a user using a context menu.
	 * The modal is supposed to ask for report details.
	 *
	 * @param event The {@link UserContextInteractionEvent} fired when reporting the user
	 * @return the built {@link Modal}
	 */
	protected Modal buildUserReportModal(UserContextInteractionEvent event) {
		TextInput messageInput = TextInput.create("reason", "Report Description", TextInputStyle.PARAGRAPH)
				.setMaxLength(MessageEmbed.VALUE_MAX_LENGTH)
				.build();
		String title = "Report " + event.getTarget().getAsTag();
		return Modal.create(ComponentIdBuilder.build("report", "user", event.getTarget().getId()), title.substring(0, Math.min(title.length(), Modal.MAX_TITLE_LENGTH)))
				.addActionRows(ActionRow.of(messageInput))
				.build();
	}

	/**
	 * Builds a {@link Modal} for reporting a message using a context menu.
	 * The modal is supposed to ask for report details.
	 *
	 * @param event The {@link MessageContextInteractionEvent} fired when reporting the message
	 * @return the built {@link Modal}
	 */
	protected Modal buildMessageReportModal(MessageContextInteractionEvent event) {
		String title = "Report message";
		Member targetMember = event.getTarget().getMember();
		if (targetMember != null) {
			title += " from " + targetMember.getUser().getAsTag();
		}
		TextInput messageInput = TextInput.create("reason", "Report Description", TextInputStyle.PARAGRAPH)
				.setMaxLength(MessageEmbed.VALUE_MAX_LENGTH)
				.build();
		return Modal.create(ComponentIdBuilder.build("report", "message", event.getTarget().getId()), title.substring(0, Math.min(title.length(), Modal.MAX_TITLE_LENGTH)))
				.addActionRows(ActionRow.of(messageInput))
				.build();
	}

	/**
	 * Handles a single user report.
	 *
	 * @param hook The {@link InteractionHook} to respond to.
	 * @param reason The reason for reporting this user.
	 * @param targetId The targeted user's id.
	 */
	protected void handleUserReport(InteractionHook hook, String reason, String targetId) {
		if (reason.isBlank()) {
			Responses.error(hook, "No report reason was provided.").queue();
			return;
		}
		hook.getJDA().retrieveUserById(targetId).queue(target -> {
			GuildConfig config = Bot.config.get(hook.getInteraction().getGuild());
			var embed = buildReportEmbed(target, hook.getInteraction().getUser(), reason, hook.getInteraction().getChannel());
			embed.setTitle(String.format("%s reported %s", hook.getInteraction().getUser().getName(), target.getName()));
			MessageChannel reportChannel = config.getModeration().getReportChannel();
			reportChannel.sendMessageEmbeds(embed.build())
					.queue(m -> this.createReportThread(m, target.getIdLong(), config.getModeration()));
			embed.setDescription("Successfully reported " + "`" + target.getAsTag() + "`!\nYour report has been send to our Moderators");
			hook.sendMessageEmbeds(embed.build()).queue();
		}, failure -> {
			Responses.error(hook, "The user to report seems not to exist any more.").queue();
			log.warn("Cannot retrieve user {} when reporting them", targetId, failure);
		});
	}

	private void handleMessageReport(ModalInteractionEvent event, String messageId) {
		String reason = event.getValue("reason").getAsString();
		if (reason.isBlank()) {
			Responses.error(event.getHook(), "No report reason was provided.").queue();
			return;
		}
		event.getMessageChannel().retrieveMessageById(messageId).queue(target -> {
			GuildConfig config = Bot.config.get(event.getGuild());
			EmbedBuilder embed = buildReportEmbed(target.getAuthor(), event.getUser(), reason, event.getTextChannel());
			embed.setTitle(String.format("%s reported a Message from %s", event.getUser().getName(), target.getAuthor().getName()));
			embed.addField("Message", String.format("[Jump to Message](%s)", target.getJumpUrl()), false);
			MessageChannel reportChannel = config.getModeration().getReportChannel();
			reportChannel.sendMessageEmbeds(embed.build()).queue(m -> createReportThread(m, target.getAuthor().getIdLong(), config.getModeration()));
			embed.setDescription("Successfully reported " + "`" + target.getAuthor().getAsTag() + "`!\nYour report has been send to our Moderators");
			event.getHook().sendMessageEmbeds(embed.build()).queue();
		}, failure -> {
			Responses.error(event.getHook(), "The author of the message to report seems not to exist any more.").queue();
			log.info("Cannot retrieve reported message {} in channel {} - the message might have been deleted", messageId, event.getChannel(), failure);
		});

	}

	private ActionRow setComponents(long targetId, long threadId) {
		return ActionRow.of(
				Button.secondary(ComponentIdBuilder.build("resolve-report", threadId), "Mark as resolved"),
				Button.danger(String.format(InteractionUtils.BAN_TEMPLATE, targetId), "Ban"),
				Button.danger(String.format(InteractionUtils.KICK_TEMPLATE, targetId), "Kick")
		);
	}

	private void createReportThread(Message message, long targetId, ModerationConfig config) {
		message.createThreadChannel(message.getEmbeds().get(0).getTitle()).queue(
				thread -> thread.sendMessage(config.getStaffRole().getAsMention())
						.setActionRows(setComponents(targetId, thread.getIdLong()))
						.queue()
		);
	}

	private EmbedBuilder buildReportEmbed(User reported, User reportedBy, String reason, Channel channel) {
		return new EmbedBuilder()
				.setAuthor(reported.getAsTag(), null, reported.getEffectiveAvatarUrl())
				.setColor(Responses.Type.DEFAULT.getColor())
				.addField("Member", reported.getAsMention(), true)
				.addField("Reported by", reportedBy.getAsMention(), true)
				.addField("Channel", channel.getAsMention(), true)
				.addField("Reported on", String.format("<t:%s:F>", Instant.now().getEpochSecond()), false)
				.addField("ID", String.format("```%s```", reported.getId()), true)
				.addField("Reason", String.format("```%s```", reason), false)
				.setFooter(reportedBy.getAsTag(), reportedBy.getEffectiveAvatarUrl())
				.setTimestamp(Instant.now());
	}
}
