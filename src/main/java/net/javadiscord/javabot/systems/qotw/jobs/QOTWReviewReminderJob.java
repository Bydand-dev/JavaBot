package net.javadiscord.javabot.systems.qotw.jobs;

import lombok.RequiredArgsConstructor;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.javadiscord.javabot.data.config.BotConfig;
import net.javadiscord.javabot.data.config.GuildConfig;
import net.javadiscord.javabot.systems.notification.NotificationService;

import java.util.EnumSet;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Job which reminds reviewers to review QOTW if this has not been done already.
 */
@Service
@RequiredArgsConstructor
public class QOTWReviewReminderJob {
	private final JDA jda;
	private final NotificationService notificationService;
	private final BotConfig botConfig;

	/**
	 * Reminds reviewers to review QOTW if this has not been done already.
	 */
	@Scheduled(cron = "0 0 7 * * 1") // Monday, 07:00  UTC
	public void execute() {
		for (Guild guild : jda.getGuilds()) {
			GuildConfig guildConfig = botConfig.get(guild);
			if (guildConfig.getModerationConfig().getLogChannel() == null || guildConfig.getQotwConfig().getSubmissionChannel()==null) {
				continue;
			}
			if (!guildConfig.getQotwConfig().getSubmissionChannel().getThreadChannels().isEmpty()) {
				notificationService
					.withGuild(guild)
					.sendToModerationLog(channel ->
						channel
							.sendMessageFormat("%s\n**Reminder**\nThe QOTW has not been reviewed yet.",
									guildConfig.getQotwConfig().getQOTWReviewRole().getAsMention())
							.setAllowedMentions(EnumSet.of(Message.MentionType.ROLE))
					);
			}
		}
	}
}
