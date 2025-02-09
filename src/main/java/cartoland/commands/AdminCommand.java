package cartoland.commands;

import cartoland.utilities.*;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.attribute.ISlowmodeChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * {@code AdminCommand} is an execution when a moderator uses /admin command. This class extends
 * {@link HasSubcommands} class which implements {@link ICommand} interface, which is for the commands HashMap in
 * {@link cartoland.events.CommandUsage}. This class doesn't handle sub commands, but call other classes to
 * deal with it.
 *
 * @since 2.1
 * @author Alex Cai
 */
public class AdminCommand extends HasSubcommands
{
	private static final String TEMP_BAN_SET = "serialize/temp_ban_set.ser";

	//userID為value[0] ban time為value[1] ban guild為value[2]
	public static final Set<long[]> tempBanSet = (FileHandle.deserialize(TEMP_BAN_SET) instanceof HashSet<?> set) ? set.stream()
			.map(o -> (long[])o).collect(Collectors.toSet()) : new HashSet<>();
	public static final byte USER_ID_INDEX = 0;
	public static final byte BANNED_TIME = 1;
	public static final byte BANNED_SERVER = 2;

	static
	{
		FileHandle.registerSerialize(TEMP_BAN_SET, tempBanSet); //註冊串聯化
	}

	public AdminCommand()
	{
		super(3);
		subcommands.put("mute", new MuteSubcommand());
		subcommands.put("temp_ban", new TempBanSubcommand());
		subcommands.put("slow_mode", new SlowModeSubcommand());
	}

	/**
	 * {@code MuteSubCommand} is a class that handles one of the sub commands of {@code /admin} command, which is
	 * {@code /admin mute}.
	 *
	 * @since 2.1
	 * @author Alex Cai
	 */
	private static class MuteSubcommand implements ICommand
	{
		private static final long MAX_TIME_OUT_LENGTH_MILLIS = 1000L * 60 * 60 * 24 * Member.MAX_TIME_OUT_LENGTH;

		@Override
		public void commandProcess(SlashCommandInteractionEvent event)
		{
			Member member = event.getMember(); //使用指令的成員
			if (member == null)
			{
				event.reply("Impossible, this is required!").queue();
				return;
			}

			long userID = member.getIdLong(); //使用指令的成員ID

			if (!member.hasPermission(Permission.MODERATE_MEMBERS))
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.mute.no_permission")).setEphemeral(true).queue();
				return;
			}

			Member target = event.getOption("target", CommonFunctions.getAsMember);
			if (target == null) //找不到要被禁言的成員
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.mute.no_member")).setEphemeral(true).queue();
				return;
			}

			if (target.isOwner()) //無法禁言群主 會擲出HierarchyException
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.mute.can_t_owner")).setEphemeral(true).queue();
				return;
			}
			if (target.isTimedOut()) //已經被禁言了
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.mute.already_timed_out")).setEphemeral(true).queue();
				return;
			}

			Double durationBox = event.getOption("duration", CommonFunctions.getAsDouble);
			if (durationBox == null)
			{
				event.reply("Impossible, this is required!").queue();
				return;
			}
			double duration = durationBox;
			if (duration <= 0) //不能負時間
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.mute.duration_must_be_positive")).setEphemeral(true).queue();
				return;
			}

			String unit = event.getOption("unit", CommonFunctions.getAsString);
			if (unit == null) //單位
			{
				event.reply("Impossible, this is required!").queue();
				return;
			}

			//不用java.util.concurrent.TimeUnit 因為它不接受浮點數
			long durationMillis = Math.round(duration * switch (unit) //將單位轉成毫秒 1000毫秒等於1秒
			{
				case "second" -> 1000;
				case "minute" -> 1000 * 60;
				case "hour" -> 1000 * 60 * 60;
				case "double_hour" -> 1000 * 60 * 60 * 2;
				case "day" -> 1000 * 60 * 60 * 24;
				case "week" -> 1000 * 60 * 60 * 24 * 7;
				default -> 1;
			}); //Math.round會處理溢位

			if (durationMillis > MAX_TIME_OUT_LENGTH_MILLIS) //不能禁言超過28天
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.mute.too_long").formatted(Member.MAX_TIME_OUT_LENGTH)).setEphemeral(true).queue();
				return;
			}

			String mutedTime = Algorithm.buildCleanFloatingString(Double.toString(duration)) + ' ' + JsonHandle.getStringFromJsonKey(userID, "admin.unit_" + unit);
			String replyString = JsonHandle.getStringFromJsonKey(userID, "admin.mute.success")
					.formatted(target.getAsMention(), mutedTime, (System.currentTimeMillis() + durationMillis) / 1000);
			String reason = event.getOption("reason", CommonFunctions.getAsString);
			if (reason != null) //有理由
				replyString += JsonHandle.getStringFromJsonKey(userID, "admin.mute.reason").formatted(reason);

			event.reply(replyString).queue();
			target.timeoutFor(Duration.ofMillis(durationMillis)).reason(reason).queue();
		}
	}

	/**
	 * {@code TempBanSubCommand} is a class that handles one of the sub commands of {@code /admin} command, which is
	 * {@code /admin temp_ban}.
	 *
	 * @since 2.1
	 * @author Alex Cai
	 */
	private static class TempBanSubcommand implements ICommand
	{
		@Override
		public void commandProcess(SlashCommandInteractionEvent event)
		{
			Member member = event.getMember(); //使用指令的成員
			if (member == null)
			{
				event.reply("Impossible, this is required!").queue();
				return;
			}

			long userID = member.getIdLong(); //使用指令的成員ID

			if (!member.hasPermission(Permission.BAN_MEMBERS))
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.temp_ban.no_permission")).setEphemeral(true).queue();
				return;
			}

			Member target = event.getOption("target", CommonFunctions.getAsMember);
			if (target == null)
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.temp_ban.no_member")).setEphemeral(true).queue();
				return;
			}
			if (target.isOwner()) //無法禁言群主 會擲出HierarchyException
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.temp_ban.can_t_owner")).setEphemeral(true).queue();
				return;
			}

			Double durationBox = event.getOption("duration", CommonFunctions.getAsDouble);
			if (durationBox == null)
			{
				event.reply("Impossible, this is required!").queue();
				return;
			}
			double duration = durationBox;
			if (duration <= 0) //不能負時間
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.temp_ban.duration_too_short")).setEphemeral(true).queue();
				return;
			}

			String unit = event.getOption("unit", CommonFunctions.getAsString);
			if (unit == null)
			{
				event.reply("Impossible, this is required!").queue();
				return;
			}

			long durationHours = Math.round(duration * switch (unit) //將單位轉成小時
			{
				case "double_hour" -> 2;
				case "day" -> 24;
				case "week" -> 24 * 7;
				case "month" -> 24 * 30;
				case "season" -> 24 * 30 * 3;
				case "year" -> 24 * 365;
				case "wood_rat" -> 24 * 365 * 60;
				case "century" -> 24 * 365 * 100;
				default -> 1; //其實unit一定等於上述那些或second 但是default必須的
			}); //Math.round會處理溢位

			if (durationHours < 1L) //時間不能小於一小時
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.temp_ban.duration_too_short")).setEphemeral(true).queue();
				return;
			}

			String bannedTime = Algorithm.buildCleanFloatingString(Double.toString(duration)) + ' ' + JsonHandle.getStringFromJsonKey(userID, "admin.unit_" + unit);
			String replyString = JsonHandle.getStringFromJsonKey(userID, "admin.temp_ban.success")
					.formatted(
							target.getAsMention(), bannedTime,
							System.currentTimeMillis() / 1000 + durationHours * 60 * 60); //直到<t:> 以秒為單位
			//TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + TimeUnit.HOURS.toSeconds(durationHours)

			String reason = event.getOption("reason", CommonFunctions.getAsString);
			if (reason != null)
				replyString += JsonHandle.getStringFromJsonKey(userID, "admin.temp_ban.reason").formatted(reason);

			event.reply(replyString).queue(); //回覆

			//回覆完再開始動作 避免超過三秒限制
			long pardonTime = TimerHandle.getHoursFrom1970() + durationHours; //計算解除時間
			//TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis())
			if (pardonTime <= 0) //溢位
				pardonTime = Long.MAX_VALUE;

			Guild guild = target.getGuild();
			long[] banData = new long[3];
			banData[USER_ID_INDEX] = target.getIdLong(); //紀錄被ban的人的ID
			banData[BANNED_TIME] = pardonTime; //紀錄被ban的人的時間
			banData[BANNED_SERVER] = guild.getIdLong(); //紀錄被ban的人的群組
			tempBanSet.add(banData); //紀錄ban了這個人
			guild.ban(target, 0, TimeUnit.SECONDS).reason(reason + '\n' + bannedTime).queue();
		}
	}

	/**
	 * {@code SlowModeSubCommand} is a class that handles one of the sub commands of {@code /admin} command, which is
	 * {@code /admin slow_mode}.
	 *
	 * @since 2.1
	 * @author Alex Cai
	 */
	private static class SlowModeSubcommand implements ICommand
	{
		@Override
		public void commandProcess(SlashCommandInteractionEvent event)
		{
			Member member = event.getMember(); //使用指令的成員
			if (member == null)
			{
				event.reply("Impossible, this is required!").queue();
				return;
			}

			long userID = member.getIdLong(); //使用指令的成員ID

			if (!member.hasPermission(Permission.MANAGE_CHANNEL))
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.slow_mode.no_permission")).setEphemeral(true).queue();
				return;
			}

			if (!(event.getOption("channel", CommonFunctions.getAsChannel) instanceof ISlowmodeChannel channel)) //不是可以設慢速模式的頻道
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.slow_mode.wrong_channel")).setEphemeral(true).queue();
				return;
			}

			Double timeBox = event.getOption("time", CommonFunctions.getAsDouble); //可惜沒有getAsFloat
			if (timeBox == null)
			{
				event.reply("Impossible, this is required!").queue();
				return;
			}
			float time = (float) timeBox.doubleValue(); //解包並轉float
			if (time < 0) //不能負時間 可以0 0代表取消慢速
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.slow_mode.time_must_be_no_negative")).setEphemeral(true).queue();
				return;
			}

			String unit = event.getOption("unit", CommonFunctions.getAsString); //單位字串
			if (unit == null)
			{
				event.reply("Impossible, this is required!").queue();
				return;
			}

			int timeSecond = Math.round(time * switch (unit) //將單位轉成秒
			{
				case "second" -> 1;
				case "minute" -> 60;
				case "hour" -> 60 * 60;
				case "double_hour" -> 60 * 60 * 2;
				default -> 0;
			});
			if (timeSecond > ISlowmodeChannel.MAX_SLOWMODE) //不能超過6小時 21600秒
			{
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.slow_mode.too_long").formatted(ISlowmodeChannel.MAX_SLOWMODE / (60 * 60)))
						.setEphemeral(true).queue();
				return;
			}

			String slowTime = Algorithm.buildCleanFloatingString(Float.toString(time)) + ' ' + JsonHandle.getStringFromJsonKey(userID, "admin.unit_" + unit);
			if (timeSecond > 0)
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.slow_mode.success").formatted(channel.getAsMention(), slowTime)).queue();
			else //一定是等於0 前面過濾掉小於0的情況了
				event.reply(JsonHandle.getStringFromJsonKey(userID, "admin.slow_mode.cancel").formatted(channel.getAsMention())).queue();
			channel.getManager().setSlowmode(timeSecond).queue(); //設定慢速時間
		}
	}
}