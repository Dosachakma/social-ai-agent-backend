const scheduledJobService = require('../services/scheduledJobService');
const socialPostService = require('../services/socialPostService');
const agentLogService = require('../services/agentLogService');

/**
 * Headless Scheduler Dispatcher.
 * Scans for due, approved scheduled posts and safely creates durable platform jobs.
 */
class SchedulerDispatcher {
  constructor(options = {}) {
    this.pollIntervalMs = options.pollIntervalMs || 15000;
    this.batchSize = options.batchSize || 50;
    this.timer = null;
    this.isRunning = false;
    this.isDispatching = false;
  }

  /**
   * Start the scheduler dispatcher loop.
   */
  start(intervalMs = null) {
    if (this.isRunning) return;
    if (intervalMs) this.pollIntervalMs = intervalMs;
    this.isRunning = true;

    this.timer = setInterval(async () => {
      try {
        await this.runDispatchCycle();
      } catch (err) {
        console.error('[SchedulerDispatcher] Error in dispatch cycle:', err.message);
      }
    }, this.pollIntervalMs);

    if (this.timer.unref) {
      this.timer.unref(); // Prevent preventing process exit in tests
    }
  }

  /**
   * Stop the scheduler dispatcher loop.
   */
  stop() {
    this.isRunning = false;
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  /**
   * Run a single dispatch cycle.
   * Finds due posts and creates platform jobs.
   */
  async runDispatchCycle() {
    if (this.isDispatching) return { dispatchedPostCount: 0, createdJobCount: 0 };
    this.isDispatching = true;

    let dispatchedPostCount = 0;
    let createdJobCount = 0;

    try {
      const duePosts = await scheduledJobService.findDuePostsForDispatch(this.batchSize);

      for (const post of duePosts) {
        try {
          const platforms = Array.isArray(post.targetPlatforms)
            ? post.targetPlatforms
            : (typeof post.targetPlatforms === 'string' ? JSON.parse(post.targetPlatforms) : []);

          if (platforms.length === 0) {
            console.warn(`[SchedulerDispatcher] Post ${post.id} has no target platforms, skipping`);
            continue;
          }

          const jobs = await scheduledJobService.createJobsForPost(
            post.workspaceId,
            post.id,
            platforms,
            post.scheduledAt,
            post.maxRetries || 3
          );

          if (jobs.length > 0) {
            dispatchedPostCount++;
            createdJobCount += jobs.length;

            // Operational audit logging
            try {
              await agentLogService.createLog(post.workspaceId, {
                action: 'SCHEDULE_POST',
                platform: platforms[0] || 'FACEBOOK',
                status: 'APPROVED',
                metadata: {
                  event: 'SCHEDULER_DISPATCH_JOBS_CREATED',
                  postId: post.id,
                  platformCount: String(platforms.length),
                  jobIds: JSON.stringify(jobs.map(j => j.id))
                }
              });
            } catch (logErr) {
              // Ignore logging error to prevent breaking dispatch
            }
          }
        } catch (postErr) {
          console.error(`[SchedulerDispatcher] Failed to dispatch jobs for post ${post.id}:`, postErr.message);
        }
      }

      return { dispatchedPostCount, createdJobCount };
    } finally {
      this.isDispatching = false;
    }
  }
}

module.exports = new SchedulerDispatcher();
