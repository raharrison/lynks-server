package worker

import entry.LinkService
import kotlinx.coroutines.time.delay
import notify.Notification
import notify.NotifyService
import resource.ResourceRetriever
import util.JsonMapper.defaultMapper
import util.loggerFor
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant

private val logger = loggerFor<DiscussionFinderWorker>()

data class DiscussionFinderWorkerRequest(val linkId: String, val intervalIndex: Int = -1): PersistVariableWorkerRequest() {
    override val key = linkId
}

class DiscussionFinderWorker(private val linkService: LinkService,
                             private val resourceRetriever: ResourceRetriever,
                             notifyService: NotifyService) : PersistedVariableChannelBasedWorker<DiscussionFinderWorkerRequest>(notifyService) {

    private data class Discussion(val title: String, val url: String, val score: Int, val comments: Int, val created: Long)

    override suspend fun doWork(input: DiscussionFinderWorkerRequest) {
        logger.info("Launching discussion finder for entry ${input.linkId}")
        findDiscussions(input.linkId, input.intervalIndex)
    }

    private val intervals = listOf<Long>(60, 60 * 4, 60 * 10, 60 * 24)

    override val requestClass = DiscussionFinderWorkerRequest::class.java

    private suspend fun findDiscussions(linkId: String, initialIntervalIndex: Int) {
        var intervalIndex = initialIntervalIndex
        while (true) {
            val link = linkService.get(linkId) ?: break
            logger.info("Finding discussions for entry ${link.id}")
            val discussions = mutableListOf<Discussion>().apply {
                addAll(hackerNewsDiscussions(link.url))
                addAll(redditDiscussions(link.url))
            }
            logger.info("Found ${discussions.size} discussions for entry ${link.id}")

            val current = if (link.props.containsAttribute("discussions"))
                link.props.getAttribute("discussions") as List<*>
            else emptyList<Any>()

            if(discussions.isNotEmpty()) {
                link.props.addAttribute("discussions", discussions)
                linkService.mergeProps(link.id, link.props)
                sendNotification(Notification.processed("Discussions found"), link)
            }

            intervalIndex++
            if (intervalIndex >= intervals.size) {
                // would break but more discussions found
                if (current.size != discussions.size && discussions.isNotEmpty()) {
                    logger.info("Discussion finder for entry ${link.id} remaining active")
                    intervalIndex--
                } else {
                    logger.info("Discussion finder for entry ${link.id} ending")
                    break
                }
            }

            // update schedule
            updateSchedule(DiscussionFinderWorkerRequest(linkId, intervalIndex))
            val interval = intervals[intervalIndex]
            logger.info("Discussion finder for entry ${link.id} sleeping for $interval minutes")

            delay(Duration.ofMinutes(interval))
        }
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    private suspend fun hackerNewsDiscussions(url: String): List<Discussion> {
        val base = "http://hn.algolia.com/api/v1/search?query=%s&restrictSearchableAttributes=url"
        val response = resourceRetriever.getString(base.format(encode(url)))
        val discussions = mutableListOf<Discussion>()
        if(response == null || response.isBlank()) return discussions
        val node = defaultMapper.readTree(response)

        if (node.has("hits")) {
            for (hit in node.get("hits")) {
                val title = hit.get("title").textValue()
                val link = "https://news.ycombinator.com/item?id=${hit.get("objectID").textValue()}"
                val score = hit.get("points").intValue()
                val comments = hit.get("num_comments").intValue()
                val createdStamp = hit.get("created_at").textValue()
                val instant = Instant.parse(createdStamp)
                discussions.add(Discussion(title, link, score, comments, instant.epochSecond))
            }
        }
        return discussions.sortedWith(compareByDescending(Discussion::created)
                .thenComparing(compareByDescending(Discussion::comments)))
    }

    private suspend fun redditDiscussions(url: String): List<Discussion> {
        val base = "https://www.reddit.com/api/info.json?url=%s"
        val response = resourceRetriever.getString(base.format(encode(url)))
        val discussions = mutableListOf<Discussion>()
        if(response == null || response.isBlank()) return discussions
        val node = defaultMapper.readTree(response)

        if (node.has("data")) {
            val data = node.get("data")
            if (data.has("children")) {
                val children = data.get("children")
                for (child in children) {
                    if (child.get("kind").textValue() == "t3") {
                        val site = child.get("data")
                        val sub = site.get("subreddit_name_prefixed").textValue()
                        val link = site.get("permalink").textValue()
                        val score = site.get("score").intValue()
                        val comments = site.get("num_comments").intValue()
                        val created = site.get("created_utc").longValue()
                        discussions.add(Discussion(sub, link, score, comments, created))
                    }
                }
            }
        }
        return discussions.sortedWith(compareByDescending(Discussion::created)
                .thenComparing(compareByDescending(Discussion::comments)))
    }


}