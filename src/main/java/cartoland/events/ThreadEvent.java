package cartoland.events;

import cartoland.utilities.ForumsHandle;
import cartoland.utilities.IDs;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * {@code ThreadEvent} is a listener that triggers when a user create a thread or a thread archived. For now, this only
 * affect Question forum post. This class was registered in {@link cartoland.Cartoland#main}, with the build of
 * JDA.
 *
 * @since 1.5
 * @author Alex Cai
 */
public class ThreadEvent extends ListenerAdapter
{
	@Override
	public void onChannelCreate(ChannelCreateEvent event)
	{
		if (!event.getChannelType().isThread()) //如果不是討論串
			return; //bye have a great time

		ThreadChannel threadChannel = event.getChannel().asThreadChannel();
		threadChannel.join().queue(); //加入討論串

		//關於地圖專版和問題論壇
		if (threadChannel.getParentChannel().asStandardGuildChannel().getParentCategoryIdLong() == IDs.FORUM_CATEGORY_ID)
			ForumsHandle.createForumPost(threadChannel);
	}

	@Override
	public void onChannelUpdateArchived(ChannelUpdateArchivedEvent event)
	{
		if (!event.getChannelType().isThread()) //不是討論串或論壇貼文
			return; //不用執行

		if (Boolean.TRUE.equals(event.getNewValue())) //變成關閉
			return; //不用執行

		ThreadChannel forumPost = event.getChannel().asThreadChannel();
		if (forumPost.getParentChannel().getIdLong() != IDs.QUESTIONS_CHANNEL_ID) //不在問題論壇
			return; //不用執行

		Guild cartoland = event.getGuild();
		if (cartoland.getIdLong() != IDs.CARTOLAND_SERVER_ID) //不在創聯
			return; //不用執行

		ForumChannel questionsChannel = forumPost.getParentChannel().asForumChannel(); //問題論壇
		ForumTag resolvedForumTag = questionsChannel.getAvailableTagById(IDs.RESOLVED_FORUM_TAG_ID); //已解決
		ForumTag unresolvedForumTag = questionsChannel.getAvailableTagById(IDs.UNRESOLVED_FORUM_TAG_ID); //未解決
		Set<ForumTag> tags = new HashSet<>(forumPost.getAppliedTags()); //本貼文目前擁有的tag getAppliedTags()回傳的是不可變動的list
		tags.remove(resolvedForumTag); //移除resolved
		tags.add(unresolvedForumTag); //新增unresolved 因為是set所以不用擔心重複
		forumPost.getManager()
				.setAppliedTags(tags.size() <= ForumsHandle.MAX_TAG ? tags : new ArrayList<>(tags).subList(0, ForumsHandle.MAX_TAG)) //最多只能5個tag
				.queue(); //貼文狀態為未解決
	}
}