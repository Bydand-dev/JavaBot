package net.javadiscord.javabot.systems.qotw.submissions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.javadiscord.javabot.data.config.guild.QOTWConfig;
import net.javadiscord.javabot.systems.notification.NotificationService;
import net.javadiscord.javabot.systems.qotw.QOTWPointsService;
import net.javadiscord.javabot.systems.qotw.dao.QuestionQueueRepository;
import net.javadiscord.javabot.systems.qotw.model.QOTWQuestion;
import net.javadiscord.javabot.systems.qotw.model.QOTWSubmission;
import net.javadiscord.javabot.util.ExceptionLogger;
import net.javadiscord.javabot.util.Responses;
import net.javadiscord.javabot.util.WebhookUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Handles & manages QOTW Submissions by using Discords {@link ThreadChannel}s.
 */
@Slf4j
@RequiredArgsConstructor
public class SubmissionManager {
	/**
	 * The submission thread's name.
	 */
	public static final String THREAD_NAME = "%s — %s";
	private static final String SUBMISSION_ACCEPTED = "\u2705";
	private static final String SUBMISSION_DECLINED = "\u274C";

	private final QOTWConfig config;
	private final QOTWPointsService pointsService;
	private final QuestionQueueRepository questionQueueRepository;
	private final NotificationService notificationService;
	private final ExecutorService asyncPool;

	/**
	 * Handles the "Submit your Answer" Button interaction.
	 *
	 * @param event          The {@link ButtonInteractionEvent} that is fired upon use.
	 * @param questionNumber The current qotw-week number.
	 * @return A {@link WebhookMessageCreateAction}.
	 */
	@Transactional
	public WebhookMessageCreateAction<?> handleSubmission(@NotNull ButtonInteractionEvent event, int questionNumber) {
		event.deferEdit().queue();
		Member member = event.getMember();
		if (!canCreateSubmissions(member)) {
			return Responses.warning(event.getHook(), "You're not eligible to create a new submission thread.");
		}
		config.getSubmissionChannel().createThreadChannel(
				String.format(THREAD_NAME, questionNumber, member.getId()), true).queue(
				thread -> {
					thread.addThreadMember(member).queue();
					thread.getManager().setInvitable(false).setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_WEEK).queue();
					try {
						asyncPool.execute(() -> {
							Optional<QOTWQuestion> questionOptional = questionQueueRepository.findByQuestionNumber(questionNumber);
							if (questionOptional.isPresent()) {
								thread.sendMessage(member.getAsMention())
										.setEmbeds(buildSubmissionThreadEmbed(event.getUser(), questionOptional.get(), config))
										.setComponents(ActionRow.of(Button.danger("qotw-submission:delete", "Delete Submission")))
										.queue(s -> {
										}, err -> ExceptionLogger.capture(err, getClass().getSimpleName()));
								QOTWSubmission submission = new QOTWSubmission(thread);
								submission.setAuthor(member.getUser());
							} else {
								thread.sendMessage("Could not retrieve current QOTW Question. Please contact an Administrator if you think that this is a mistake.")
										.queue();
							}
						});
					} catch (DataAccessException e) {
						ExceptionLogger.capture(e, getClass().getSimpleName());
					}
				}, e -> log.error("Could not create submission thread for member {}. ", member.getUser().getAsTag(), e)
		);
		log.info("Opened new Submission Thread for User {}", member.getUser().getAsTag());
		return Responses.success(event.getHook(), "Submission Thread created", "Successfully created a new private Thread for your submission.");
	}

	public List<QOTWSubmission> getActiveSubmissions() {
		return config.getSubmissionChannel().getThreadChannels()
				.stream()
				.map(QOTWSubmission::new).toList();
	}

	/**
	 * Handles the "Delete Submission" Button.
	 *
	 * @param event The {@link ButtonInteractionEvent} that is fired upon use.
	 */
	public void handleThreadDeletion(@NotNull ButtonInteractionEvent event) {
		if (event.getChannelType() != ChannelType.GUILD_PRIVATE_THREAD) return;
		ThreadChannel thread = event.getChannel().asThreadChannel();
		new QOTWSubmission(thread).retrieveAuthor(author -> {
			if (event.getUser().getIdLong() == author.getIdLong()) {
				thread.delete().queue();
			}
		});
	}

	private boolean canCreateSubmissions(Member member) {
		if (member == null) return false;
		if (member.getUser().isBot() || member.getUser().isSystem()) return false;
		if (member.isTimedOut() || member.isPending()) return false;
		return config.getSubmissionChannel().getThreadChannels()
				.stream().noneMatch(p -> p.getName().contains(member.getId()));
	}

	/**
	 * Accepts a submission.
	 *
	 * @param hook       The {@link net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent} that was fired.
	 * @param thread     The submission's {@link ThreadChannel}.
	 * @param author     The submissions' author.
	 * @param bestAnswer Whether the submission is among the best answers for this week.
	 */
	public void acceptSubmission(InteractionHook hook, @NotNull ThreadChannel thread, @NotNull User author, boolean bestAnswer) {
		thread.getManager().setName(SUBMISSION_ACCEPTED + thread.getName().substring(1)).queue();
		pointsService.increment(author.getIdLong());
		notificationService.withQOTW(thread.getGuild(), author).sendAccountIncrementedNotification();
		Responses.success(hook, "Submission Accepted",
				"Successfully accepted submission by " + author.getAsMention()).queue();
		notificationService.withQOTW(thread.getGuild()).sendSubmissionActionNotification(author, new QOTWSubmission(thread), bestAnswer ? SubmissionStatus.ACCEPT_BEST : SubmissionStatus.ACCEPT);
		Optional<ThreadChannel> newestPostOptional = config.getSubmissionsForumChannel().getThreadChannels()
				.stream().max(Comparator.comparing(ThreadChannel::getTimeCreated));
		if (newestPostOptional.isPresent()) {
			ThreadChannel newestPost = newestPostOptional.get();
			getMessagesByUser(thread, author).thenAccept(messages -> {
				for (Message message : messages) {
					if (message.getAuthor().isBot() || message.getType() != MessageType.DEFAULT) continue;
					WebhookUtil.ensureWebhookExists(newestPost.getParentChannel().asForumChannel(), wh -> {
						if (message.getContentRaw().length() > 2000) {
							WebhookUtil.mirrorMessageToWebhook(wh, message, message.getContentRaw().substring(0, 2000), newestPost.getIdLong());
							WebhookUtil.mirrorMessageToWebhook(wh, message, message.getContentRaw().substring(2000), newestPost.getIdLong());
						} else {
							WebhookUtil.mirrorMessageToWebhook(wh, message, message.getContentRaw(), newestPost.getIdLong());
						}
					});
				}
				newestPost.sendMessageEmbeds(buildAuthorEmbed(author, bestAnswer)).queue();
			});
		}
		thread.getManager().setLocked(true).setArchived(true).queue();
	}

	/**
	 * Declines a submission.
	 *
	 * @param hook   The {@link net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent} that was fired.
	 * @param thread The submission's {@link ThreadChannel}.
	 * @param author The submissions' author.
	 */
	public void declineSubmission(InteractionHook hook, @NotNull ThreadChannel thread, User author) {
		thread.getManager().setName(SUBMISSION_DECLINED + thread.getName().substring(1)).queue();
		notificationService.withQOTW(thread.getGuild(), author).sendSubmissionDeclinedEmbed();
		Responses.success(hook, "Submission Declined", "Successfully declined submission by " + author.getAsMention()).queue();
		notificationService.withQOTW(thread.getGuild()).sendSubmissionActionNotification(author, new QOTWSubmission(thread), SubmissionStatus.DECLINE);
		thread.getManager().setLocked(true).setArchived(true).queue();
	}

	private CompletableFuture<List<Message>> getMessagesByUser(@NotNull ThreadChannel channel, User user) {
		return channel.getIterableHistory()
				.reverse()
				.takeAsync(channel.getMessageCount())
				.thenApply(list -> list.stream().filter(m -> m.getAuthor().equals(user)).toList());
	}

	private @NotNull MessageEmbed buildAuthorEmbed(@NotNull User user, boolean bestAnswer) {
		return new EmbedBuilder()
				.setAuthor((bestAnswer ? "\u2B50 " : "") + "Submission from " + user.getAsTag(), null, user.getAvatarUrl())
				.setColor(bestAnswer ? Responses.Type.WARN.getColor() : Responses.Type.DEFAULT.getColor())
				.build();
	}

	private @NotNull MessageEmbed buildSubmissionThreadEmbed(@NotNull User createdBy, @NotNull QOTWQuestion question, @NotNull QOTWConfig config) {
		return new EmbedBuilder()
				.setColor(Responses.Type.DEFAULT.getColor())
				.setAuthor(createdBy.getAsTag(), null, createdBy.getEffectiveAvatarUrl())
				.setTitle(String.format("Question of the Week #%s", question.getQuestionNumber()))
				.setDescription(String.format("""
								%s

								Hey, %s! Please submit your answer into this private thread.
								The %s will review your submission once a new question appears.""",
						question.getText(), createdBy.getAsMention(), config.getQOTWReviewRole().getAsMention()))
				.addField("Note",
						"""
								To maximize your chances of getting this week's QOTW Point make sure to:
								— Provide a **Code example** (if possible)
								— Try to answer the question as detailed as possible.

								Staff usually won't reply in here.""", false)
				.setTimestamp(Instant.now())
				.build();
	}
}
