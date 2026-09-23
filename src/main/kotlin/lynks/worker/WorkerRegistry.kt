package lynks.worker

import kotlinx.coroutines.channels.SendChannel
import lynks.common.CommentId
import lynks.common.EntryId
import lynks.common.UserId
import lynks.common.inject.ServiceProvider
import lynks.task.Task
import lynks.task.TaskContext

class WorkerRegistry {

    fun init(serviceProvider: ServiceProvider) {
        with(serviceProvider) {
            linkWorker = LinkProcessorWorker(get(), get(), get(), get(), get()).worker()
            discussionWorker = DiscussionFinderWorker(get(), get(), get(), get()).worker()
            taskWorker = TaskRunnerWorker().worker()
            unreadDigestWorker = UnreadLinkDigestWorker(get(), get(), get()).worker()
            fileCleanupWorker = TempFileCleanupWorker().worker()
            orphanCleanupWorker = OrphanResourceCleanupWorker().worker()
            reminderWorker = ReminderWorker(get(), get()).worker()
            entryRefWorker = EntryRefWorker(get(), get(), get(), get()).worker()
        }
    }

    private lateinit var linkWorker: SendChannel<LinkProcessingRequest>
    private lateinit var discussionWorker: SendChannel<DiscussionFinderWorkerRequest>
    private lateinit var taskWorker: SendChannel<TaskRunnerRequest>
    private lateinit var unreadDigestWorker: SendChannel<String>
    private lateinit var fileCleanupWorker: SendChannel<TempFileCleanupWorkerRequest>
    private lateinit var orphanCleanupWorker: SendChannel<OrphanResourceCleanupRequest>
    private lateinit var reminderWorker: SendChannel<ReminderWorkerRequest>
    private lateinit var entryRefWorker: SendChannel<EntryRefWorkerRequest>

    fun acceptLinkWork(request: LinkProcessingRequest) {
        linkWorker.trySend(request)
    }

    fun acceptTaskWork(task: Task<TaskContext>, context: TaskContext) {
        taskWorker.trySend(TaskRunnerRequest(task, context))
    }

    fun acceptDiscussionWork(userId: UserId, linkId: EntryId) {
        discussionWorker.trySend(DiscussionFinderWorkerRequest(userId, linkId))
    }

    fun acceptReminderWork(request: ReminderWorkerRequest) {
        reminderWorker.trySend(request)
    }

    fun acceptEntryRefWork(userId: UserId, entryId: EntryId) {
        entryRefWorker.trySend(DefaultEntryRefWorkerRequest(userId, entryId))
    }

    fun acceptCommentRefWork(userId: UserId, entryId: EntryId, commentId: CommentId, updateType: CrudType) {
        entryRefWorker.trySend(CommentRefWorkerRequest(userId, entryId, commentId, updateType))
    }

}
