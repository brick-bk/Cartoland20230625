package cartoland.events;

import cartoland.utilities.CommonFunctions;
import cartoland.utilities.FileHandle;
import cartoland.utilities.IDs;
import cartoland.utilities.JsonHandle;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code ContextMenu} is a listener that triggers when a user uses right click command. This class was registered
 * in {@link cartoland.Cartoland#main}, with the build of JDA.
 *
 * @since 1.5
 * @author Alex Cai
 */
public class ContextMenu extends ListenerAdapter
{
	public static final String RAW_TEXT = "Raw Text";
	public static final String REACTIONS = "Reactions";
	public static final String CODE_BLOCK = "Code Block";
	public static final String QUOTE = "Quote";
	public static final String PIN = "Pin";
	private final EmbedBuilder embedBuilder = new EmbedBuilder();

	@Override
	public void onMessageContextInteraction(MessageContextInteractionEvent event)
	{
		User user = event.getUser();
		String eventName = event.getName();

		switch (eventName)
		{
			case RAW_TEXT ->
					event.replyFiles(FileUpload.fromData(event.getTarget().getContentRaw().getBytes(StandardCharsets.UTF_8), "message.txt"))
							.setEphemeral(true)
							.queue();

			case REACTIONS ->
			{
				String reactions = event.getTarget()
						.getReactions()
						.stream()
						.map(reaction -> reaction.getEmoji().getFormatted() + " × " + reaction.getCount())
						.collect(Collectors.joining("\t"));

				event.reply(reactions.isEmpty() ? "There's no any reactions" : reactions)
						.setEphemeral(true)
						.queue();
			}

			case CODE_BLOCK ->
			{
				String rawContent = event.getTarget().getContentRaw();
				int contentLength = rawContent.length();
				if (contentLength <= 1992) //因為前後要加```\n和\n``` 因此以1992為界線
				{
					event.reply("```\n" + rawContent + "\n```").queue();
					return;
				}

				//先回覆前1992個字 以及格式
				event.reply("```\n" + rawContent.substring(0, 1992) + "\n```").queue(interactionHook ->
					interactionHook.retrieveOriginal().queue(message ->
					{
						if (contentLength <= 1992 + 1992) //如果從[1992] = 第1993個字開始算起 長度不超過1992個字
							message.reply("```\n" + rawContent.substring(1992) + "\n```").mentionRepliedUser(false).queue();
						else
							message.reply("```\n" + rawContent.substring(1992, 1992 + 1992) + "\n```").mentionRepliedUser(false)
								.queue(message1 -> message1.reply("```\n" + rawContent.substring(1992 + 1992) + "\n```").mentionRepliedUser(false).queue());
					}));
			}

			case QUOTE ->
			{
				Message message = event.getTarget();
				User linkAuthor = message.getAuthor(); //連結訊息的發送者
				String linkAuthorName = linkAuthor.getEffectiveName();
				String linkAuthorAvatar = linkAuthor.getEffectiveAvatarUrl();
				MessageChannel channel = event.getChannel();

				embedBuilder.setAuthor(linkAuthorName, linkAuthorAvatar, linkAuthorAvatar)
						.setDescription(message.getContentRaw()) //訊息的內容
						.setTimestamp(message.getTimeCreated()) //連結訊息的發送時間
						.setFooter(channel != null ? channel.getName() : linkAuthorName, null); //訊息的發送頻道

				//選擇連結訊息內的第一張圖片作為embed的圖片
				//不用add field 沒必要那麼麻煩
				List<Message.Attachment> attachments = message.getAttachments();
				if (attachments.size() != 0)
					attachments.stream()
							.filter(CommonFunctions.isImage)
							.findFirst()
							.ifPresent(imageAttachment -> embedBuilder.setImage(imageAttachment.getUrl()));
				else
					embedBuilder.setImage(null);
				event.replyEmbeds(embedBuilder.build())
					.addActionRow(Button.link(message.getJumpUrl(), JsonHandle.getStringFromJsonKey(event.getUser().getIdLong(), "quote.jump_message")))
					.queue();
			}

			case PIN ->
			{
				Member member = event.getMember();
				if (member == null)
				{
					event.reply("You should use this function in a server!").queue();
					return;
				}

				Message target = event.getTarget();
				Channel channel = event.getChannel();
				boolean isDiscussPostOwner = //如果是地圖專版的論壇貼文的開啟者 可以無視權限直接釘選
					channel != null && channel.getType().isThread() && //是討論串
					((ThreadChannel) channel).getParentChannel().getIdLong() == IDs.MAP_DISCUSS_CHANNEL_ID && //是地圖專版
					((ThreadChannel) channel).getOwnerIdLong() == member.getIdLong(); //是開啟者

				if (!isDiscussPostOwner && !member.hasPermission(Permission.MESSAGE_MANAGE)) //如果不是地圖專版貼文不是開啟者且沒有權限
				{
					event.reply("You don't have the permission to pin this message!").setEphemeral(true).queue();
					return;
				}

				if (target.isPinned())
				{
					event.reply("Unpinned message.").queue();
					target.unpin().queue(); //解釘
				}
				else
				{
					event.reply("Pinned message.").queue();
					target.pin().queue(); //釘選
				}
			}
		}

		FileHandle.log(user.getEffectiveName() + "(" + user.getIdLong() + ") used " + eventName);
	}
}