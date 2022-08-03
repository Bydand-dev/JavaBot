package net.javadiscord.javabot.api.routes.user_profile;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.javadiscord.javabot.Bot;
import net.javadiscord.javabot.api.response.ApiResponseBuilder;
import net.javadiscord.javabot.api.response.ApiResponses;
import net.javadiscord.javabot.api.routes.JDAEntity;
import net.javadiscord.javabot.api.routes.user_profile.model.HelpAccountData;
import net.javadiscord.javabot.api.routes.user_profile.model.UserProfileData;
import net.javadiscord.javabot.systems.help.HelpExperienceService;
import net.javadiscord.javabot.systems.help.model.HelpAccount;
import net.javadiscord.javabot.systems.moderation.warn.dao.WarnRepository;
import net.javadiscord.javabot.systems.qotw.QOTWPointsService;
import net.javadiscord.javabot.systems.qotw.model.QOTWAccount;
import net.javadiscord.javabot.systems.user_preferences.UserPreferenceService;
import net.javadiscord.javabot.systems.user_preferences.model.Preference;
import net.javadiscord.javabot.systems.user_preferences.model.UserPreference;
import net.javadiscord.javabot.util.ExceptionLogger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestController
public class UserProfileController implements JDAEntity {

	@GetMapping(
			value = "{guild_id}/{user_id}",
			produces = MediaType.APPLICATION_JSON_VALUE
	)
	public ResponseEntity<String> getUserProfile(
			@PathVariable(value = "guild_id") String guildId,
			@PathVariable(value = "user_id") String userId
	) {
		Guild guild = getJDA().getGuildById(guildId);
		if (guild == null) {
			return new ResponseEntity<>(ApiResponses.INVALID_GUILD_IN_REQUEST, HttpStatus.BAD_REQUEST);
		}
		User user = getJDA().retrieveUserById(userId).complete();
		if (user == null) {
			return new ResponseEntity<>(ApiResponses.INVALID_USER_IN_REQUEST, HttpStatus.BAD_REQUEST);
		}
		try (Connection con = Bot.getDataSource().getConnection()) {
			UserProfileData data = new UserProfileData();
			data.setUserId(user.getIdLong());
			data.setUserName(user.getName());
			data.setDiscriminator(user.getDiscriminator());
			data.setEffectiveAvatarUrl(user.getEffectiveAvatarUrl());
			// Question of the Week Account
			QOTWPointsService qotwService = new QOTWPointsService(Bot.getDataSource());
			QOTWAccount qotwAccount = qotwService.getOrCreateAccount(user.getIdLong());
			data.setQotwAccount(qotwAccount);
			// Help Account
			HelpExperienceService helpService = new HelpExperienceService(Bot.getDataSource());
			HelpAccount helpAccount = helpService.getOrCreateAccount(user.getIdLong());
			data.setHelpAccount(HelpAccountData.of(helpAccount, guild));
			// User Preferences
			UserPreferenceService preferenceService = new UserPreferenceService(Bot.getDataSource());
			List<UserPreference> preferences = Arrays.stream(Preference.values()).map(p -> preferenceService.getOrCreate(user.getIdLong(), p)).toList();
			data.setPreferences(preferences);
			// User Warns
			WarnRepository warnRepository = new WarnRepository(con);
			LocalDateTime cutoff = LocalDateTime.now().minusDays(Bot.getConfig().get(guild).getModerationConfig().getWarnTimeoutDays());
			data.setWarns(warnRepository.getWarnsByUserId(user.getIdLong(), cutoff));

			return new ResponseEntity<>(new ApiResponseBuilder().add("profile", data).build(), HttpStatus.OK);
		} catch (SQLException e) {
			ExceptionLogger.capture(e, getClass().getSimpleName());
			return new ResponseEntity<>(ApiResponses.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
}
