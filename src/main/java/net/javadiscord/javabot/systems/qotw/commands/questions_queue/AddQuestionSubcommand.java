package net.javadiscord.javabot.systems.qotw.commands.questions_queue;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageAction;
import net.dv8tion.jda.api.requests.restaction.interactions.InteractionCallbackAction;
import net.javadiscord.javabot.data.h2db.DbHelper;
import net.javadiscord.javabot.systems.qotw.commands.QOTWSubcommand;
import net.javadiscord.javabot.systems.qotw.dao.QuestionQueueRepository;
import net.javadiscord.javabot.systems.qotw.model.QOTWQuestion;
import net.javadiscord.javabot.util.Responses;

import java.sql.Connection;

/**
 * Subcommand that allows staff-members to add question to the QOTW-Queue.
 */
public class AddQuestionSubcommand extends QOTWSubcommand {
	@Override
	protected InteractionCallbackAction<?> handleCommand(SlashCommandInteractionEvent event, Connection con, long guildId) {
		return event.replyModal(this.buildQuestionModal());
	}

	private Modal buildQuestionModal() {
		TextInput priorityField = TextInput.create("priority", "Priority (Leave blank for default)", TextInputStyle.SHORT)
				.setRequired(false)
				.setValue("0")
				.build();

		TextInput questionField = TextInput.create("question", "Question Text", TextInputStyle.PARAGRAPH)
				.setMaxLength(1024)
				.build();

		return Modal.create("qotw-add-question", "Create QOTW Question")
				.addActionRows(ActionRow.of(questionField), ActionRow.of(priorityField))
				.build();
	}

	/**
	 * Handles the Modal, that pops up when executing <code>/qotw question-queue add</code>.
	 *
	 * @param event The {@link ModalInteractionEvent} that was fired upon submitting the modal.
	 * @return A {@link WebhookMessageAction}, that needs to be queued.
	 */
	public static WebhookMessageAction<Message> handleModalSubmit(ModalInteractionEvent event) {
		event.deferReply(true).queue();
		// Create question
		QOTWQuestion question = new QOTWQuestion();
		question.setGuildId(event.getGuild().getIdLong());
		question.setCreatedBy(event.getUser().getIdLong());
		question.setPriority(0);

		ModalMapping textOption = event.getValue("question");
		if (textOption == null || textOption.getAsString().isEmpty()) {
			return Responses.warning(event.getHook(), "Invalid question text. Must not be blank, and must be less than 1024 characters.");
		}
		question.setText(textOption.getAsString());

		ModalMapping priorityOption = event.getValue("priority");
		if (priorityOption == null || !priorityOption.getAsString().matches("\\d+")) {
			return Responses.error(event.getHook(), "Invalid priority value. Must be a numeric value.");
		}

		if (!priorityOption.getAsString().isEmpty()) {
			question.setPriority(Integer.parseInt(priorityOption.getAsString()));
		}

		DbHelper.doDaoAction(QuestionQueueRepository::new, dao -> dao.save(question));
		return Responses.success(event.getHook(), "Question Added", "Your question has been added to the queue.");
	}
}
