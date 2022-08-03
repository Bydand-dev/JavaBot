package net.javadiscord.javabot.api.routes.qotw_leaderboard.model;

import lombok.Data;
import net.javadiscord.javabot.systems.qotw.model.QOTWAccount;

@Data
public class QOTWMemberData {
	private long userId;
	private String userName;
	private String discriminator;
	private String effectiveAvatarUrl;
	private QOTWAccount account;
}
