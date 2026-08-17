require('dotenv').config();
const db = require('./db/pool');
const schedulerDispatcher = require('./workers/schedulerDispatcher');
const publishWorker = require('./workers/publishWorker');

/**
 * Social AI Agent — Standalone Headless Background Worker Daemon
 * 
 * Runs the headless scheduler dispatcher and publish worker as a dedicated,
 * isolated process for horizontally scalable production deployments.
 */

console.log('================================================================');
console.log('--- Social AI Agent Standalone Background Worker Daemon ---');
console.log('================================================================');
console.log(`Process ID: ${process.pid}`);
console.log(`Node Version: ${process.version}`);
console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
console.log(`Database Configured: ${db.isConfigured() ? 'Yes' : 'No (In-Memory Fallback)'}`);

const schedulerPollInterval = parseInt(process.env.SCHEDULER_POLL_INTERVAL_MS || '15000', 10);
const workerPollInterval = parseInt(process.env.WORKER_POLL_INTERVAL_MS || '10000', 10);

console.log(`Scheduler Dispatcher Interval: ${schedulerPollInterval}ms`);
console.log(`Publish Worker Interval: ${workerPollInterval}ms`);

// Start both background services
schedulerDispatcher.start(schedulerPollInterval);
publishWorker.start(workerPollInterval);

console.log('✓ Headless Scheduler Dispatcher active');
console.log('✓ Headless Publish Worker active');
console.log('Worker daemon is running. Press Ctrl+C to terminate.');

let isShuttingDown = false;

async function gracefulShutdown(signal) {
  if (isShuttingDown) return;
  isShuttingDown = true;

  console.log(`\nReceived ${signal}. Shutting down worker daemon gracefully...`);

  try {
    // 1. Stop scheduler and publish worker polling loops
    schedulerDispatcher.stop();
    publishWorker.stop();
    console.log('✓ Scheduler Dispatcher and Publish Worker loops stopped.');

    // 2. Safely close database connection pool
    if (db.isConfigured()) {
      await db.closePool();
      console.log('✓ Database connection pool closed.');
    }

    console.log('Worker daemon shut down cleanly.');
    process.exit(0);
  } catch (err) {
    console.error('Error during worker shutdown:', err.message);
    process.exit(1);
  }
}

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

module.exports = {
  schedulerDispatcher,
  publishWorker,
  gracefulShutdown
};
